# Changelog

Notable user-visible and engineering changes are documented here. This project follows semantic versioning for tagged releases where practical.

## Unreleased

### Added

- Added the MIT license for the repository.

### Changed

- Refined the pastel studio design across the home screen, microphone tools, recording flows, settings, player, and audio library.
- Replaced heavy clay-style surfaces with calmer border-first cards, restrained elevation, accessible dark accents, and consistent Poppins typography.
- Preserved 48dp-or-larger controls, scalable text, scrolling at large font sizes, explicit focus states, and non-color action labels.
- Made the microphone navigation test scroll-aware so the 200% font-scale matrix verifies a reachable action instead of assuming a fixed viewport.
- Production release builds use R8 code shrinking, optimization, obfuscation, and resource shrinking.
- App and ads modules now use Java 17 source and target compatibility, matching the required toolchain.
- The application manifest no longer directly declares notification or media-playback foreground-service permissions for foreground-screen playback. Third-party SDKs may still contribute generic permissions to the merged manifest, which must be reviewed on the final artifact.
- Cleartext network traffic is disabled at the installed-manifest level.
- Device-test execution has a bounded command deadline so a stalled test produces diagnostics instead of consuming the entire CI job.

### Documentation

- Added architecture, contribution, security, and pull-request guidance.
- Updated production and release gates for the API 24/34/36/37 test matrix and Android 17 target.

## 1.1

- Added live microphone monitoring, hold-to-record announcements, saved recordings, local playback, sharing, lifecycle recovery, consent-aware ads, and expanded automated verification.
