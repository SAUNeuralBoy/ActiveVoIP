package team.genesis.android.activevoip;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.view.Menu;
import android.widget.Button;
import android.widget.ImageButton;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

import team.genesis.data.UUID;
import team.genesis.network.DNSLookupThread;
import team.genesis.tunnels.UDPActiveDatagramTunnel;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
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
        findViewById(R.id.button_compass).setOnClickListener(v -> {
            navController.navigate(R.id.nav_gallery);
        });
        sp = SPManager.getManager(this);
        host = InetAddress.getLoopbackAddress();
        try {
            writeTunnel = new UDPActiveDatagramTunnel(host,sp.getPort(),sp.getUUID());
            listenTunnel = new UDPActiveDatagramTunnel(host,sp.getPort(),sp.getUUID());
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
        }
        Handler aliveHandler = new Handler();
        Runnable keepsAlive = new Runnable() {
            @Override
            public void run() {
                try {
                    listenTunnel.keepAlive();
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    aliveHandler.postDelayed(this,5000);
                }
            }
        };
        aliveHandler.postDelayed(keepsAlive,5000);

        HandlerThread recvThread = new HandlerThread("recv");
        recvThread.start();
        Handler recvHandler = new Handler(recvThread.getLooper());
        Runnable recv = new Runnable() {
            @Override
            public void run() {
                try {
                    listenTunnel.recv();
                } catch (IOException e) {
                    e.printStackTrace();
                    recvHandler.postDelayed(this,100);
                    return;
                }
                recvHandler.post(this);
            }
        };
        recvHandler.post(recv);
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
    private UDPActiveDatagramTunnel writeTunnel;
    private UDPActiveDatagramTunnel listenTunnel;
    private InetAddress host;
    public void setHost(InetAddress hostAddr,int port){
        writeTunnel.setHost(hostAddr, port);
        listenTunnel.setHost(hostAddr, port);
    }
    public void setHost(InetAddress hostAddr){
        writeTunnel.setHost(hostAddr);
        listenTunnel.setHost(hostAddr);
    }
    public void setUUID(UUID uuid){
        writeTunnel.setSrc(uuid);
        listenTunnel.setSrc(uuid);
    }
    public void write(byte[] data, UUID dst) throws IOException {
        writeTunnel.send(data,dst);
    }
    public void update(){
        setUUID(sp.getUUID());
        setHost(host,sp.getPort());
        updateDNS();
    }
    public boolean updateDNS(){
        DNSLookupThread dns = new DNSLookupThread(sp.getHostname());
        dns.start();
        try {
            dns.join(1000);
        } catch (InterruptedException ignored) {
        }
        if(dns.getIP()==null)   return false;
        if(dns.getIP()!=host){
            host=dns.getIP();
            setHost(host);
        }
        return true;
    }
}