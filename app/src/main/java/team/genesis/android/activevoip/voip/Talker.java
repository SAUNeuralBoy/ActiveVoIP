package team.genesis.android.activevoip.voip;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;

import androidx.annotation.NonNull;

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
import team.genesis.android.activevoip.Crypto;
import team.genesis.android.activevoip.Network;
import team.genesis.android.activevoip.UI;
import team.genesis.android.activevoip.VoIPService;
import team.genesis.android.activevoip.network.ClientTunnel;
import team.genesis.android.activevoip.network.Ctrl;
import team.genesis.concentus.OpusApplication;
import team.genesis.concentus.OpusDecoder;
import team.genesis.concentus.OpusEncoder;
import team.genesis.concentus.OpusException;
import team.genesis.concentus.OpusSignal;
import team.genesis.data.UUID;

import static java.lang.System.exit;

public class Talker {
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION;
    public static final int SAMPLE_RATE = 8000;
    public static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
    public static final int RECORD_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    public static final int FRAME_INTERVAL = 20;
    public static final int ASYNC_TOLERANCE = 100/FRAME_INTERVAL;
    public static final int PL_REFRESH_INTERVAL = 50;

    private final UUID otherId;
    private final SecretKey secretKey;
    private ClientTunnel tunnel;
    private Cipher cipherEncrypt,cipherDecrypt;
    private boolean isRecording;
    private AudioRecord audioRecord;
    private Handler asyncHandler;
    private Handler pullHandler;

    private AudioTrack audioTrack;
    private final AudioManager audioManager;
    private final VoIPService mService;

