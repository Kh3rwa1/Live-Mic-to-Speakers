# Physical Device Test Plan & Validation Guide

This document outlines the step-by-step test procedures, diagnostic tools, and validation tables for QA and engineers conducting real-device verification on physical Android hardware across Android 7.0 (API 24) through Android 15/16/17 (API 34–37).

---

## 1. Diagnostic Tools & Live Pipeline Inspection

### 1.1 In-App Audio Diagnostics (Debug Builds)
In debuggable builds (`assembleDebug`), an internal diagnostics panel is accessible directly on the **Live Microphone** screen:
1. Open **Live Microphone** and tap **Start**.
2. **Long-press** the **Output Route** status label at the bottom of the screen (e.g. `Output: Speaker` or `Output: Headset`).
3. An **Audio Diagnostics** dialog appears displaying live pipeline telemetry:
   - **Sample Rate**: Configured sampling rate (e.g., 48000 Hz or 44100 Hz) vs native device rate.
   - **Buffer Size**: Frame count and bytes for input (`AudioRecord`) and output (`AudioTrack`).
   - **Underrun Count**: Buffer underrun tally reported by `AudioTrack.getUnderrunCount()` (API 24+).
   - **Active Route**: Exact product name of the sink device reported by `AudioDeviceInfo.getProductName()`.
   - **Low Latency Path**: Indicates whether `PERFORMANCE_MODE_LOW_LATENCY` is active (API 26+).
   - **Monitoring Gain**: Current gain percentage applied by `LiveGainProcessor`.
4. Tap **Copy** to copy the diagnostic block to the device clipboard for bug reporting.

### 1.2 Logcat Monitoring Commands
To inspect pipeline events and underruns in real time over ADB, run:

```bash
# Monitor audio session lifecycle, sample rate, buffer bursts, and underruns:
adb logcat -s AndroidAudioSession:D

# Expected output on session start:
# D/AndroidAudioSession: started rate=48000 nativeRate=48000 framesPerBuffer=96 recordBuffer=384 trackBuffer=768 lowLatency=true
# D/AndroidAudioSession: underruns=0 rate=48000 framesPerBuffer=96 trackBuffer=768

# Monitor audio route changes, noisy output broadcasts, and device disconnections:
adb logcat -s AudioRouteGuard:D AudioSessionRunner:D

# Monitor recording recovery and quarantine sweeps:
adb logcat -s RecordingRecovery:D RecordingFiles:D
```

---

## 2. Latency Measurement & Empirical Benchmarking

Follow these exact steps to benchmark and measure live mic-to-speaker latency across physical devices:

