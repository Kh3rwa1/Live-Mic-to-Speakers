package com.word.way.audio;

import android.media.AudioDeviceInfo;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioRouteClassifierTest {

    @Test
    public void bluetoothTypesAreClassifiedCorrectly() {
        assertTrue(AudioRouteClassifier.isBluetooth(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertTrue(AudioRouteClassifier.isBluetooth(AudioDeviceInfo.TYPE_BLUETOOTH_SCO));
        assertFalse(AudioRouteClassifier.isBluetooth(AudioDeviceInfo.TYPE_WIRED_HEADSET));
        assertFalse(AudioRouteClassifier.isBluetooth(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertFalse(AudioRouteClassifier.isBluetooth(AudioDeviceInfo.TYPE_USB_DEVICE));
        assertFalse(AudioRouteClassifier.isBluetooth(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
    }

    @Test
    public void wiredAndUsbTypesAreClassifiedCorrectly() {
        assertTrue(AudioRouteClassifier.isWiredOrUsb(AudioDeviceInfo.TYPE_WIRED_HEADSET));
        assertTrue(AudioRouteClassifier.isWiredOrUsb(AudioDeviceInfo.TYPE_WIRED_HEADPHONES));
        assertTrue(AudioRouteClassifier.isWiredOrUsb(AudioDeviceInfo.TYPE_USB_DEVICE));
        assertTrue(AudioRouteClassifier.isWiredOrUsb(AudioDeviceInfo.TYPE_USB_HEADSET));
        assertFalse(AudioRouteClassifier.isWiredOrUsb(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP));
        assertFalse(AudioRouteClassifier.isWiredOrUsb(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER));
    }

    @Test
    public void shouldShowBluetoothWarningOnlyWhenBluetoothActiveWithoutWired() {
        assertTrue(AudioRouteClassifier.shouldShowBluetoothWarning(true, false));
        assertFalse(AudioRouteClassifier.shouldShowBluetoothWarning(true, true)); // wired takes priority
        assertFalse(AudioRouteClassifier.shouldShowBluetoothWarning(false, false));
        assertFalse(AudioRouteClassifier.shouldShowBluetoothWarning(false, true));
    }

    @Test
    public void builtinSpeakerActiveWhenNoExternalDevices() {
        assertTrue(AudioRouteClassifier.isBuiltinSpeakerActive(new int[]{AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}));
        assertTrue(AudioRouteClassifier.isBuiltinSpeakerActive(new int[0]));
        assertTrue(AudioRouteClassifier.isBuiltinSpeakerActive((int[]) null));
        assertFalse(AudioRouteClassifier.isBuiltinSpeakerActive(new int[]{AudioDeviceInfo.TYPE_WIRED_HEADPHONES}));
        assertFalse(AudioRouteClassifier.isBuiltinSpeakerActive(new int[]{AudioDeviceInfo.TYPE_BLUETOOTH_A2DP}));
        assertFalse(AudioRouteClassifier.isBuiltinSpeakerActive(new int[]{
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADSET
        }));
    }

    @Test
    public void hasBluetoothAndWiredDetection() {
        assertTrue(AudioRouteClassifier.hasBluetoothOutput(new int[]{AudioDeviceInfo.TYPE_BLUETOOTH_A2DP}));
        assertFalse(AudioRouteClassifier.hasBluetoothOutput(new int[]{AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}));
        assertTrue(AudioRouteClassifier.hasWiredOrUsbOutput(new int[]{AudioDeviceInfo.TYPE_USB_HEADSET}));
        assertFalse(AudioRouteClassifier.hasWiredOrUsbOutput(new int[]{AudioDeviceInfo.TYPE_BUILTIN_SPEAKER}));
    }
}
