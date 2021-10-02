package team.genesis.android.activevoip;

import android.app.AlertDialog;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.room.Room;

import java.io.IOException;
import java.net.SocketException;
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
import team.genesis.tunnels.UDPActiveDatagramTunnel;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

import static java.lang.System.exit;

public class VoIPService extends Service {


    private MainActivity mActivity;

    public VoIPService() {
        mActivity = null;
    }
    public void setActivity(MainActivity activity){
        mActivity = activity;
    }

    public ContactDao getDao() {
        return dao;
    }

    public class VoIPBinder extends Binder {
        public VoIPService getService(){
            return VoIPService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new VoIPService.VoIPBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dao = Room.databaseBuilder(this, ContactDB.class, "ContactEntity").allowMainThreadQueries().build().getDao();
        sp = SPManager.getManager(this);
        try {
            tunnel = new ClientTunnel("main",sp.getHostname(),sp.getPort(),sp.getUUID());
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
            return;
        }
        Handler uiHandler = new Handler();
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
                    if(!s.verify(sign)&&mActivity!=null){
                        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                        builder.setTitle(R.string.dangerous);
                        builder.setMessage(R.string.mitm_warning_msg);
                        builder.setPositiveButton(R.string.panic, (dialog, which) -> {});
                        builder.setNegativeButton(R.string.remain_calm, (dialog, which) -> {});
                        builder.show();
                        return;
                    }
                    //todo:refactor talkingFragment
                    /*
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
                    }*/
                    break;
                }
                //todo:refactor talkingFragment
                    /*
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
                }*/
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

        mProbeListener = (recv, cnt) -> {

        };
        Runnable probeHost = new Runnable() {
            @Override
            public void run() {
                MainViewModel.CompassColor color;
                try {
                    if (probe.probe(tunnel.getHostAddr(), tunnel.getPort(), 1) == 1) {
                        mProbeListener.onProbe(1,1);
                    } else {
                        int recv = probe.probe(tunnel.getHostAddr(), tunnel.getPort(), 10);
                        mProbeListener.onProbe(recv,10);
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }finally {
                    probeHandler.postDelayed(this,2500);
                }
            }
        };
        probeHandler.post(probeHost);

    }

    private ContactDao dao;
    private SPManager sp;
    private ClientTunnel tunnel;

    public void update() {
        tunnel.setSrc(sp.getUUID());
        tunnel.setPort(sp.getPort());
        tunnel.update(sp.getHostname());
    }

    public interface ProbeListener{
        void onProbe(int recv,int cnt);
    }
    private ProbeListener mProbeListener;
    public void setProbeListener(ProbeListener listener){
        mProbeListener = listener;
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
    public void write(byte[] data, UUID dst){
        tunnel.send(data,dst);
    }
}