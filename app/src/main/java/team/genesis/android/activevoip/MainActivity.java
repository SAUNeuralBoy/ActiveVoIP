package team.genesis.android.activevoip;

import android.app.AlertDialog;
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

import androidx.annotation.NonNull;
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.db.ContactDB;
import team.genesis.android.activevoip.db.ContactDao;
import team.genesis.android.activevoip.db.ContactEntity;
import team.genesis.android.activevoip.network.ClientTunnel;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.android.activevoip.ui.MainViewModel;
import team.genesis.android.activevoip.ui.talking.TalkingViewModel;
import team.genesis.data.UUID;
import team.genesis.network.DNSLookupThread;
import team.genesis.tunnels.ActiveDatagramTunnel;
import team.genesis.tunnels.UDPActiveDatagramTunnel;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

import static java.lang.System.exit;

public class MainActivity extends AppCompatActivity {

    public static final int REQ_CODE_PERMISSION = 1001;

    private AppBarConfiguration mAppBarConfiguration;

    private MainViewModel viewModel;
    private TalkingViewModel talkingViewModel;

    private ClientTunnel tunnel;

    private ContactDao dao;
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        talkingViewModel = new ViewModelProvider(this).get(TalkingViewModel.class);
        dao = Room.databaseBuilder(this, ContactDB.class, "ContactEntity").allowMainThreadQueries().build().getDao();
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

        sp = SPManager.getManager(this);
        try {
            tunnel = new ClientTunnel("activity",sp.getHostname(),sp.getPort(),sp.getUUID());
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
            return;
        }
        update();
        tunnel.observe(this,viewModel.getPrefLiveData());

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
                    createPair(contact);
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

