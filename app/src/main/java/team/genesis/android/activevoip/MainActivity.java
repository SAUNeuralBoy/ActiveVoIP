package team.genesis.android.activevoip;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.view.Menu;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.navigation.NavigationView;

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
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.db.ContactDB;
import team.genesis.android.activevoip.db.ContactDao;
import team.genesis.android.activevoip.db.ContactEntity;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.android.activevoip.ui.MainViewModel;
import team.genesis.data.UUID;
import team.genesis.network.DNSLookupThread;
import team.genesis.tunnels.ActiveDatagramTunnel;
import team.genesis.tunnels.UDPActiveDatagramTunnel;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;

    private MainViewModel viewModel;

    private ContactDB contactDB;
    private ContactDao dao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        contactDB = Room.databaseBuilder(this, ContactDB.class, "ContactEntity").allowMainThreadQueries().build();
        dao = contactDB.getDao();
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

        Handler uiHandler = new Handler();
        Handler writeHandler = getCycledHandler("keepalive");
        findViewById(R.id.button_add).setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this,v);
            popup.setOnMenuItemClickListener(item -> {
                UUID uuid = new UUID();
                final EditText input = new EditText(this);
                int itemId = item.getItemId();
                Contact contact = new Contact();
                Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        ByteBuf buf = Unpooled.buffer();
                        buf.writeInt(Ctrl.PAIR.ordinal());
                        KeyPairGenerator kpg;
                        try {
                            kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
                            kpg.initialize(new KeyGenParameterSpec.Builder(
                                    Crypto.to64(uuid.getBytes()),
                                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                                    .setDigests(KeyProperties.DIGEST_SHA256,
                                            KeyProperties.DIGEST_SHA512)
                                    .build());
                        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
                            e.printStackTrace();
                            exit(0);
                            return;
                        }
                        KeyPair kp = kpg.generateKeyPair();
                        contact.uuid = uuid;
                        contact.ourPk = kp.getPublic().getEncoded();
                        buf.writeBytes(contact.ourPk);
                        writeHandler.post(() -> write(buf.array(),uuid));
                        contact.status = Contact.Status.PAIR_SENT;
                        dao.insertContact(new ContactEntity(contact));
                    }
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
        Runnable keepsAlive = new Runnable() {
            @Override
            public void run() {
                try {
                    listenTunnel.keepAlive();
                } catch (IOException e) {
                    e.printStackTrace();
                }finally {
                    writeHandler.postDelayed(this,5000);
                }
            }
        };
        writeHandler.postDelayed(keepsAlive,5000);

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
                uiHandler.post(()->{try {
                    ByteBuf buf = Unpooled.copiedBuffer(msg.data);
                    ByteBuf repBuf = Unpooled.buffer();
                    switch (Ctrl.values()[buf.readInt()]) {
                        case PAIR:{
                            if (buf.readableBytes() < 91) return;
                            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
                            kpg.initialize(new KeyGenParameterSpec.Builder(
                                    Crypto.to64(msg.src.getBytes()),
                                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                                    .setDigests(KeyProperties.DIGEST_SHA256,
                                            KeyProperties.DIGEST_SHA512)
                                    .build());
                            KeyPair kp = kpg.generateKeyPair();
                            Contact contact = new Contact();
                            contact.uuid = msg.src;
                            contact.ourPk = kp.getPublic().getEncoded();
                            buf.readBytes(contact.otherPk, 0, 91);

                            repBuf.writeInt(Ctrl.PAIR_RESPONSE.ordinal());
                            repBuf.writeBytes(contact.ourPk);
                            repBuf.writeBytes(contact.otherPk);
                            write(repBuf.array(),msg.src);

                            contact.status = Contact.Status.PAIR_RCVD;
                            dao.insertContact(new ContactEntity(contact));
                            break;}
                        case PAIR_RESPONSE:{
                            if (buf.readableBytes() < 91*2) return;
                            ContactEntity[] result = ContactDB.findContactByUUID(dao,msg.src);
                            if(result==null)    return;
                            Contact contact;
                            try {
                                contact = result[0].getContact();
                            } catch (Crypto.DecryptException e) {
                                dao.deleteContact(result[0]);
                                return;
                            }
                            if(contact.status!= Contact.Status.PAIR_SENT)   return;
                            buf.readBytes(contact.otherPk,0,91);
                            byte[] chk = new byte[91];
                            buf.readBytes(chk,0,91);
                            if(!Arrays.equals(contact.ourPk,chk))   return;
                            try {
                                KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
                                keyStore.load(null);
                                if (!keyStore.containsAlias(Crypto.to64(msg.src.getBytes())))
                                    return;
                            }catch (KeyStoreException | CertificateException | IOException e){
                                e.printStackTrace();
                                exit(0);
                            }

                            repBuf.writeInt(Ctrl.PAIR_ACK.ordinal());
                            write(repBuf.array(),msg.src);
                            contact.status = Contact.Status.CONFIRM_WAIT;
                            dao.insertContact(new ContactEntity(contact));
                            break;}
                        case PAIR_ACK:
                            ContactEntity[] result = ContactDB.findContactByUUID(dao,msg.src);
                            if(result==null)    return;
                            Contact contact;
                            try {
                                contact = result[0].getContact();
                            } catch (Crypto.DecryptException e) {
                                dao.deleteContact(result[0]);
                                return;
                            }
                            if(contact.status== Contact.Status.PAIR_RCVD){
                                contact.status = Contact.Status.CONFIRM_WAIT;
                                dao.insertContact(new ContactEntity(contact));
                            }
                            break;
                    }
                }catch (IndexOutOfBoundsException ignored){}
                catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
                    e.printStackTrace();
                    exit(0);
                }
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
                MainViewModel.CompassColor color;
                try {
                    if (probe.probe(host, port, 1) == 1) {
                        color = MainViewModel.CompassColor.FINE;
                    } else {
                        if (probe.probe(host, port, 10) > 0)
                            color = MainViewModel.CompassColor.DISTURBING;
                        else
                            color = MainViewModel.CompassColor.ERROR;
                    }
                    if(viewModel.getCompassColor().getValue()!=color)
                        uiHandler.post(()->viewModel.getCompassColor().setValue(color));
                }catch (IOException e){
                    e.printStackTrace();
                }finally {
                    probeHandler.postDelayed(this,2500);
                }
            }
        };
        probeHandler.post(probeHost);


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
    public static Handler getCycledHandler(String name){
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new Handler(thread.getLooper());
    }
}