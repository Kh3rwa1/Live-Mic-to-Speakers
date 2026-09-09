# Architecture

Live Mic to Speakers is a foreground Android audio utility. Its most important design rule is that platform audio resources have one clear owner and never outlive the screen or operation that requested them.

## Modules

- `app`: activities, audio capture and recording, local-library playback, storage, and sharing.
- `ads`: consent-aware advertising integration and ad lifecycle management.
- `tools`: deterministic quality, dependency, native-alignment, SDK, and CI diagnostics.

## Live microphone path

`LiveMicrophoneActivity` expresses user intent but does not own `AudioRecord` or `AudioTrack` directly. `AudioSessionRunner` serializes sessions on one worker and uses generation tokens to cancel stale starts. `AndroidAudioSession` owns audio focus, route observation, capture, playback, optional echo cancellation/noise suppression, metering, and cleanup for one session.

Invariants:

1. Opening the screen does not allocate the microphone.
2. Only one session can own capture and output at a time.
3. A stop or lifecycle change invalidates an in-progress start.
4. Reads and writes are non-blocking; UI callbacks are posted separately.
5. Focus loss or a material output-route change stops the session.
6. Every exit path releases effects, input, output, route observation, and focus.

## Recording path

`RecordingController` is a single-worker state machine: `IDLE`, `STARTING`, `RECORDING`, `STOPPING`, and `CLOSED`. `RecordingSession` is the Android `MediaRecorder` adapter.

Recordings are first written under a unique `.pending` filename. Empty, cancelled, failed, or sub-500 ms captures are discarded. A successfully finalized file is renamed to `.m4a` before the library is notified. Publication never overwrites an existing recording; a failed rename keeps the pending file for recovery.

## Playback path

Short previews use `PreviewPlayer`. Saved-track playback uses the bound `MediaPlaybackService` and immutable `PlaybackState` values. Playback requests audio focus, pauses on focus loss or headphone disconnection, prepares asynchronously, and releases resources when the owner unbinds. It is intentionally foreground-screen playback, not background playback.

## Storage and sharing

New recordings remain in app-specific recording folders. Library queries and metadata scans run off the UI thread. Playback uses content URIs where available. Sharing grants temporary read-only access through a non-exported `FileProvider` restricted to the recording folders; unrelated private, cache, and external files are outside the provider roots.

## Ads and privacy

The ads module owns consent, SDK initialization, ad eligibility, and ad-object cleanup. Ad requests must not begin merely because the application process started. Production identifiers and signing data are injected only for an explicit production build and are never committed.

## Quality boundaries

Automated checks cover lifecycle ownership, failure cleanup, file publication, sharing boundaries, playback state, installed-manifest policy, layouts, large text, dependency policy, and native packaging. They do not prove acoustic safety, real-device latency, Bluetooth quality, battery consumption, regional consent behavior, or store approval. Those remain release gates in `RELEASE_CHECKLIST.md`.

## Evolution rules

- Keep platform audio calls behind worker-owned adapters.
- Prefer immutable state and typed failures over Activity-owned booleans and generic messages.
- Do not add background capture or playback without a correctly typed foreground service, a persistent notification, and dedicated policy tests.
- New user-visible text belongs in string resources and must be checked at 200% font scale.
- Gradually move screen orchestration into lifecycle-aware controllers/ViewModels; avoid a risky all-at-once rewrite of the proven audio core.
