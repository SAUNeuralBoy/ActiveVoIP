package team.genesis.android.activevoip;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.SecretKeySpec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.android.activevoip.data.Contact;
import team.genesis.android.activevoip.db.ContactDB;
import team.genesis.android.activevoip.db.ContactDao;
import team.genesis.android.activevoip.db.ContactEntity;
import team.genesis.android.activevoip.network.ClientTunnel;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.android.activevoip.voip.Talker;
import team.genesis.data.UUID;
import team.genesis.tunnels.UDPActiveDatagramTunnel;
import team.genesis.tunnels.active.datagram.udp.UDPProbe;

import static java.lang.System.exit;

public class VoIPService extends Service {


    private MainActivity mActivity;
    private Handler uiHandler;
    private KeyStore keyStore;
    private Signature s;
    private static class Talk{
        Contact contact;
        KeyPair keyPair;
        boolean incoming;

        public Talk(Contact contact, KeyPair keyPair, boolean incoming) {
            this.contact = contact;
            this.keyPair = keyPair;
            this.incoming = incoming;
        }

        public Contact getContact() {
            return contact;
        }

        public KeyPair getKeyPair() {
            return keyPair;
        }

        public boolean isIncoming() {
            return incoming;
        }
    }
    public class Dispatcher{
        private Talk talk;
        private Talker talker;
        private byte[] newTalk(Contact contact,boolean incoming){try {
            mActivity.lock();
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
            kpg.initialize(256);
            talk = new Talk(contact,kpg.generateKeyPair(),incoming);
            return talk.getKeyPair().getPublic().getEncoded();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
            return null;
        }
        }
        private byte[] performECDH(KeyPair kp,byte[] otherPkt) {try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(kp.getPrivate());
            ka.doPhase(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(otherPkt)), true);
            byte[] sharedSecret = ka.generateSecret();
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(sharedSecret);
            List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(kp.getPublic().getEncoded()), ByteBuffer.wrap(otherPkt));
            Collections.sort(keys);
            hash.update(keys.get(0));
            hash.update(keys.get(1));
            return hash.digest();
        }catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException e) {
            e.printStackTrace();
            exit(0);
        }
            return null;
        }
        public void push(Contact contact){
            //setStat(Status.INCOMING);
            if(!contact.status.equals(Contact.Status.READY))    return;
            if(talk!=null&&contact.uuid.equals(talk.contact.uuid))  return;
            contact.invoke = System.currentTimeMillis();
            uiHandler.postDelayed(() -> {
                Contact t = ContactDB.getContactOrDelete(dao,contact.uuid);
                if(t ==null)   return;
                updateContact(t);
            }, 1500);
            updateContact(contact);
        }
        public byte[] accept(Contact contact){
            if(talk!=null)  return null;
            return newTalk(contact,true);
        }
        public void markRead(Contact contact){
            contact.otherPkt = null;
            updateContact(contact);
        }
        public void reject(Contact contact){
            markRead(contact);
        }
        public byte[] call(Contact contact){
            if(talk!=null)   return null;
            return newTalk(contact,false);
        }
        public void onRejected(UUID uuid){
            if(!isOutgoing(uuid))   return;
            cancelCall();
        }
        public boolean onAccepted(Contact contact, byte[] msg){
            if(!isOutgoing(contact.uuid))   return false;
            startTalking(contact.uuid,msg);
            return true;
        }
        public void startTalkingIfAccepted(UUID uuid){
            startTalking(uuid,talk.getContact().otherPkt);
        }
        public void startTalking(UUID uuid,byte[] otherPkt){
            if(talk==null)  return;
            if(!talk.getContact().uuid.equals(uuid))    return;
            talker = new Talker(new UUID(Crypto.md5(talk.getKeyPair().getPublic().getEncoded())),
                    new UUID(Crypto.md5(otherPkt)),
                    new SecretKeySpec(performECDH(talk.getKeyPair(),otherPkt),"AES"),sp.getHostname(),sp.getPort(),
                    (AudioManager) getSystemService(Context.AUDIO_SERVICE),VoIPService.this);
            talker.startTalking();
            markRead(talk.getContact());
        }
        public boolean isIncoming(){
            if(talk==null)  return false;
            return talk.isIncoming();
        }
        public boolean isOutgoing(UUID uuid){
            if(talk==null||talk.isIncoming())  return false;
            return talk.getContact().uuid.equals(uuid);
        }
        public void cut(){
            if(talker!=null){
                talker.cut();
                talker=null;
            }
            cancelCall();
        }
        public void cancelCall(){
            talk = null;
            uiHandler.post(()->mActivity.unlock());
        }

        public Dispatcher() {
            talk = null;
            talker = null;
        }
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    private Dispatcher dispatcher;
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
        String id = "voip_notification_channel_id";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //用户可见的通道名称
            String channelName = "VoIP Foreground Service Notification";
            //通道的重要程度
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel notificationChannel = new NotificationChannel(id, channelName, importance);
            notificationChannel.setDescription("Channel description");
            //LED灯
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            //震动
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            notificationChannel.enableVibration(true);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
        startForeground(1,
                new NotificationCompat.Builder(this,id)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("ActiveVoIP")
                        .setContentText("Running")
                        .setWhen(System.currentTimeMillis()).build());
        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            s = Signature.getInstance("SHA256withECDSA");
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException e) {
            e.printStackTrace();
            exit(0);
        }
        //mStat = new MutableLiveData<>(Status.READY);
        //reset();
        dao = Room.databaseBuilder(this, ContactDB.class, "ContactEntity").allowMainThreadQueries().build().getDao();
        sp = SPManager.getManager(this);
        try {
            tunnel = new ClientTunnel("main",sp.getHostname(),sp.getPort(),sp.getUUID());
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
            return;
        }
        //countDown = null;
        dispatcher = new Dispatcher();
        uiHandler = new Handler();
        tunnel.setRecvListener(msg-> uiHandler.post(()->{try {
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
                    updateContact(contact);
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
                        if (!keyStore.containsAlias(Crypto.to64(msg.src.getBytes())))
                            return;
                    }catch (KeyStoreException e){
                        e.printStackTrace();
                        exit(0);
                    }

                    repBuf.writeInt(Ctrl.PAIR_ACK.ordinal());
                    write(Network.readAllBytes(repBuf),msg.src);
                    contact.status = Contact.Status.CONFIRM_WAIT;
                    updateContact(contact);
                    break;}
                case PAIR_ACK: {
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    if (contact.status == Contact.Status.PAIR_RCVD) {
                        contact.status = Contact.Status.CONFIRM_WAIT;
                        updateContact(contact);
                    }
                    break;
                }
                case CALL:{
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    s.initVerify(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(contact.otherPk)));
                    byte[] otherPkt = Network.readNbytes(buf,500);
                    s.update(otherPkt);
                    byte[] sign = Network.readNbytes(buf,500);
                    if(!s.verify(sign)){
                        alert();
                        //reset();
                        return;
                    }
                    contact.otherPkt = otherPkt;
                    dispatcher.push(contact);
                    break;
                }
                case CALL_REJECT:{
                    dispatcher.onRejected(msg.src);
                    break;
                }
                case CALL_RESPONSE:{
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    s.initVerify(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(contact.otherPk)));//s.initVerify(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(mContact.otherPk)));
                    byte[] otherPkt = Network.readNbytes(buf,500);
                    s.update(otherPkt);
                    byte[] sign = Network.readNbytes(buf,500);
                    if(!s.verify(sign)){
                        alert();
                        return;
                    }
                    if(dispatcher.onAccepted(contact,otherPkt)) {
                        ByteBuf ack = Unpooled.buffer();
                        ack.writeInt(Ctrl.CALL_ACK.ordinal());
                        write(Network.readAllBytes(ack), msg.src);
                    }
                    break;
                }
                case CALL_ACK:{
                    dispatcher.startTalkingIfAccepted(msg.src);
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

        Runnable probeHost = new Runnable() {
            @Override
            public void run() {
                try {
                    if (probe.probe(tunnel.getHostAddr(), tunnel.getPort(), 1) == 1) {
                        updateProbeResult(1.1);
                    } else {
                        int recv = probe.probe(tunnel.getHostAddr(), tunnel.getPort(), 10);
                        if(recv>0)
                            updateProbeResult(recv/10.0);
                        else
                            updateProbeResult(-0.1);
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }finally {
                    probeHandler.postDelayed(this,2500);
                }
            }
        };
        probeResult = new MutableLiveData<>(-0.1);
        probeHandler.post(probeHost);


    }

    private void updateContact(Contact contact) {
        dao.insertContact(new ContactEntity(contact));
    }

    private MutableLiveData<Double> probeResult;
    public MutableLiveData<Double> getLiveProbeResult(){
        return probeResult;
    }
    public void updateProbeResult(double result){
        if(probeResult.getValue()==null||probeResult.getValue()==result)    return;
        uiHandler.post(()->probeResult.setValue(result));
    }



    private ContactDao dao;
    private SPManager sp;
    private ClientTunnel tunnel;

    public SPManager getManager(){
        return sp;
    }

    public void update() {
        tunnel.setSrc(sp.getUUID());
        tunnel.setPort(sp.getPort());
        tunnel.update(sp.getHostname());
    }


    public void startCalling(Contact contact){
        byte[] ourPk = dispatcher.call(contact);
        if(ourPk==null) return;
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(Ctrl.CALL.ordinal());
        Network.writeBytes(buf,ourPk);
        sign(ourPk,contact,buf);
        byte[] pkt = Network.readAllBytes(buf);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if(!(dispatcher.isOutgoing(contact.uuid)&&dispatcher.talker==null))    return;
                write(pkt, contact.uuid);
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(r);
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
        updateContact(contact);
    }
    public void write(byte[] data, UUID dst){
        tunnel.send(data,dst);
    }
    public void alert(){
        if(mActivity==null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.dangerous);
        builder.setMessage(R.string.mitm_warning_msg);
        builder.setPositiveButton(R.string.panic, (dialog, which) -> {});
        builder.setNegativeButton(R.string.remain_calm, (dialog, which) -> {});
        builder.show();
    }
    public void reject(Contact contact){
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(Ctrl.CALL_REJECT.ordinal());
        write(Network.readAllBytes(buf),contact.uuid);
        dispatcher.reject(contact);
    }
    public void acceptCall(Contact contact){
        byte[] ourPk = dispatcher.accept(contact);
        if(ourPk==null) return;
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(Ctrl.CALL_RESPONSE.ordinal());
        Network.writeBytes(buf,ourPk);
        if (!sign(ourPk, contact, buf)) return;
        write(Network.readAllBytes(buf),contact.uuid);
    }

    private boolean sign(byte[] data, Contact contact, ByteBuf buf) {
        try {
            if (!keyStore.containsAlias(Crypto.to64(contact.uuid.getBytes()))) return false;
            //s.initSign(((KeyStore.PrivateKeyEntry) keyStore.getEntry(Crypto.to64(contact.uuid.getBytes()), null)).getPrivateKey());
            s.initSign((PrivateKey) keyStore.getKey(Crypto.to64(contact.uuid.getBytes()), null));
            s.update(data);
            byte[] sign = s.sign();
            Network.writeBytes(buf, sign);
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnrecoverableEntryException | SignatureException | KeyStoreException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    public void cut(){
        dispatcher.cut();
    }
}