        final MainActivity tActivity = this;
        tunnel.setRecvListener(msg->uiHandler.post(()->{try {
            ByteBuf buf = Unpooled.copiedBuffer(msg.data);
            ByteBuf repBuf = Unpooled.buffer();
            switch (Ctrl.values()[buf.readInt()]) {
                case PAIR:{
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
                    kpg.initialize(new KeyGenParameterSpec.Builder(
                            Crypto.to64(msg.src.getBytes()),
                            KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                            .setDigests(KeyProperties.DIGEST_SHA256,
                                    KeyProperties.DIGEST_SHA512)
                            .build());
                    KeyPair kp = kpg.generateKeyPair();
                    Contact contact;
                    ContactEntity result = ContactDB.findContactByUUID(dao,msg.src);
                    if(result==null)
                        contact = new Contact();
                    else {
                        contact = ContactDB.getContactOrDelete(dao,result);
                        if(contact==null)   return;
                    }
                    contact.uuid = msg.src;
                    contact.ourPk = kp.getPublic().getEncoded();

                    contact.otherPk = Network.readNbytes(buf,500);

                    repBuf.writeInt(Ctrl.PAIR_RESPONSE.ordinal());
                    Network.writeBytes(repBuf,contact.ourPk);
                    Network.writeBytes(repBuf,contact.otherPk);
                    write(Network.readAllBytes(repBuf),msg.src);

                    contact.status = Contact.Status.PAIR_RCVD;
                    dao.insertContact(new ContactEntity(contact));
                    break;}
                case PAIR_RESPONSE:{
                    ContactEntity result = ContactDB.findContactByUUID(dao,msg.src);
                    if(result==null)    return;
                    Contact contact = ContactDB.getContactOrDelete(dao,result);
                    if(contact==null)   return;
                    if(contact.status!= Contact.Status.PAIR_SENT)   return;
                    contact.otherPk = Network.readNbytes(buf,500);
                    byte[] chk = Network.readNbytes(buf,500);
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
                    write(Network.readAllBytes(repBuf),msg.src);
                    contact.status = Contact.Status.CONFIRM_WAIT;
                    dao.insertContact(new ContactEntity(contact));
                    break;}
                case PAIR_ACK: {
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    if (contact.status == Contact.Status.PAIR_RCVD) {
                        contact.status = Contact.Status.CONFIRM_WAIT;
                        dao.insertContact(new ContactEntity(contact));
                    }
                    break;
                }
                case CALL:{
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    if(contact.status!= Contact.Status.READY)   return;
                    Signature s = Signature.getInstance("SHA256withECDSA");
                    s.initVerify(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(contact.otherPk)));
                    byte[] otherPk = Network.readNbytes(buf,500);
                    s.update(otherPk);
                    byte[] sign = Network.readNbytes(buf,500);
                    if(!s.verify(sign)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(tActivity);
                        builder.setTitle(R.string.dangerous);
                        builder.setMessage(R.string.mitm_warning_msg);
                        builder.setPositiveButton(R.string.panic, (dialog, which) -> {});
                        builder.setNegativeButton(R.string.remain_calm, (dialog, which) -> {});
                        builder.show();
                        return;
                    }
                    switch (talkingViewModel.getStatus()){
                        case DEAD:
                            talkingViewModel.setStatus(TalkingViewModel.Status.INVOKING);
                            talkingViewModel.setContact(contact);
                            talkingViewModel.setOtherPk(otherPk);
                            navController.navigate(R.id.nav_talking);
                            break;
                        case INCOMING:
                            if(!contact.uuid.equals(talkingViewModel.getContact().uuid))    return;
                            if(!Arrays.equals(otherPk,talkingViewModel.getOtherPk()))   return;
                            talkingViewModel.setStatus(TalkingViewModel.Status.INVOKING);
                            break;
                        case CALLING:
                        case TALKING:
                        case INVOKING:
                            return;
                    }
                    break;
                }
                case CALL_REJECT:{
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    if(contact.status!= Contact.Status.READY)   return;
                    if(!contact.uuid.equals(talkingViewModel.getContact().uuid))    return;
                    if(talkingViewModel.getStatus()!= TalkingViewModel.Status.CALLING)  return;
                    talkingViewModel.setStatus(TalkingViewModel.Status.REJECTED);
                    break;
                }
                case CALL_RESPONSE:{
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    if(contact.status!= Contact.Status.READY)   return;
                    if(!contact.uuid.equals(talkingViewModel.getContact().uuid))    return;
                    if(talkingViewModel.getStatus()!= TalkingViewModel.Status.CALLING)  return;
                    Signature s = Signature.getInstance("SHA256withECDSA");
                    s.initVerify(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(contact.otherPk)));
                    byte[] otherPk = Network.readNbytes(buf,500);
                    s.update(otherPk);
                    byte[] sign = Network.readNbytes(buf,500);
                    if(!s.verify(sign)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(tActivity);
                        builder.setTitle(R.string.dangerous);
                        builder.setMessage(R.string.mitm_warning_msg);
                        builder.setPositiveButton(R.string.panic, (dialog, which) -> {});
                        builder.setNegativeButton(R.string.remain_calm, (dialog, which) -> {});
                        builder.show();
                        return;
                    }
                    if(!Arrays.equals(talkingViewModel.getOurPk(),Network.readNbytes(buf,500))) return;
                    talkingViewModel.setOtherPk(otherPk);
                    talkingViewModel.setStatus(TalkingViewModel.Status.CALL_ACCEPTED);
                    break;
                }
                case CALL_ACK:{
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    if(contact.status!= Contact.Status.READY)   return;
                    if(!contact.uuid.equals(talkingViewModel.getContact().uuid))    return;
                    if(talkingViewModel.getStatus()!= TalkingViewModel.Status.ACCEPT_CALL)  return;
                    talkingViewModel.setStatus(TalkingViewModel.Status.TALKING);
                    break;
                }
            }
        }catch (IndexOutOfBoundsException | SignatureException | InvalidKeySpecException | InvalidKeyException ignored){}
        catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
            exit(0);
        }
        }));

        Handler probeHandler = UI.getCycledHandler("probe");
        UDPProbe probe;
        try {
            probe = UDPActiveDatagramTunnel.getProbe(1000);
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
            return;
        }
        uiHandler = new Handler();
        Runnable probeHost = new Runnable() {
            @Override
            public void run() {
                MainViewModel.CompassColor color;
                try {
                    if (probe.probe(tunnel.getHostAddr(), tunnel.getPort(), 1) == 1) {
                        color = MainViewModel.CompassColor.FINE;
                    } else {
                        if (probe.probe(tunnel.getHostAddr(), tunnel.getPort(), 10) > 0)
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
    public ContactDao getDao(){
        return dao;
    }
    public void createPair(Contact contact){
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(Ctrl.PAIR.ordinal());
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
            kpg.initialize(new KeyGenParameterSpec.Builder(
                    Crypto.to64(contact.uuid.getBytes()),
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
        contact.ourPk = kp.getPublic().getEncoded();
        Network.writeBytes(buf,contact.ourPk);
        write(Network.readAllBytes(buf),contact.uuid);
        contact.status = Contact.Status.PAIR_SENT;
        dao.insertContact(new ContactEntity(contact));
    }
    public void write(byte[] data,UUID dst){
        tunnel.send(data,dst);
    }
    public void update(){
        viewModel.getPrefLiveData().setValue(new ClientTunnel.Preference(sp.getUUID(),sp.getHostname(),sp.getPort()));
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
            exit(0);
        }
    }
}