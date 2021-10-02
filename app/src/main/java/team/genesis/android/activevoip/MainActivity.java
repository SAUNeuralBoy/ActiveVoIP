package team.genesis.android.activevoip;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.view.Menu;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.room.Room;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.db.ContactDB;
import team.genesis.android.activevoip.db.ContactDao;
import team.genesis.android.activevoip.db.ContactEntity;
import team.genesis.android.activevoip.network.ClientTunnel;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.android.activevoip.ui.MainViewModel;
import team.genesis.android.activevoip.ui.talking.TalkingFragment;
import team.genesis.android.activevoip.ui.talking.TalkingViewModel;
import team.genesis.data.UUID;
import team.genesis.network.DNSLookupThread;
import team.genesis.tunnels.ActiveDatagramTunnel;
import team.genesis.tunnels.UDPActiveDatagramTunnel;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    public static final int REQ_CODE_PERMISSION = 1001;

    private AppBarConfiguration mAppBarConfiguration;

    private MainViewModel viewModel;
    private TalkingViewModel talkingViewModel;
    private VoIPService service;
    private ServiceConnection conn;

    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!isServiceRunning(VoIPService.class))
            startService(new Intent(this,VoIPService.class));
        conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                service = ((VoIPService.VoIPBinder)binder).getService();
                service.setActivity(MainActivity.this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service.setActivity(null);
            }
        };
        bindService(new Intent(this,VoIPService.class),conn,Context.BIND_ABOVE_CLIENT|Context.BIND_IMPORTANT);

        talkingViewModel = new ViewModelProvider(this).get(TalkingViewModel.class);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);
        findViewById(R.id.button_compass).setOnClickListener(v -> navController.navigate(R.id.nav_gallery));
        findViewById(R.id.button_edit).setOnClickListener(v -> navController.navigate(R.id.nav_edit));

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);


        viewModel.getCompassColor().observe(this, compassColor -> {
            int color = R.color.error_color;
            switch (compassColor){
                case FINE:
                    color = R.color.fine_color;
                    break;
                case DISTURBING:
                    color = R.color.distrubing_color;
                    break;
            }
            ((ImageButton)findViewById(R.id.button_compass)).setImageTintList(ColorStateList.valueOf(getResources().getColor(color)));
        });

        findViewById(R.id.button_add).setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this,v);
            popup.setOnMenuItemClickListener(item -> {
                UUID uuid = new UUID();
                final EditText input = new EditText(this);
                int itemId = item.getItemId();
                Contact contact = new Contact();
                Runnable r = () -> {
                    contact.uuid = uuid;
                    service.createPair(contact);
                };
                if(itemId==R.id.action_add_from_contact_name){
                    UI.makeInputWindow(this,input,getString(R.string.contact_input_title), (dialog, which) -> {
                        if(input.getText().toString().equals("")){
                            UI.makeSnackBar(v,getString(R.string.contact_empty));
                            return;
                        }
                        uuid.fromBytes(Crypto.md5(input.getText().toString().getBytes(StandardCharsets.UTF_8)));
                        contact.alias = input.getText().toString();
                        r.run();
                    });
                }else if(itemId==R.id.action_add_uuid){
                    UI.makeInputWindow(this,input,getString(R.string.from_uuid), (dialog, which) -> {
                        if(input.getText().toString().equals("")){
                            UI.makeSnackBar(v,getString(R.string.contact_empty));
                            return;
                        }
                        uuid.fromBytes(Crypto.from64(input.getText().toString()));
                        r.run();
                    });
                }else return false;
                return true;
            });
            popup.inflate(R.menu.add);
            popup.show();
        });




        uiHandler = new Handler();
        service.setProbeListener((recv, cnt) -> {
            MainViewModel.CompassColor color;
            if(recv==cnt) color = MainViewModel.CompassColor.FINE;
            else if(recv>0) color = MainViewModel.CompassColor.DISTURBING;
            else color = MainViewModel.CompassColor.ERROR;
            if(viewModel.getCompassColor().getValue()!=color)
                uiHandler.post(()->viewModel.getCompassColor().setValue(color));
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }


    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
    private SPManager sp;
    public ContactDao getDao(){
        return service.getDao();
    }

    public String getHostname(){
        return sp.getHostname();
    }
    public int getPort(){
        return sp.getPort();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==REQ_CODE_PERMISSION){
            if(grantResults[0] != PERMISSION_GRANTED) exit(0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)!= PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},MainActivity.REQ_CODE_PERMISSION);
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(conn);
    }
}