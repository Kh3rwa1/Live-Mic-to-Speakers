package com.word.way.audio;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

/**
 * Classifies audio output devices for latency characteristics.
 */
public final class AudioRouteClassifier {

    private AudioRouteClassifier() { }

    public static boolean isBluetooth(int deviceType) {
        if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET
                    || deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWiredOrUsb(int deviceType) {
        if (deviceType == AudioDeviceInfo.TYPE_WIRED_HEADSET
                || deviceType == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                || deviceType == AudioDeviceInfo.TYPE_USB_DEVICE
                || deviceType == AudioDeviceInfo.TYPE_USB_HEADSET
                || deviceType == AudioDeviceInfo.TYPE_USB_ACCESSORY
                || deviceType == AudioDeviceInfo.TYPE_LINE_ANALOG
                || deviceType == AudioDeviceInfo.TYPE_LINE_DIGITAL
                || deviceType == AudioDeviceInfo.TYPE_AUX_LINE) {
            return true;
        }
        return false;
    }

    public static boolean hasBluetoothOutput(int[] deviceTypes) {
        if (deviceTypes == null) return false;
        for (int type : deviceTypes) {
            if (isBluetooth(type)) return true;
        }
        return false;
    }

    public static boolean hasWiredOrUsbOutput(int[] deviceTypes) {
        if (deviceTypes == null) return false;
        for (int type : deviceTypes) {
            if (isWiredOrUsb(type)) return true;
        }
        return false;
    }

    public static boolean isBuiltinSpeakerActive(int[] deviceTypes) {
        return !hasWiredOrUsbOutput(deviceTypes) && !hasBluetoothOutput(deviceTypes);
    }

    public static boolean hasBluetoothOutput(AudioDeviceInfo[] devices) {
        if (devices == null) return false;
        for (AudioDeviceInfo device : devices) {
            if (device != null && isBluetooth(device.getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasWiredOrUsbOutput(AudioDeviceInfo[] devices) {
        if (devices == null) return false;
        for (AudioDeviceInfo device : devices) {
            if (device != null && isWiredOrUsb(device.getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBuiltinSpeakerActive(AudioDeviceInfo[] devices) {
        return !hasWiredOrUsbOutput(devices) && !hasBluetoothOutput(devices);
    }

    public static boolean hasBluetoothOutput(AudioManager manager) {
        if (manager == null) return false;
        try {
            return hasBluetoothOutput(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean hasWiredOrUsbOutput(AudioManager manager) {
        if (manager == null) return false;
        try {
            return hasWiredOrUsbOutput(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isBuiltinSpeakerActive(AudioManager manager) {
        if (manager == null) return true;
        try {
            return isBuiltinSpeakerActive(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS));
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    /**
     * Determines whether a Bluetooth latency warning should be shown.
     * Returns true only when Bluetooth is active and no low-latency wired/USB device is active.
     */
    public static boolean shouldShowBluetoothWarning(boolean hasBluetooth, boolean hasWiredOrUsb) {
        return hasBluetooth && !hasWiredOrUsb;
    }
}
