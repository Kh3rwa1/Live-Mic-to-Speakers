# Design proposal: optional background playback

Status: **proposal only, not implemented.** This document describes how to keep saved-recording
playback alive when the user leaves the player screen. It intentionally changes nothing today:
playback still stops when the player screen is not visible.

## Problem

Playback lives in `MusicActivity` and the local preview; leaving the screen pauses and unbinds the
player. That is correct for a foreground-only utility, but users reasonably expect a saved track to
keep playing when they switch apps or lock the screen. Microphone capture must stay foreground-only
for privacy and platform reasons — this proposal is about **playback of already-saved recordings
only**.

## Goals

- Playback of a saved recording continues with the screen off / app backgrounded.
- Media controls appear on the lock screen, in the notification shade and on Bluetooth/headset
  buttons, driven by the platform, not a custom notification hack.
- A single user toggle in Settings: **Keep playing in the background** (default off).
- No behaviour change to live microphone monitoring, hold-to-speak, or recording.
- The existing recording/consent/ads constraints continue to hold.

## Non-goals

- Background microphone capture or monitoring.
- A second playback engine; the same `MediaPlayer`/MediaStore content-URI path is reused.
- Automatic resume of a previous session on app launch.

## Proposed architecture

1. **`MediaSessionService`** (androidx.media3 or the platform `MediaSession`).
   - One service, e.g. `media.PlaybackService`, declared with
     `android:foregroundServiceType="mediaPlayback"` and `android:exported="false"`.
   - Owns a `MediaSession` and a single `Player` instance. The Activity becomes a *controller*
     only: it renders transport state from the session and sends commands, instead of owning the
     player.
   - `MediaSession` is created with a `PlaybackState` + `MediaMetadata` per track; the framework
     then derives notification and lock-screen controls automatically.

2. **Foreground service + notification.**
   - Starting playback promotes the service with `startForeground()` using a `MediaStyle`
     notification (`androidx.media.app.NotificationCompat.MediaStyle`) bound to the session token.
   - Notification actions: play/pause, next/previous within the current list, stop.
   - `POST_NOTIFICATIONS` (API 33+) is requested only when the user enables the toggle and starts
     background playback; denial degrades to foreground-only playback with no crash.

3. **Lifecycle and ownership.**
   - The service is started when playback begins **and** the toggle is on; otherwise the existing
     bound-foreground model is kept unchanged.
   - On `onTaskRemoved`, playback stops unless the toggle is on and playback is genuinely in
     progress (mirrors the platform's expected `mediaPlayback` behaviour).
   - Audio focus and the noisy-route (`ACTION_AUDIO_BECOMING_NOISY`) handling currently in
     `PreviewPlayer` move into the service so headphone unplug still pauses playback.
   - Configuration changes no longer rebuild the player: state lives with the service/session.

4. **Settings and persistence.**
   - New boolean preference, e.g. `playbackBackground`, surfaced as a labelled switch with a
     content description and a short explanation of battery use.
   - Off by default; turning it off while playing continues the current track until it finishes,
     then does not restart.

5. **Manifest and policy.**
   - Add `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (API 34+) permissions and the
     `mediaPlayback` service type. These are only declared if the feature ships.
   - Update the Data Safety disclosure: background media playback is a new data-use surface.
   - Document a Play Store justification ("audio playback continues while the app is not visible").

## Migration path

- Phase A: introduce the service behind the toggle, leaving the Activity path as the default.
- Phase B: move the player, audio focus and route guard into the service.
- Phase C: delete the Activity-owned player once tests cover the service path and the toggle is on.

## Testing plan

- JVM: preference gating, notification-action intent mapping, reconnect-on-startup state machine.
- Instrumentation: start playback, background the app, assert the session reports
  `PLAYING` and audio focus is held; unplug route and assert pause; toggle off and assert the
  foreground service stops.
- Manual: 10-minute playback with screen off, Bluetooth output, and battery measurement.

## Risks

- Background playback increases battery use and Play policy scrutiny.
- A service-owned player must not leak into the microphone/recording paths; strict ownership
  boundaries and generation tickets are required, matching the existing
  `AudioSessionRunner`/`RecordingController` discipline.
- Do **not** ship this without the toggle, the disclosure update, and real-device testing.
