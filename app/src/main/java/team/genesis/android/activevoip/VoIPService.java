package team.genesis.android.activevoip;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
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
import team.genesis.data.UUID;

import static java.lang.System.exit;

public class VoIPService extends Service {
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    public static final int SAMPLE_RATE = 44100;
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
        int recordBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,CHANNEL, RECORD_ENCODING);
        AudioRecord audioRecord = new AudioRecord(AUDIO_SOURCE,SAMPLE_RATE, CHANNEL, RECORD_ENCODING,recordBufSize);
        byte[] buf = new byte[recordBufSize];
        isRecording = true;
        audioRecord.startRecording();
        Handler recordHandler = UI.getCycledHandler("voip_record");
        Runnable r = new Runnable() {
            @Override
            public void run() {
                if(!isRecording)    return;
                if(audioRecord.read(buf,0,recordBufSize)==AudioRecord.SUCCESS){

                }
                recordHandler.post(this);
            }
        };
        recordHandler.post(r);

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