### 2.1 Benchmark Steps
1. **OboeTester Hardware Baseline**:
   - Install [OboeTester](https://github.com/google/oboe/tree/main/apps/OboeTester) on the test device.
   - Navigate to **Round Trip Latency** test.
   - Run the test using the device's native sample rate and low-latency MMAP or AAudio path.
   - Record the measured latency in milliseconds. This represents the physical theoretical minimum for the device hardware and acoustic path.
2. **App Acoustic Clap Test**:
   - In a quiet room, place the test device running Live Mic to Speaker next to a secondary recording device (e.g. laptop or second phone running an audio recorder at 48 kHz).
   - Start live monitoring on the test device with monitoring gain at 80%.
   - Perform a sharp acoustic clap ~10 cm from the microphone. Both the direct physical clap and the amplified speaker output will be picked up by the secondary recorder.
   - Import the recording into an audio editor (e.g., Audacity).
   - Zoom in to waveform level and measure the time delta between the direct clap transient peak and the amplified output transient peak.
   - Perform 5 claps and record the **median** value in ms.
3. **AudioFlinger Fast-Track ("F") Audit**:
   - While monitoring is active, execute:
     ```bash
     adb shell dumpsys media.audio_flinger
     ```
   - In the command output, locate the active mixer thread (e.g., `FastMixer` or `MixerThread`) and search for the app's PID (`adb shell pidof com.word.way`).
   - Inspect the `Tracks` or `FastTracks` column. Verify that the track flags contain **`F`** (Fast track enabled). If absent or showing `None`, the HAL or framework has denied the fast mixer path.
4. **Telemetry Snapshot & Copy**:
   - Long-press the output route label on the Live Microphone screen to open the **Audio Diagnostics** dialog.
   - Tap **Copy** to capture the full telemetry snapshot.
5. **Session Duration & Drift Checks**:
   - Record diagnostics and clap test at **10 seconds** into the session.
   - Let the session run uninterrupted for **5 minutes**, then record diagnostics and clap test again.
   - Check whether the queue depth has drifted and whether any underruns accumulated.
6. **Route Matrix**:
   - Repeat measurements for:
     - Built-in speaker
     - 3.5mm wired headphones / headset
     - Bluetooth audio (A2DP / BLE)

### 2.2 Latency Benchmark Results Table

| Device Model | Android Version | Build SHA | Route | OboeTester RTL (ms) | App Clap Median (ms) | Fast Track ("F") (Y/N) | Underruns (5 min) | Queue Depth (10s / 5min) | Audio Config (Source, AEC, NS) | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| *e.g. Pixel 8* | *Android 14 (API 34)* | | Built-in Speaker | | | | | | | |
| *e.g. Pixel 8* | *Android 14 (API 34)* | | Wired Headset | | | | | | | |
| *e.g. Pixel 8* | *Android 14 (API 34)* | | Bluetooth (A2DP) | | | | | | | |
| *e.g. Galaxy A34* | *Android 14 (API 34)* | | Built-in Speaker | | | | | | | |
| *e.g. Galaxy A34* | *Android 14 (API 34)* | | Wired Headset | | | | | | | |
| *e.g. Galaxy A34* | *Android 14 (API 34)* | | Bluetooth (A2DP) | | | | | | | |

---

## 3. Step-by-Step Functional Test Procedures

### Test 1: Live Mic — Internal Built-in Speaker
*Validates: Speaker feedback safety dialog, startup gain ramp, soft limiter, and acoustic feedback detection.*

1. Disconnect all external audio accessories (headphones, USB-C adapters, Bluetooth).
2. Launch the app and tap **Live Microphone**.
3. Tap **Start**.
   - **Expected**: Exactly ONE dialog appears ("Prevent loud feedback").
4. Tap **Start** in the dialog without checking "Don't show again".
   - **Expected**: Audio begins smoothly without pops or clicks (300 ms linear ramp).
   - **Expected**: Spoken audio is amplified cleanly without harsh digital distortion (soft limiter active).
5. Move the microphone close to the speaker at moderate volume to induce feedback howl:
   - **Expected**: Within ~1 second of sustained howl, audio automatically mutes completely.
   - **Expected**: Session stops, and UI displays: *"Feedback detected: the microphone heard the speaker. Use headphones or lower the volume, then tap Start."*
6. Tap **Start** again:
   - **Expected**: Safety dialog appears again (since "Don't show again" was not checked).
7. Check "Don't show again" and tap **Start**:
   - **Expected**: Mic starts immediately.
8. Stop and tap **Start** once more:
   - **Expected**: Starts directly without prompting the safety dialog.

---

### Test 2: Live Mic — 3.5mm Wired Headphones
*Validates: Low-latency playback, round-trip stability, safety dialog bypass, and disconnect shutdown.*

1. Connect standard 3.5mm wired headphones or headset.
2. Open **Live Microphone**.
3. Tap **Start**:
   - **Expected**: Bypasses the speaker safety dialog and starts immediately (headphones detected).
   - **Expected**: Route displays "Headset" or "Headphones".
4. Speak into the microphone and evaluate latency:
   - **Expected**: Roundtrip latency is minimal with no perceivable echo delay.
   - **Expected**: Zero audible underrun clicks or stutter during continuous speech.
5. While audio is streaming, unplug the 3.5mm headphone jack:
   - **Expected**: Monitoring stops immediately.
   - **Expected**: Sound does NOT spill over to the built-in speaker.
   - **Expected**: UI displays: *"Microphone stopped because the audio output or audio focus changed."*

---

### Test 3: Live Mic — USB-C Audio Adapter / Digital Headset
*Validates: USB digital sink routing, hotplug disconnect, and low-latency performance.*

1. Connect a USB-C audio dongle or digital USB headset.
2. Open **Live Microphone** and tap **Start**:
   - **Expected**: Starts immediately without speaker safety dialog.
   - **Expected**: Output route identifies the USB peripheral.
3. Verify latency and audio clarity during speech.
4. Check underruns via long-press diagnostics or logcat: `adb logcat -s AndroidAudioSession:D`.
   - **Expected**: Underrun count remains stable (0 or near 0).
5. Disconnect the USB-C adapter during playback:
   - **Expected**: Session halts immediately with route interruption message. Audio does NOT switch to speaker.

---

### Test 4: Live Mic — Bluetooth Audio (SCO vs A2DP)
*Validates: Bluetooth permission flow on API 31+, route identification, and latency behavior.*

1. Pair and connect a Bluetooth headset/speaker.
2. Open **Live Microphone**.
3. Tap **Start**:
   - **Expected**: Audio session starts first without preliminary Bluetooth permission dialogs.
   - **Expected (API 31+)**: If `BLUETOOTH_CONNECT` permission has not been granted, a rationale dialog appears asking for Bluetooth permission to display device names.
   - **Expected**: If permission is denied, output route cleanly falls back to generic system output without crashing.
   - **Expected**: Subsequent starts do not repeatedly prompt for Bluetooth permission.
4. Verify audio output over Bluetooth:
   - Note: Bluetooth A2DP inherently has higher latency (~100–200ms) than wired routes due to Bluetooth codec buffer frames.

---

### Test 4b: Audio Input Profiles & Low-Latency Source Selection
*Validates: Input profile selection in Settings, persistent preferences, and active pipeline hardware effect configuration.*

1. Open **Settings** screen.
2. Locate the **Audio Input Profile** card:
   - **Low latency (Recommended)**: Raw audio without filter delays.
   - **Balanced**: Standard mic with noise suppression.
   - **Noisy room / Call-style**: Voice communication with echo cancellation and noise suppression.
3. Select **Low latency**:
   - Verify Toast: *"Audio profile updated. Changes apply to the next live session."*
4. Return to **Live Microphone**, tap **Start**, and long-press the output route label to view Diagnostics:
   - Verify source is `VOICE_PERFORMANCE` (API 29+) or `VOICE_RECOGNITION` (API 24–28).
   - Verify AEC: `false`, NS: `false`.
5. Return to **Settings**, select **Noisy room / Call-style**, and restart Live Microphone:
   - Verify source is `VOICE_COMMUNICATION (7)`.
   - Verify AEC: `true` (if available), NS: `true` (if available).
6. Verify profile setting persists across app restart.

---

### Test 4c: Bluetooth Honesty Notice Verification
*Validates: Non-blocking warning on wireless Bluetooth routes and proper suppression on wired/USB routes.*

1. Connect a Bluetooth audio device (A2DP or SCO).
2. Open **Live Microphone** and tap **Start**:
   - **Expected**: A non-blocking notice card appears below the route label: *"Bluetooth adds noticeable delay. Wired headphones give the lowest latency."*
   - **Expected**: The notice does not block interaction, monitoring gain slider, or stopping/starting.
3. While live monitoring is active, plug in 3.5mm wired headphones or a USB-C headset:
   - **Expected**: Audio reroutes to wired headphones and monitoring halts safely on route change.
4. Tap **Start** with wired headphones connected:
   - **Expected**: The Bluetooth notice is GONE (`visibility = GONE`).
5. Disconnect wired headphones so output falls back to Bluetooth:
   - **Expected**: Bluetooth notice reappears when Bluetooth output is active.

---

### Test 5: Hold to Speak
*Validates: Push-to-talk press, sub-500ms tap discard, normal hold save, and slide-to-cancel.*

1. Open **Hold to Speak**.
2. **Sub-500ms Tap**: Tap and immediately release the red button (< 0.5s):
   - **Expected**: Button pulses briefly; no recording is saved; no abandoned file remains in storage.
3. **Normal Hold**: Press and hold the button for 3 seconds while speaking, then release:
   - **Expected**: Red recording pulse animation runs during hold.
   - **Expected**: Releasing saves the clip cleanly as an `.m4a` file in readable timestamp format.
   - **Expected**: Toast confirms save; "Play last" button activates.
4. **Slide to Cancel**: Press and hold, then drag finger > 50dp away from the button before releasing:
   - **Expected**: Status displays cancellation; recording is discarded; no file is saved.

---

### Test 6: Audio Recorder
*Validates: Continuous AAC recording, live level meter, readable naming, and finalization.*

1. Open **Record Audio**.
2. Tap **Record**:
   - **Expected**: Recording starts; timer increments; level meter animates with voice input.
3. Record for 60 seconds.
4. Tap **Stop**:
   - **Expected**: Clip is published to storage with format `Record Audio YYYY-MM-DD HH-mm-ss.m4a`.
   - **Expected**: Temporary `.pending` file is cleanly removed.

---

### Test 7: Audio Library & Storage Management
*Validates: Playback, rename, delete with confirmation, FileProvider sharing, and unrecovered quarantine.*

1. Open **Audio Library**.
2. Tap the newly created recording:
   - **Expected**: Opens player screen; waveform / progress bar plays audio smoothly.
3. Return to library, long-press a recording, and select **Rename**:
   - **Expected**: Enter a new name; file renames cleanly without duplicate extensions.
4. Long-press and select **Share recording**:
   - **Expected**: Android system share sheet appears using secure `content://` URI (`FileProvider`); sharing to messaging/drive app succeeds.
5. Long-press and select **Delete**:
   - **Expected**: Confirmation dialog appears. Canceling preserves the file. Confirming permanently deletes the file and refreshes the list.
6. **Unrecovered Quarantine Validation**:
   - Push a non-empty corrupted file to app storage:
     `adb shell "echo 'corrupt audio' > /sdcard/Android/data/com.word.way/files/Recording/corrupt.pending"`
   - Open Audio Library:
     - **Expected**: Recovery quarantine moves it to `corrupt.unrecovered` (it is NOT deleted).
     - **Expected**: An accessible banner appears at the top of the library indicating unrecovered recordings exist.
     - **Expected**: User can delete permanently or share via the share sheet.

---

### Test 8: User Ad Preference Persistence
*Validates: User toggle survives app kills, restarts, and rotation without hardcoded override.*

1. Open **Settings**.
2. Toggle the **Show Ads** switch to **OFF**.
3. Force stop the app:
   `adb shell am force-stop com.word.way`
4. Re-launch the app:
   - **Expected**: No banner ads appear on Main, Live Mic, or Settings screens.
   - **Expected**: In Settings, the **Show Ads** switch remains **OFF**.
5. Rotate device to landscape and back:
   - **Expected**: Ad preference remains OFF; no ad banners load.
6. Toggle **Show Ads** back to **ON**:
   - **Expected**: Banners load and display normally (subject to UMP consent).

---

## 3. Physical Device Validation Results Table

Please complete the table below for each physical test device:

| Test ID | Critical Path | Device Model | Android Version | Audio Route | Result (PASS / FAIL) | Measured Latency / Notes | Tester Name / Date |
|---|---|---|---|---|---|---|---|
| **1.1** | Live Mic: Speaker Ramp & Limiter | | | Built-in Speaker | | | |
| **1.2** | Live Mic: Feedback Auto-Mute | | | Built-in Speaker | | | |
| **1.3** | Live Mic: Don't Show Again | | | Built-in Speaker | | | |
| **2.1** | Live Mic: 3.5mm Latency | | | Wired Headset | | | |
| **2.2** | Live Mic: 3.5mm Disconnect Shutdown | | | Wired Headset | | | |
| **3.1** | Live Mic: USB-C Audio Latency | | | USB-C Adapter | | | |
| **3.2** | Live Mic: USB-C Disconnect Shutdown | | | USB-C Adapter | | | |
| **4.1** | Live Mic: Bluetooth Connect Flow | | | Bluetooth Headset | | | |
| **4.2** | Live Mic: Bluetooth Disconnect | | | Bluetooth Headset | | | |
| **5.1** | Hold to Speak: Sub-500ms Discard | | | Internal Mic | | | |
| **5.2** | Hold to Speak: Normal Save | | | Internal Mic | | | |
| **5.3** | Hold to Speak: Slide to Cancel | | | Internal Mic | | | |
| **6.1** | Audio Recorder: 60s AAC .m4a Save | | | Internal Mic | | | |
| **7.1** | Library: Playback & Scrubbing | | | Built-in Speaker | | | |
| **7.2** | Library: Rename & Delete Dialog | | | N/A | | | |
| **7.3** | Library: FileProvider Share Sheet | | | N/A | | | |
| **7.4** | Library: Unrecovered Quarantine Banner | | | N/A | | | |
| **8.1** | Settings: Ad Preference Persistence | | | N/A | | | |
| **8.2** | Screen Rotation: Layout Adaptation | | | N/A | | | |

---

## 4. Acceptance Criteria & Sign-Off Checklist

Before approving this release for production:
- [ ] No audio feedback howl persists longer than 1 second when microphone is held to the speaker.
- [ ] No crash or audio leak occurs when disconnecting headphones/adapters during live monitoring.
- [ ] Underrun count on live monitoring remains under 5 during a 5-minute continuous test.
- [ ] All in-flight recordings survive app interruptions or are quarantined as `.unrecovered` (no audio lost).
- [ ] Release APK is signed with the official release key (verified non-debug via `:app:verifyProductionRelease`).
- [ ] No test device IDs are packaged in the release build.
- [ ] User ad preferences are strictly honored across app restarts.
