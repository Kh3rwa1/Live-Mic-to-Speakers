# Changelog

Notable user-visible and engineering changes are documented here. This project follows semantic versioning for tagged releases where practical.

## Unreleased

### Changed

- Production release builds use R8 code shrinking, optimization, obfuscation, and resource shrinking.
- Java source and target compatibility now match the required JDK 17 toolchain.
- Foreground-screen playback no longer declares unused notification or foreground-service permissions.
- Cleartext network traffic is disabled at the installed-manifest level.
- Device-test execution has a bounded command deadline so a stalled test produces diagnostics instead of consuming the entire CI job.

### Documentation

- Added architecture, contribution, security, and pull-request guidance.
- Updated production and release gates for the API 24/34/36/37 test matrix and Android 17 target.

## 1.1

- Added live microphone monitoring, hold-to-record announcements, saved recordings, local playback, sharing, lifecycle recovery, consent-aware ads, and expanded automated verification.