    public Talker(UUID ourId, UUID otherId, SecretKey secretKey, String hostName, int port, AudioManager audioManager, VoIPService service){
        deviceManager = new DeviceManager();
        try {
            cipherEncrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipherDecrypt = Cipher.getInstance("AES/CBC/PKCS5Padding",cipherEncrypt.getProvider());
        } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
            exit(0);
        }
        this.otherId = otherId;
        this.secretKey = secretKey;
        this.audioManager = audioManager;
        this.mService = service;
        try {
            tunnel = new ClientTunnel("voip",hostName,port,ourId);
        } catch (SocketException e) {
            e.printStackTrace();
            exit(0);
        }
    }
    private static abstract class Codec {
        public abstract byte[] encode(short[] pcm,int length);
        public abstract short[] decode(byte[] encoded);
        public abstract short[] predict(int defaultSize);
    }
    private static class OpusCodec extends Codec {
        public static final int BITRATE = 24000;
        public static final int BUF_SIZE = 2048;
        private final OpusEncoder encoder;
        private final OpusDecoder decoder;
        private final byte[] buf;
        private final short[] sbuf;
        private byte[] last;
        private final int frameSize;
        public OpusCodec(int frameSize) {
            try {
                decoder = new OpusDecoder(SAMPLE_RATE, 1);
                encoder = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            } catch (OpusException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
            encoder.setBitrate(BITRATE);
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_VOICE);
            encoder.setComplexity(10);
            buf = new byte[BUF_SIZE];
            sbuf = new short[BUF_SIZE];
            last = null;
            this.frameSize = frameSize;
        }
        public byte[] encode(short[] pcm,int length){
            try {
                return Arrays.copyOf(buf,encoder.encode(pcm,0,frameSize, buf,0, buf.length));/*
                System.out.println("o");
                System.out.println(pcm.length);
                System.out.println("t");
                byte[] encoded = Arrays.copyOf(buf,encoder.encode(Arrays.copyOf(pcm,length),0,frameSize, buf,0, buf.length));
                System.out.println(decode(encoded).length);
                return encoded;*/
            } catch (OpusException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }
        public short[] _decode(byte[] encoded,boolean fec){
            try {
                return Arrays.copyOf(sbuf,decoder.decode(encoded,0,encoded.length,sbuf,0,frameSize,fec));
            } catch (OpusException e) {
                e.printStackTrace();
                throw new RuntimeException();
            }
        }
        public short[] decode(byte[] encoded){
            last = Arrays.copyOf(encoded,encoded.length);
            return _decode(encoded,false);
        }
        public short[] predict(int defaultSize){
            //ut.println("loss");
            if(last==null)
                return new short[defaultSize];
            return _decode(last,true);
        }
    }
    /*
    public static class IlbcCodec extends Codec{
        private final byte[] buf;
        public IlbcCodec() {
            AudioCodec.audio_codec_init(20);
            buf = new byte[2048];
        }
        public byte[] encode(byte[] pcm){
            return Arrays.copyOf(buf,AudioCodec.audio_encode(pcm,0,pcm.length,buf,0));
        }
        public byte[] decode(byte[] encoded){
            return Arrays.copyOf(buf,AudioCodec.audio_decode(encoded,0,encoded.length,buf,buf.length));
        }
        public byte[] predict(int defaultSize){
            return decode(new byte[defaultSize]);
        }
    }*/
    public void startTalking(){



        int recordBufSize = (SAMPLE_RATE/1000)* FRAME_INTERVAL;//AudioRecord.getMinBufferSize(SAMPLE_RATE,CHANNEL, RECORD_ENCODING)*2;
        Codec codec = new OpusCodec(recordBufSize);
        audioRecord = new AudioRecord(AUDIO_SOURCE,SAMPLE_RATE, CHANNEL, RECORD_ENCODING,recordBufSize);
        short[] buf = new short[recordBufSize];

        isRecording = true;
        audioRecord.startRecording();
        asyncHandler = UI.getCycledHandler("voip_async");
        pullHandler = UI.getCycledHandler("voip_pull");
        Handler uiHandler = new Handler();

        Runnable encode = new Runnable() {
            @Override
            public void run() {
                //System.out.println(System.currentTimeMillis());
                if (!isRecording) return;
                int len = audioRecord.read(buf, 0, recordBufSize, AudioRecord.READ_BLOCKING);
                //System.out.println(len);
                if (len < 0) return;
                Talker.this.write(Ctrl.TALK_PACK, codec.encode(buf,len));
                pullHandler.post(this);
            }
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
            Talker.Request req;
            try {
                req = parse(msg.data);
            } catch (Crypto.DecryptException e) {
                return;
            }
            if (req==null)  return;
            switch (req.ctrl){
                case TALK_PACK:
                    if(req.data==null||req.data.length<1)  return;
                    incoming.add(req.data);
                    break;
                case TALK_CUT:
                    mService.cut();
            }
        });
        AtomicInteger total= new AtomicInteger();
        AtomicInteger lost= new AtomicInteger();
        Runnable play = () -> {
            if(!isRecording)    return;
            short[] pcm;
            total.incrementAndGet();
            if(incoming.size()<1) {
                pcm = codec.predict(recordBufSize);
                lost.incrementAndGet();
            }
            else {
                pcm = codec.decode(incoming.remove(0));
                if(incoming.size()> ASYNC_TOLERANCE)  incoming.remove(0);
            }
            if(pcm.length>0) {
                //if(audioTrack.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING) audioTrack.play();
                //System.out.println(System.currentTimeMillis());
                audioTrack.write(pcm, 0, pcm.length, AudioTrack.WRITE_NON_BLOCKING);
            }
            if(total.get()> PL_REFRESH_INTERVAL) {
                System.out.println(((double) lost.get() / total.get()) * 100);
                total.set(0);
                lost.set(0);
            }
        };
        Runnable detect = new Runnable() {
            @Override
            public void run() {
                if(!isRecording)    return;
                deviceManager.updateDevices(audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS),
                        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
                AudioDeviceInfo inPriority = deviceManager.getDevice(DeviceManager::deviceIsInput);
                if(shouldUpdatePreferredIn(inPriority
                        ,audioRecord.getRoutedDevice(),audioRecord.getPreferredDevice()))
                    setInDevice(inPriority);
                inPriority = deviceManager.getDevice(DeviceManager::deviceIsOutput);
                if(shouldUpdatePreferredOut(inPriority,audioTrack.getRoutedDevice(),audioTrack.getPreferredDevice()))
                    setOutDevice(inPriority);
                uiHandler.postDelayed(this,1000);
            }
        };
        uiHandler.post(detect);
        new Thread(() -> {
            while (isRecording) {
                asyncHandler.post(play);
                //asyncHandler.post(encode);
                try {
                    //noinspection BusyWait
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }).start();
        pullHandler.post(encode);
    }
    private void write(Ctrl ctrl, byte[] data){try {
        //System.out.println(data.length);
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
    public Talker.Request parse(byte[] pack) throws IndexOutOfBoundsException, Crypto.DecryptException {
        if(pack.length==4){
            Talker.Request req = new Talker.Request();
            ByteBuf buf = Unpooled.copiedBuffer(pack);
            req.ctrl = Ctrl.values()[buf.readInt()];
            req.data = null;
            return req;
        }
        if(pack.length<=4+16)  throw new IndexOutOfBoundsException();
        Talker.Request req = new Talker.Request();
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
    }
    private AudioDeviceInfo getCurrentDevice(AudioDeviceInfo routing,AudioDeviceInfo preferred){
        if(preferred!=null) return preferred;
        return routing;
    }
    private boolean shouldUpdatePreferredIn(AudioDeviceInfo inPriority,AudioDeviceInfo routing,AudioDeviceInfo preferred){
        if(inPriority==null)    return false;
        AudioDeviceInfo currentDevice = getCurrentDevice(routing, preferred);
        if(currentDevice==null)   return true;
        return currentDevice.getId()!=inPriority.getId();
    }
    private boolean shouldUpdatePreferredOut(AudioDeviceInfo inPriority,AudioDeviceInfo routing,AudioDeviceInfo preferred){
        if(inPriority==null)    return false;
        if(getCurrentDevice(routing, preferred)==null)  return true;
        return !isUsingAttached() || !DeviceManager.deviceIsAttached(inPriority);
    }
    public void checkBlueTooth(AudioDeviceInfo device){
        if(device.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO&&(!audioManager.isBluetoothScoOn()))
            audioManager.setBluetoothScoOn(true);
        if(device.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP&&(!audioManager.isBluetoothA2dpOn()))
            //noinspection deprecation
            audioManager.setBluetoothA2dpOn(true);
    }
    public void setInDevice(AudioDeviceInfo device){
        if(audioRecord==null||device==null)   return;
        checkBlueTooth(device);
        audioRecord.setPreferredDevice(device);
    }
    public void setOutDevice(AudioDeviceInfo device){
        if(audioTrack==null||device==null)   return;
        checkBlueTooth(device);
        audioTrack.setPreferredDevice(device);
    }

    public boolean isUsingAttached(){
        if(audioTrack==null) return false;
        return DeviceManager.deviceIsAttached(getCurrentDevice(audioTrack.getRoutedDevice(),audioTrack.getPreferredDevice()));
    }
    public boolean isUsingSpeaker(){
        if(!isUsingAttached())  return false;
        return DeviceManager.deviceIsSpeaker(getCurrentDevice(audioTrack.getRoutedDevice(),audioTrack.getPreferredDevice()));
    }
    public void switchSpeaker(){
        if(!isUsingAttached())  return;
        if(isUsingSpeaker())
            setOutDevice(deviceManager.getEarphone());
        else
            setOutDevice(deviceManager.getSpeaker());
    }
    private static class DeviceManager{
        private static class Device{
            AudioDeviceInfo deviceInfo;
            int type;

            public Device(AudioDeviceInfo deviceInfo, int type) {
                this.deviceInfo = deviceInfo;
                this.type = type;
            }

            public static final int TYPE_INPUT = 0;
            public static final int TYPE_OUTPUT = 1;
        }
        private final List<Device> devices;
        public DeviceManager(){
            devices = new LinkedList<>();
        }
        public AudioDeviceInfo getSpeaker(){
            return getDevice(DeviceManager::deviceIsSpeaker,DeviceManager::deviceIsOutput);
        }
        public AudioDeviceInfo getEarphone(){
            return getDevice(device -> deviceIsAttached(device)&&(!deviceIsSpeaker(device)), DeviceManager::deviceIsOutput);
        }
        private interface DeviceInfoCondition{
            boolean match(Device device);
        }
        private AudioDeviceInfo getDevice(DeviceInfoCondition c1,DeviceInfoCondition c2){
            AudioDeviceInfo device = getDevice(c1);
            if(device==null)    device = getDevice(c2);
            return device;
        }
        private AudioDeviceInfo getDevice(DeviceInfoCondition condition){
            for(Device i:devices)
                if(condition.match(i))  return i.deviceInfo;
            return null;
        }
        public void updateDevices(AudioDeviceInfo[] inputDevices,AudioDeviceInfo[] outputDevices){
            devices.clear();
            for(AudioDeviceInfo i:inputDevices) devices.add(new Device(i,Device.TYPE_INPUT));
            for(AudioDeviceInfo i:outputDevices) devices.add(new Device(i,Device.TYPE_INPUT));
            devices.sort((o1, o2) -> Integer.compare(priority(o1.deviceInfo), priority(o2.deviceInfo)));
        }
        public static int priority(@NonNull AudioDeviceInfo device){
            if(device.getType()==AudioDeviceInfo.TYPE_WIRED_HEADPHONES||device.getType()==AudioDeviceInfo.TYPE_WIRED_HEADSET)
                return 1;
            if(device.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||device.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
                return 2;
            if(deviceIsAttached(device)||device.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC)    return 3;
            return 4;
        }
        private static boolean deviceIsAttached(Device device){
            return deviceIsAttached(device.deviceInfo);
        }
        public static boolean deviceIsAttached(AudioDeviceInfo deviceInfo){
            if(deviceInfo==null)    return false;
            return deviceInfo.getType()==AudioDeviceInfo.TYPE_BUILTIN_EARPIECE||
                    deviceInfo.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER||
                    deviceInfo.getType()==AudioDeviceInfo.TYPE_TELEPHONY;
        }

        private static boolean deviceIsSpeaker(AudioDeviceInfo deviceInfo){
            if(deviceInfo==null)    return false;
            return deviceInfo.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        }
        private static boolean deviceIsSpeaker(Device device){
            return deviceIsSpeaker(device.deviceInfo);
        }
        public static boolean deviceIsInput(Device device){
            return device.type==Device.TYPE_INPUT;
        }
        private static boolean deviceIsOutput(Device device){
            return device.type==Device.TYPE_OUTPUT;
        }
    }
    private final DeviceManager deviceManager;
}
