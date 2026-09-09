# Production readiness

This document describes safeguards and release gates; it does not assign a measured 10/10 rating or certify a build for production.

## Current baseline

- JDK 17, Gradle 9.7.1, Android Gradle Plugin 9.4.0.
- compileSdk/targetSdk 37 with Android 7 / API 24 as the minimum.
- Java 17 source and target compatibility across app and ads modules.
- Release builds use the optimized default R8 configuration, project rules, and resource shrinking.
- Application identity remains `com.word.way`; backups remain disabled and internal components remain non-exported.
- Cleartext traffic is disabled. The app manifest does not directly request notification or media-playback foreground-service permissions for its bound player. Review the final merged manifest because third-party SDKs can contribute generic permissions.

## Implemented safeguards

- Serialized live-audio sessions with cancellation, non-blocking PCM transfer, focus handling, route interruption, and deterministic cleanup.
- Serialized recording preparation/finalization, runtime-error recovery, pending-file publication, invalid-capture removal, and dead-screen callback suppression.
- Focus-aware preview and saved-track playback with headphone-disconnect handling and asynchronous preparation.
- App-specific recording storage, off-main library scans, content-URI playback, stable filtering identity, and read-only sharing through narrow provider roots.
- Consent-aware ad initialization and requests, screen-owned ad cleanup, privacy choices, and guarded fullscreen completion.
- Production configuration gates for signing, versioning, and non-test ad identifiers.
- JVM, lint, helper, dependency-policy, native-alignment, and API 24/34/36/37 instrumentation checks, including 200% font-scale runs on APIs 36 and 37.

## Building and verifying

```bash
bash ./gradlew :app:assembleDebug :app:assembleRelease
bash ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :ads:testDebugUnitTest :ads:testReleaseUnitTest
bash ./gradlew :app:lintDebug :app:lintRelease :ads:lintDebug
python3 tools/check_quality_contract.py
python3 -m unittest discover -s tools/tests -v
bash ./gradlew :app:writeDependencyInventory
python3 tools/check_dependency_policy.py app/build/reports/runtime-dependencies.json
```

Inspect checks and uploaded reports for the exact commit being released. A workflow definition is not a passing result. R8 build success also does not replace runtime testing of the signed release artifact.

## Configuring a real release

Do not commit secrets, paste signing data into public discussions, or put passwords directly on a shared command line. Use environment variables or private Gradle user-home properties.

Required settings:

- `LIVE_MIC_ADMOB_APP_ID`
- `LIVE_MIC_AD_BANNER`
- `LIVE_MIC_AD_NATIVE`
- `LIVE_MIC_AD_NATIVE_ADVANCE` (optional)
- `LIVE_MIC_AD_INTERSTITIAL`
- `LIVE_MIC_AD_OPEN`
- `LIVE_MIC_AD_REWARDED`
- `LIVE_MIC_KEYSTORE`
- `LIVE_MIC_STORE_PASSWORD`
- `LIVE_MIC_KEY_ALIAS`
- `LIVE_MIC_KEY_PASSWORD`
- `LIVE_MIC_VERSION_CODE`
- `LIVE_MIC_VERSION_NAME`

Run:

```bash
bash ./gradlew :app:verifyProductionRelease -PproductionRelease=true
bash ./gradlew :app:bundleRelease -PproductionRelease=true
```

Production ad resources are generated only for the opted-in release variant. Validation never prints supplied credential values. It does not certify live ads, mediation, regional consent, privacy disclosures, or store compliance.

## Required before publishing

- Obtain an all-green run for the final commit, including API 24 and the API 37 16 KiB image.
- Install and test the actual signed, R8-processed release artifact.
- Measure latency, underruns, battery use, feedback risk, and interruption behavior on representative physical devices and wired/USB/Bluetooth routes.
- Inspect screenshots and manually test TalkBack, keyboard focus, 200% text, contrast, RTL, cutouts, gesture/three-button navigation, and resizing.
- Verify real UMP, AdMob, mediation, privacy policy, and Data Safety behavior in applicable regions.
- Verify Play-generated APKs and packaged native dependencies on a 16 KiB device.
- Complete the evidence fields in `RELEASE_CHECKLIST.md`.

No source-only change can complete these physical-device, account-console, legal, signing, or store-owner decisions automatically.
