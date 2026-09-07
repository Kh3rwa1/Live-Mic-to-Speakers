# Release gates — do not publish on source review alone

## Automated
- [ ] GitHub build, JVM tests and Android lint pass on the final commit.
- [ ] API 34 instrumentation checks pass on the final commit.
- [ ] Signed release build is installable; run tests against the release variant as well as debug.

## Real-device audio matrix
- [ ] Test representative Android 7/12/14 and current Android devices.
- [ ] Open and leave live mic without starting: no retained microphone resources.
- [ ] Rapidly start/stop/restart repeatedly: only one session, no stale UI, no crash.
- [ ] Deny microphone permission, grant it later, and revoke it in Settings.
- [ ] Use the phone speaker with Bluetooth permission denied.
- [ ] Test wired/USB/Bluetooth outputs; disconnect and reconnect during audio.
- [ ] Measure actual end-to-end latency for each output route; publish measured ranges, not "zero latency".
- [ ] Verify low-volume startup guidance; test feedback resistance cautiously, never at high volume.
- [ ] Test calls, audio-focus loss, screen lock, Home, Back, and process recreation.
- [ ] Play a saved file, pause, leave and return: retain position and paused state.
- [ ] Make short/cancelled recordings and simulate storage-full/finalization errors: no corrupt file represented as saved.
- [ ] Validate saved M4A files with another player; confirm export/share MIME types and file-provider paths.
- [ ] Test TalkBack start/stop, hold-to-record fallback, large text and small screens.

## Remaining product/security work
- [ ] Audit all ad entrypoints and add a region-appropriate consent flow before ad requests where required. Existing Mobile Ads initialization has NOT been replaced with a consent implementation by this hardening pass.
- [ ] Audit banner/native/interstitial resource cleanup and singleton Activity references throughout the ads module.
- [ ] Review the broad external FileProvider path and external-library sharing flows together before restricting paths.
- [ ] Move remaining saved-history/MediaStore queries and metadata scans off the UI thread; harden the legacy local-audio picker and preview focus handling.
- [ ] Check dependency resolution/security reports, application ID, versioning, privacy policy, Data Safety, and current Play target-SDK requirements. This branch intentionally retains targetSdk 34; it is NOT a claim of current store-submission compliance.
- [ ] Complete design/device QA, localization and accessibility audit beyond the changed controls.

Background playback/capture is intentionally out of scope. Implement a correctly typed foreground service, user-visible notification, and platform permission flow before advertising it.
