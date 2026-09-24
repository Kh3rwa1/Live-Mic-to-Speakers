package com.word.way.audio;

import android.media.AudioDeviceInfo;
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

    /**
     * Determines whether a Bluetooth latency warning should be shown.
     * Returns true only when Bluetooth is active and no low-latency wired/USB device is active.
     */
    public static boolean shouldShowBluetoothWarning(boolean hasBluetooth, boolean hasWiredOrUsb) {
        return hasBluetooth && !hasWiredOrUsb;
    }
}
