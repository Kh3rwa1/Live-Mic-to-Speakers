# Core-tool quality and production readiness

This change improves the app; it does not assign a measured 10/10 rating or certify production readiness.

## Implemented

- API 36 target/compile SDK with AGP 8.10.1 and Gradle 8.11.1 (JDK 17).
- Shared system-bar, cutout and IME safe-area handling with non-accumulating insets. Existing screen backgrounds determine system-bar icon contrast.
- Consistent, scrollable live-mic, recording, hold-to-record, playback and settings screens. Controls have visible focus, named actions, at least 48dp targets, scalable sp text and resource-based labels.
- Direct ad-privacy choices in Settings. The existing consent gate, ad identifiers, policy URL and recording paths are preserved.
- Existing worker ownership/cancellation/finalization and microphone interruption rules are preserved. Foreground-only behavior is explained in the interface.
- Production configuration checks reject test ad identifiers, missing signing information and unspecified versioning. A release bundle cannot be built in demo mode accidentally.

## Build and verification

`bash ./gradlew :app:assembleDebug :app:assembleRelease` still builds demo-configuration artifacts for CI; release remains unsigned unless production is explicitly configured.

`python3 tools/check_quality_contract.py` checks the migrated layouts and labels.

CI runs existing JVM and instrumented regressions plus native control-reachability, recreation, safe-inset and screenshot checks on APIs 24, 34 and 36. API 36 also runs at 200% font scale. Screenshots are uploaded with each device-test report. Read the actual checks on the final commit; configuration is not a passing result.

This environment could not download an Android toolchain. Local validation is limited to XML/source contracts and Java syntax; native build/test evidence must come from GitHub CI. Screenshots require human visual inspection, including clipping, contrast, RTL and screen-reader use. Automated bounds checks are not a substitute.

## Configuring a real release

Do not paste signing secrets into chat, commit them, or put passwords on the command line. Use environment variables or your private Gradle user-home properties. Keep the existing application ID and signing key if this app is already distributed.

Required setting names:

- LIVE_MIC_ADMOB_APP_ID
- LIVE_MIC_AD_BANNER
- LIVE_MIC_AD_NATIVE
- LIVE_MIC_AD_INTERSTITIAL
- LIVE_MIC_AD_OPEN
- LIVE_MIC_AD_REWARDED
- LIVE_MIC_KEYSTORE (absolute path recommended)
- LIVE_MIC_STORE_PASSWORD
- LIVE_MIC_KEY_ALIAS
- LIVE_MIC_KEY_PASSWORD
- LIVE_MIC_VERSION_CODE (positive integer; must increase over the last published version)
- LIVE_MIC_VERSION_NAME

Run `bash ./gradlew :app:verifyProductionRelease -PproductionRelease=true`, then `bash ./gradlew :app:bundleRelease -PproductionRelease=true`.

Production ad strings are generated as a release-only resource overlay; the committed demo IDs remain unchanged for debug. Validation never prints supplied credential values. Configuration validation does not certify live ads, mediation, regional consent or store compliance.

## Still required before publishing

Complete RELEASE_CHECKLIST.md against the actual signed artifact. In particular:

- Measure end-to-end audio latency, underruns, battery use and disconnect/focus behavior on representative physical phones and output routes, at safe speaker volume.
- Inspect all native screenshots and test TalkBack, keyboard controls, large text, cutouts, gesture/three-button navigation, RTL and tablet/window resizing. Library/history layouts outside the migrated core screens still need a full design/localization audit.
- Verify real UMP/AdMob/mediation traffic, privacy policy and Data Safety disclosures.
- Check packaged native dependencies for current 16 KB page-size requirements and other current store requirements; updating targetSdk alone does not establish compliance.
- No production publishing, merging, signing-key rotation or alteration of existing recordings is performed by this change.

References checked on 2026-09-07:

- https://developer.android.com/google/play/requirements/target-sdk
- https://developer.android.com/build/releases/agp-8-10-0-release-notes
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/develop/ui/views/layout/edge-to-edge
