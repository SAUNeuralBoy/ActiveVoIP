package team.genesis.android.activevoip.voip;

import android.media.AudioDeviceInfo;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.List;

class DeviceManager {
    public static class Device{
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
        return getDevice(device -> deviceIsSpeaker(device)&&deviceIsOutput(device), DeviceManager::deviceIsOutput);
    }
    public AudioDeviceInfo getEarphone(){
        return getDevice(device -> deviceIsAttached(device)&&!deviceIsSpeaker(device)&&deviceIsOutput(device), DeviceManager::deviceIsOutput);
    }
    public interface DeviceInfoCondition{
        boolean match(Device device);
    }
    public AudioDeviceInfo getDevice(DeviceInfoCondition c1, DeviceInfoCondition c2){
        AudioDeviceInfo device = getDevice(c1);
        if(device==null)    device = getDevice(c2);
        return device;
    }
    public AudioDeviceInfo getDevice(DeviceInfoCondition condition){
        for(Device i:devices)
            if(condition.match(i))  return i.deviceInfo;
        return null;
    }
    public void updateDevices(AudioDeviceInfo[] inputDevices,AudioDeviceInfo[] outputDevices){
        devices.clear();
        for(AudioDeviceInfo i:inputDevices) devices.add(new Device(i, Device.TYPE_INPUT));
        for(AudioDeviceInfo i:outputDevices) devices.add(new Device(i, Device.TYPE_OUTPUT));
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

    public static boolean deviceIsSpeaker(AudioDeviceInfo deviceInfo){
        if(deviceInfo==null)    return false;
        return deviceInfo.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
    }
    public static boolean deviceIsSpeaker(Device device){
        return deviceIsSpeaker(device.deviceInfo);
    }
    public static boolean deviceIsInput(Device device){
        return device.type== Device.TYPE_INPUT;
    }
    public static boolean deviceIsOutput(Device device){
        return device.type== Device.TYPE_OUTPUT;
    }
}
