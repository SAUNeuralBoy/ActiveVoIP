package team.genesis.android.activevoip;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.Menu;
import android.view.View;
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
import androidx.recyclerview.widget.RecyclerView;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.db.ContactDao;
import team.genesis.android.activevoip.ui.MainViewModel;
import team.genesis.android.activevoip.ui.home.ContactAdapter;
import team.genesis.data.UUID;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    public static final int REQ_CODE_PERMISSION = 1001;

    private AppBarConfiguration mAppBarConfiguration;

    private MainViewModel viewModel;
    //private TalkingViewModel talkingViewModel;
    private VoIPService service;
    private ServiceConnection conn;

    private Handler uiHandler;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        uiHandler = new Handler();
        viewModel = new ViewModelProvider(MainActivity.this).get(MainViewModel.class);

        if(!isServiceRunning(VoIPService.class))
            startService(new Intent(this,VoIPService.class));
        conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                service = ((VoIPService.VoIPBinder)binder).getService();
                service.setActivity(MainActivity.this);
                service.getLiveProbeResult().observe(MainActivity.this, aDouble -> {
                    MainViewModel.CompassColor color;
                    if(aDouble>=1) color = MainViewModel.CompassColor.FINE;
                    else if(aDouble>0) color = MainViewModel.CompassColor.DISTURBING;
                    else color = MainViewModel.CompassColor.ERROR;
                    if(viewModel.getCompassColor().getValue()!=color)
                        uiHandler.post(()->viewModel.getCompassColor().setValue(color));
                });
                uiHandler.post(() -> {
                    //talkingViewModel = new ViewModelProvider(MainActivity.this).get(TalkingViewModel.class);
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
                    //noinspection deprecation
                    mAppBarConfiguration = new AppBarConfiguration.Builder(
                            R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow)
                            .setDrawerLayout(drawer)
                            .build();
                    navController = Navigation.findNavController(MainActivity.this, R.id.nav_host_fragment);
                    NavigationUI.setupActionBarWithNavController(MainActivity.this, navController, mAppBarConfiguration);
                    NavigationUI.setupWithNavController(navigationView, navController);
                    findViewById(R.id.button_compass).setOnClickListener(v -> navController.navigate(R.id.nav_gallery));
                    findViewById(R.id.button_edit).setOnClickListener(v -> getContactAdapter().setEditable(!getContactAdapter().isEditable()));



                    viewModel.getCompassColor().observe(MainActivity.this, compassColor -> {
                        int color = R.color.error_color;
                        switch (compassColor){
                            case FINE:
                                color = R.color.fine_color;
                                break;
                            case DISTURBING:
                                color = R.color.distrubing_color;
                                break;
                        }
                        //noinspection deprecation
                        ((ImageButton)findViewById(R.id.button_compass)).setImageTintList(ColorStateList.valueOf(getResources().getColor(color)));
                    });

                    findViewById(R.id.button_add).setOnClickListener(v -> {
                        PopupMenu popup = new PopupMenu(MainActivity.this,v);
                        popup.setOnMenuItemClickListener(item -> {
                            UUID uuid = new UUID();
                            final EditText input = new EditText(MainActivity.this);
                            int itemId = item.getItemId();
                            Contact contact = new Contact();
                            Runnable r = () -> {
                                contact.uuid = uuid;
                                service.createPair(contact);
                            };
                            if(itemId==R.id.action_add_from_contact_name){
                                UI.makeInputWindow(MainActivity.this,input,getString(R.string.contact_input_title), (dialog, which) -> {
                                    if(input.getText().toString().equals("")){
                                        UI.makeSnackBar(v,getString(R.string.contact_empty));
                                        return;
                                    }
                                    uuid.fromBytes(Crypto.md5(input.getText().toString().getBytes(StandardCharsets.UTF_8)));
                                    contact.alias = input.getText().toString();
                                    r.run();
                                });
                            }else if(itemId==R.id.action_add_uuid){
                                UI.makeInputWindow(MainActivity.this,input,getString(R.string.from_uuid), (dialog, which) -> {
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
                    findViewById(R.id.button_cut).setOnClickListener(v -> service.getDispatcher().cut());
                    final ImageButton buttonSpeaker = findViewById(R.id.button_speaker);
                    buttonSpeaker.setOnClickListener(v -> {
                        if(!service.isUsingAttached()){
                            v.setVisibility(View.GONE);
                            return;
                        }
                        int color;
                        if(service.isUsingSpeaker())
                            color = R.color.disabled_color;
                        else
                            color = R.color.fine_color;
                        //noinspection deprecation
                        buttonSpeaker.setImageTintList(ColorStateList.valueOf(getResources().getColor(color)));
                        service.switchSpeaker();
                    });
                    Runnable deviceDetect = new Runnable() {
                        @Override
                        public void run() {
                            if(service.isUsingAttached()&&buttonSpeaker.getVisibility()==View.GONE)
                                buttonSpeaker.setVisibility(View.VISIBLE);
                            else if((!service.isUsingAttached())&&buttonSpeaker.getVisibility()==View.VISIBLE)
                                buttonSpeaker.setVisibility(View.GONE);
                            uiHandler.postDelayed(this,1000);
                        }
                    };
                    uiHandler.post(deviceDetect);
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service.setActivity(null);
            }
        };
        bindService(new Intent(this,VoIPService.class),conn,Context.BIND_ABOVE_CLIENT|Context.BIND_IMPORTANT);
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
    public VoIPService getService(){
        return service;
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
    public void lock(){
        findViewById(R.id.button_cut).setVisibility(View.VISIBLE);
        findViewById(R.id.button_compass).setClickable(false);
        getContactAdapter().setLocked(true);
    }
    public void unlock(){
        findViewById(R.id.button_cut).setVisibility(View.GONE);
        findViewById(R.id.button_compass).setClickable(true);
        getContactAdapter().setLocked(false);
    }

    public ContactAdapter getContactAdapter() {
        return (ContactAdapter) Objects.requireNonNull(((RecyclerView) findViewById(R.id.list_contact)).getAdapter());
    }
}