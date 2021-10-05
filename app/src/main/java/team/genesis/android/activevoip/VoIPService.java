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
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
    private KeyPair kp;
    private KeyStore keyStore;
    private Signature s;
    private byte[] derivedKey;
    private UUID ourId;
    private UUID otherId;
    private byte[] otherPkt;

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
        mStat = new MutableLiveData<>(Status.READY);
        reset();
        dao = Room.databaseBuilder(this, ContactDB.class, "ContactEntity").allowMainThreadQueries().build().getDao();
        sp = SPManager.getManager(this);
        try {
            tunnel = new ClientTunnel("main",sp.getHostname(),sp.getPort(),sp.getUUID());
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
            return;
        }
        countDown = null;
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
                        if (!keyStore.containsAlias(Crypto.to64(msg.src.getBytes())))
                            return;
                    }catch (KeyStoreException e){
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
                    if(getStat()!=Status.READY)  return;
                    Contact contact = ContactDB.getContactOrDelete(dao,msg.src);
                    if(contact==null)   return;
                    mContact = contact;

                    s.initVerify(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(contact.otherPk)));
                    byte[] otherPk = Network.readNbytes(buf,500);
                    s.update(otherPk);
                    byte[] sign = Network.readNbytes(buf,500);
                    if(!s.verify(sign)){
                        alert();
                        reset();
                        return;
                    }
                    setStat(Status.INCOMING);

                    break;
                }
                case CALL_REJECT:{
                    if(getStat()!=Status.CALLING)  return;
                    if(!msg.src.equals(mContact.uuid))    return;
                    setStat(Status.REJECTED);
                    break;
                }
                case CALL_RESPONSE:{
                    if(getStat()!=Status.CALLING)  return;
                    if(!mContact.uuid.equals(msg.src))  return;
                    
                    s.initVerify(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(mContact.otherPk)));
                    otherPkt = Network.readNbytes(buf,500);
                    s.update(otherPkt);
                    byte[] sign = Network.readNbytes(buf,500);
                    if(!s.verify(sign)){
                        alert();
                        return;
                    }
                    if(!Arrays.equals(kp.getPublic().getEncoded(),Network.readNbytes(buf,500))) return;
                    performECDH();
                    ByteBuf ack = Unpooled.buffer();
                    ack.writeInt(Ctrl.CALL_ACK.ordinal());
                    write(Network.readAllBytes(ack), msg.src);
                    startTalking();
                    break;
                }
                case CALL_ACK:{
                    if(getStat()!=Status.WAITING)   return;
                    if(!mContact.uuid.equals(msg.src))    return;
                    startTalking();
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
    private MutableLiveData<Double> probeResult;
    public MutableLiveData<Double> getLiveProbeResult(){
        return probeResult;
    }
    public void updateProbeResult(double result){
        if(probeResult.getValue()==null||probeResult.getValue()==result)    return;
        uiHandler.post(()->probeResult.setValue(result));
    }

    private void performECDH() {try {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(kp.getPrivate());
        ka.doPhase(KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC).generatePublic(new X509EncodedKeySpec(otherPkt)), true);
        byte[] sharedSecret = ka.generateSecret();
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        hash.update(sharedSecret);
        // Simple deterministic ordering
        List<ByteBuffer> keys = Arrays.asList(ByteBuffer.wrap(kp.getPublic().getEncoded()), ByteBuffer.wrap(otherPkt));
        Collections.sort(keys);
        hash.update(keys.get(0));
        hash.update(keys.get(1));
        derivedKey = hash.digest();
        ourId = new UUID(Crypto.md5(kp.getPublic().getEncoded()));
        otherId = new UUID(Crypto.md5(otherPkt));
    }catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException e) {
        e.printStackTrace();
        exit(0);
    }
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


    public boolean startCalling(Contact contact){
        mContact = contact;
        ByteBuf buf = Unpooled.buffer();

        byte[] signed = newTalk();
        if (signed==null){
            reset();
            return false;
        }
        buf.writeBytes(signed);
        setStat(Status.CALLING);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if (getStat()!=Status.CALLING) return;
                write(Network.readAllBytes(buf), mContact.uuid);
                uiHandler.postDelayed(this, 1000);
            }
        };
        uiHandler.post(r);
        return true;
    }

    private byte[] newTalk(){try {
        ByteBuf buf = Unpooled.buffer();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC);
        kpg.initialize(256);
        kp = kpg.generateKeyPair();
        byte[] ourPk = kp.getPublic().getEncoded();
        buf.writeInt(Ctrl.CALL.ordinal());
        Network.writeBytes(buf, ourPk);

        if (!keyStore.containsAlias(Crypto.to64(mContact.uuid.getBytes()))) return null;
        s.initSign(((KeyStore.PrivateKeyEntry) keyStore.getEntry(Crypto.to64(mContact.uuid.getBytes()), null)).getPrivateKey());
        s.update(ourPk);
        byte[] sign = s.sign();
        Network.writeBytes(buf, sign);
        return Network.readAllBytes(buf);
    } catch (InvalidKeyException | SignatureException | UnrecoverableEntryException | KeyStoreException e) {
        return null;
    } catch (NoSuchAlgorithmException e) {
        e.printStackTrace();
        exit(0);
        return null;
    }
    }

    private Contact mContact;
    public Contact getContact(){
        return mContact;
    }
    private MutableLiveData<Status> mStat;
    private Status getStat(){
        return mStat.getValue();
    }
    private void setStat(Status stat){
        if(mStat.getValue()!=stat)
            mStat.postValue(stat);
    }
    public MutableLiveData<Status> getLiveStat(){
        return mStat;
    }
    private CountDownLatch countDown;


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
    public void alert(){
        if(mActivity==null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        builder.setTitle(R.string.dangerous);
        builder.setMessage(R.string.mitm_warning_msg);
        builder.setPositiveButton(R.string.panic, (dialog, which) -> {});
        builder.setNegativeButton(R.string.remain_calm, (dialog, which) -> {});
        builder.show();
    }

    public void reset(){
        setStat(Status.READY);
        mContact = null;
    }
    public void reject(){
        if(mContact!=null){
            ByteBuf buf = Unpooled.buffer();
            buf.writeInt(Ctrl.CALL_REJECT.ordinal());
            write(Network.readAllBytes(buf),mContact.uuid);
            reset();
        }
    }
    public void acceptCall(){
        if(getStat()!=Status.INCOMING)  return;
        ByteBuf buf = Unpooled.buffer();
        byte[] signed = newTalk();
        if(signed==null){
            reset();
            return;
        }
        buf.writeBytes(signed);
        Network.writeBytes(buf,otherPkt);
        setStat(Status.WAITING);
        write(Network.readAllBytes(buf),mContact.uuid);
    }
    public enum Status{
        READY,CALLING,INCOMING,REJECTED,WAITING,TALKING
    }
    public void startTalking(){
        talker = new Talker(ourId,otherId,new SecretKeySpec(derivedKey,"AES"),sp.getHostname(),sp.getPort(), (AudioManager) getSystemService(Context.AUDIO_SERVICE));
        talker.startTalking();
        setStat(Status.TALKING);
    }
    public void cut(){
        if(talker==null)    return;
        talker.cut();
        talker = null;
    }
    private Talker talker;
}