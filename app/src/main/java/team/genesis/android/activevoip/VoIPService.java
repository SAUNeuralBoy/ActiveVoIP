package team.genesis.android.activevoip;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

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
    public VoIPService(){
        try {
            cipherEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipherDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
        }
    }
    public void init(UUID ourId,UUID otherId,SecretKey secretKey,String hostName,int port){
        this.ourId = ourId;
        this.otherId = otherId;
        this.secretKey = secretKey;
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
        AudioRecord audioRecord = new AudioRecord(AUDIO_SOURCE,SAMPLE_RATE, CHANNEL, RECORD_ENCODING,recordBufSize);
        byte[] buf = new byte[recordBufSize];
        isRecording = true;
        audioRecord.startRecording();
        Handler recordHandler = UI.getCycledHandler("voip_record");
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

        AudioTrack audioTrack = new AudioTrack.Builder().setAudioFormat(new AudioFormat.Builder().setEncoding(RECORD_ENCODING)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(CHANNEL).build())
        .setBufferSizeInBytes(recordBufSize)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()).build();
        tunnel.setRecvListener(new ClientTunnel.RecvListener() {
            @Override
            public void onRecv(ActiveDatagramTunnel.Incoming msg) {
                if(!msg.src.equals(otherId))    return;
                Request req;
                try {
                    req = parse(msg.data);
                } catch (Crypto.DecryptException e) {
                    return;
                }
                switch (req.ctrl){
                    case TALK_PACK:
                        byte[] sample = new byte[recordBufSize];
                        int len = AudioCodec.audio_decode(req.data,0,req.data.length,sample,0);
                        if(len<=0)  return;
                        audioTrack.write(sample,0,len,AudioTrack.WRITE_NON_BLOCKING);
                        break;
                }
            }
        });
    }
    private void write(Ctrl ctrl, byte[] data){try {
        ByteBuf buf = Unpooled.buffer();
        buf.writeInt(ctrl.ordinal());
        cipherEncrypt.init(Cipher.ENCRYPT_MODE, secretKey);
        buf.writeBytes(cipherEncrypt.getIV());
        buf.writeBytes(cipherEncrypt.doFinal(data));
        tunnel.send(buf.array(), otherId);
    } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException e) {
        e.printStackTrace();
        exit(0);
    }
    }
    public Request parse(byte[] pack) throws IndexOutOfBoundsException, Crypto.DecryptException {
        if(pack.length<=4+12)  throw new IndexOutOfBoundsException();
        Request req = new Request();
        ByteBuf buf = Unpooled.copiedBuffer(pack);
        req.ctrl = Ctrl.values()[buf.readInt()];
        byte[] cipher = new byte[pack.length-4];
        buf.readBytes(cipher);
        try {
            cipherDecrypt.init(Cipher.DECRYPT_MODE,secretKey,new IvParameterSpec(Arrays.copyOfRange(cipher,4,4+12)));
        } catch (InvalidAlgorithmParameterException | InvalidKeyException e) {
            e.printStackTrace();
            exit(0);
            return null;
        }
        try {
            req.data = cipherDecrypt.doFinal(cipher,4+12,pack.length-4-12);
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
        return new Binder();
    }
    private static class Request{
        Ctrl ctrl;
        byte[] data;
    }
}