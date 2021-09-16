package team.genesis.android.activevoip;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
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
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

import team.genesis.data.UUID;
import team.genesis.network.DNSLookupThread;
import team.genesis.tunnels.ActiveDatagramTunnel;
import team.genesis.tunnels.UDPActiveDatagramTunnel;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

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
        sp = SPManager.getManager(this);
        host = InetAddress.getLoopbackAddress();
        port = sp.getPort();
        try {
            writeTunnel = new UDPActiveDatagramTunnel(host,sp.getPort(),sp.getUUID());
            listenTunnel = new UDPActiveDatagramTunnel(host,sp.getPort(),sp.getUUID());
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
        }
        Handler uiHandler = new Handler();
        Runnable keepsAlive = new Runnable() {
            @Override
            public void run() {
                try {
                    listenTunnel.keepAlive();
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    uiHandler.postDelayed(this,5000);
                }
            }
        };
        uiHandler.postDelayed(keepsAlive,5000);

        Handler recvHandler = getCycledHandler("recv");
        Runnable recv = new Runnable() {
            @Override
            public void run() {
                ActiveDatagramTunnel.Incoming msg;
                try {
                    msg = listenTunnel.recv();
                } catch (IOException e) {
                    e.printStackTrace();
                    recvHandler.postDelayed(this,100);
                    return;
                }
                uiHandler.post(()->{

                });

                recvHandler.post(this);
            }
        };
        recvHandler.post(recv);

        Handler dnsHandler = getCycledHandler("dns");
        Runnable dnsUpdate = new Runnable() {
            @Override
            public void run() {
                updateDNS();
                dnsHandler.postDelayed(this,30000);
            }
        };
        dnsHandler.post(dnsUpdate);

        Handler probeHandler = getCycledHandler("probe");
        UDPProbe probe;
        try {
            probe = UDPActiveDatagramTunnel.getProbe(1000);
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
            return;
        }
        Runnable probeHost = new Runnable() {
            @Override
            public void run() {
                int color;
                try {
                    if (probe.probe(host, port, 1) == 1) {
                        color = R.color.fine_color;
                    } else {
                        if (probe.probe(host, port, 10) > 0)
                            color = R.color.distrubing_color;
                        else
                            color = R.color.error_color;
                    }
                    uiHandler.post(() -> ((ImageButton)findViewById(R.id.button_compass)).setImageTintList(ColorStateList.valueOf(getResources().getColor(color))));
                }catch (IOException e){
                    e.printStackTrace();
                }finally {
                    probeHandler.postDelayed(this,2500);
                }
            }
        };
        probeHandler.postDelayed(probeHost,5000);
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
    private int port;
    public void setHost(InetAddress hostAddr,int port){
        host = hostAddr;
        this.port = port;
        writeTunnel.setHost(hostAddr, port);
        listenTunnel.setHost(hostAddr, port);
    }
    public void setHost(InetAddress hostAddr){
        host = hostAddr;
        writeTunnel.setHost(hostAddr);
        listenTunnel.setHost(hostAddr);
    }
    public void setUUID(UUID uuid){
        writeTunnel.setSrc(uuid);
        listenTunnel.setSrc(uuid);
    }
    public void write(byte[] data, UUID dst){
        try {
            writeTunnel.send(data,dst);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
            setHost(dns.getIP());
        }
        return true;
    }
    private Handler getCycledHandler(String name){
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new Handler(thread.getLooper());
    }
}