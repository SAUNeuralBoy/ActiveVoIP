package team.genesis.android.activevoip;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.net.SocketException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import team.genesis.android.activevoip.network.ClientTunnel;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.android.activevoip.ui.talking.TalkingFragment;
import team.genesis.android.activevoip.ui.talking.TalkingViewModel;
import team.genesis.android.activevoip.voip.AudioCodec;
import team.genesis.data.UUID;
import team.genesis.tunnels.ActiveDatagramTunnel;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.exit;

public class VoIPService extends Service {
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    public static final int SAMPLE_RATE = 8000;
    public static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    public static final int RECORD_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private UUID ourId;
    private UUID otherId;
    private SecretKey secretKey;
    private ClientTunnel tunnel;
    private Cipher cipherEncrypt,cipherDecrypt;
    private boolean isRecording;
    private TalkingFragment fragment;
    private AudioRecord audioRecord;
    private Handler asyncHandler;

    private AudioTrack audioTrack;
    public VoIPService(){
        try {
            cipherEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipherDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding",cipherEncrypt.getProvider());
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
        }
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
                .setContentTitle("Voice Communication")
                .setContentText("Talking")
                .setWhen(System.currentTimeMillis()).build());
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    public void init(UUID ourId, UUID otherId, SecretKey secretKey, String hostName, int port, TalkingFragment fragment){
        this.ourId = ourId;
        this.otherId = otherId;
        this.secretKey = secretKey;
        this.fragment = fragment;
        try {
            tunnel = new ClientTunnel("voip",hostName,port,ourId);
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
        }
    }
    public void startTalking(){
        AudioCodec.audio_codec_init(20);


        int recordBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,CHANNEL, RECORD_ENCODING)*32;
        audioRecord = new AudioRecord(AUDIO_SOURCE,SAMPLE_RATE, CHANNEL, RECORD_ENCODING,recordBufSize);
        byte[] buf = new byte[recordBufSize];

        isRecording = true;
        audioRecord.startRecording();
        asyncHandler = UI.getCycledHandler("voip_async");
        Handler uiHandler = new Handler();

        Runnable encode = () -> {
            if(!isRecording)    return;
            int len = audioRecord.read(buf,0,recordBufSize,AudioRecord.READ_NON_BLOCKING);
            if(len<0)   return;
            byte[] encoded = new byte[2048];
            write(Ctrl.TALK_PACK,Arrays.copyOf(encoded,AudioCodec.audio_encode(buf,0,len,encoded,0)));
        };

        List<byte[]> incoming = Collections.synchronizedList(new LinkedList<>());
        audioTrack = new AudioTrack.Builder().setAudioFormat(new AudioFormat.Builder().setEncoding(RECORD_ENCODING)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
        .setBufferSizeInBytes(recordBufSize)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()).build();
        audioTrack.setVolume(AudioTrack.getMaxVolume());
        audioTrack.play();
        tunnel.setRecvListener(msg -> {
            if(!msg.src.equals(otherId))    return;
            Request req;
            try {
                req = parse(msg.data);
            } catch (Crypto.DecryptException e) {
                return;
            }
            if (req==null||req.data==null)  return;
            switch (req.ctrl){
                case TALK_PACK:
                    incoming.add(req.data);
                    break;
                case TALK_CUT:
                    fragment.onCut();
            }
        });
        Runnable play = () -> {
            if(!isRecording)    return;
            if(incoming.size()>1){
                byte[] data = incoming.remove(0);
                byte[] sample = new byte[recordBufSize];
                int len = AudioCodec.audio_decode(data,0,data.length,sample,0);
                if(len<=0)  return;
                audioTrack.write(sample,0,len,AudioTrack.WRITE_NON_BLOCKING);
                if(incoming.size()>25)  incoming.clear();
            }
        };
        AudioManager audioManager = (AudioManager) VoIPService.this.getSystemService(Context.AUDIO_SERVICE);
        Runnable detect = new Runnable() {
            @Override
            public void run() {
                if(!isRecording)    return;
                AudioDeviceInfo device = null,earPhone=null,stereo=null;
                for(AudioDeviceInfo i:audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){
                    if(i.getType()==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||i.getType()==AudioDeviceInfo.TYPE_WIRED_HEADSET){
                        device = i;
                        break;
                    }
                    if(i.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||i.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
                        device = i;
                    if(i.getType()==AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
                        earPhone = i;
                    if(i.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
                        stereo = i;
                }
                if(device!=null) {
                    audioTrack.setPreferredDevice(device);
                    fragment.disableSelect();
                }
                else if(earPhone!=null&&stereo!=null)   fragment.passDevice(earPhone,stereo);
                uiHandler.postDelayed(this,200);
            }
        };
        uiHandler.post(detect);

        new Thread(() -> {
            while (isRecording) {
                asyncHandler.post(encode);
                asyncHandler.post(play);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }).start();
    }
    private void write(Ctrl ctrl, byte[] data){try {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(ctrl.ordinal());
        cipherEncrypt.init(Cipher.ENCRYPT_MODE, secretKey);
        buf.writeBytes(cipherEncrypt.getIV());
        buf.writeBytes(cipherEncrypt.doFinal(data));
        tunnel.send(Network.readAllBytes(buf), otherId);
    } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
        e.printStackTrace();
        exit(0);
    }
    }
    public Request parse(byte[] pack) throws IndexOutOfBoundsException, Crypto.DecryptException {
        if(pack.length<=4+16)  throw new IndexOutOfBoundsException();
        Request req = new Request();
        ByteBuf buf = Unpooled.copiedBuffer(pack);
        req.ctrl = Ctrl.values()[buf.readInt()];
        byte[] cipher = new byte[pack.length-4];
        buf.readBytes(cipher);
        try {
            cipherDecrypt.init(Cipher.DECRYPT_MODE,secretKey,new IvParameterSpec(Arrays.copyOf(cipher,16)));
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            e.printStackTrace();
            exit(0);
            return null;
        }
        try {
            req.data = cipherDecrypt.doFinal(cipher,16,cipher.length-16);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            throw new Crypto.DecryptException();
        }
        return req;
    }
    public class VoIPBinder extends Binder{
        public VoIPService getService(){
            return VoIPService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new VoIPBinder();
    }

    private static class Request{
        Ctrl ctrl;
        byte[] data;
    }

    public void cut(){
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(Ctrl.TALK_CUT.ordinal());
        tunnel.send(Network.readAllBytes(buf),otherId);
        isRecording = false;
        asyncHandler.getLooper().quit();
        audioRecord.stop();
        audioRecord.release();
        tunnel.release();
        audioTrack.stop();
        audioTrack.release();
        stopSelf();
    }
    public void setOutDevice(AudioDeviceInfo device){
        audioTrack.setPreferredDevice(device);
    }
}