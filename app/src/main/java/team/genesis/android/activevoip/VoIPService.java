package team.genesis.android.activevoip;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
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

import static java.lang.System.exit;

public class VoIPService extends Service {
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
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
    private Handler recordHandler;
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


        int recordBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,CHANNEL, RECORD_ENCODING)*4;
        audioRecord = new AudioRecord(AUDIO_SOURCE,SAMPLE_RATE, CHANNEL, RECORD_ENCODING,recordBufSize);
        byte[] buf = new byte[recordBufSize];
        isRecording = true;
        audioRecord.startRecording();
        recordHandler = UI.getCycledHandler("voip_record");
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if(!isRecording)    return;
                recordHandler.postDelayed(this,20);
                int len = audioRecord.read(buf,0,recordBufSize);
                if(len>0){
                    byte[] encoded = new byte[len];
                    write(Ctrl.TALK_PACK,Arrays.copyOf(encoded,AudioCodec.audio_encode(buf,0,len,encoded,0)));
                }
            }
        };
        recordHandler.post(r);

        audioTrack = new AudioTrack.Builder().setAudioFormat(new AudioFormat.Builder().setEncoding(RECORD_ENCODING)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
        .setBufferSizeInBytes(recordBufSize)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()).build();
        audioTrack.setVolume(1.0f);
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
                    byte[] sample = new byte[recordBufSize];
                    int len = AudioCodec.audio_decode(req.data,0,req.data.length,sample,0);
                    if(len<=0)  return;
                    audioTrack.write(sample,0,len,AudioTrack.WRITE_NON_BLOCKING);
                    break;
                case TALK_CUT:
                    fragment.onCut();
            }
        });
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
        recordHandler.getLooper().quit();
        audioRecord.stop();
        audioRecord.release();
        tunnel.release();
        audioTrack.stop();
        audioTrack.release();
        stopSelf();
    }
}