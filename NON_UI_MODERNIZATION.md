# Non-UI modernization

This engineering pass deliberately defers visual design. It does not change layouts, themes, production identity, signing credentials, ad identifiers or the store release. Updated libraries can affect rendering, so human visual review remains necessary.

## Toolchain and supported devices

- Compile/target Android 17 / API 37; retain Android 7 / API 24 minimum support.
- JDK 17, Android Gradle Plugin 9.4.0 and Gradle 9.7.1. The distribution SHA-256 is pinned to Gradle's published checksum; CI separately validates wrapper JARs.
- Centralized AndroidX, Material, Glide, Lottie, AndroidX Test, ads/mediation and consent versions live in `gradle/libs.versions.toml`.
- The ads baseline is Google Mobile Ads 25.4.0 with Meta mediation 6.22.0.0 and UMP 4.0.0. This retains the existing supported Mobile Ads API line; it is not a Next-Gen rewrite.
- Existing JUnit 4 tests, LicensesDialog and dimension libraries are retained deliberately. A successful upgrade is not a claim that every dependency uses its newest major API.

## Runtime improvements

- Removed LocalBroadcastManager, RxJava 2 and RxAndroid dependencies and their app consumers.
- A lifecycle-owned recording invalidation stream replaces global action strings. It changes only after a finalized file is successfully published; stopped/destroyed observers are not called.
- History still rescans on resume for external changes. Pausing cancels pending work and invalidates stale results.
- The bound playback service exposes immutable typed snapshots. Error identity prevents progress updates from repeatedly showing the same error.
- Playback uses modern audio-focus requests on API 26+, retains the API 24 fallback and pauses on noisy output changes. Leaving the screen pauses playback before asynchronous unbinding; prior track/position/intent restoration remains intact.
- Existing consent gating, foreground-only recording, finalized-file publication and narrowly scoped read-only sharing are preserved.

## Audio and recording improvements

- Live monitoring probes the native output sample rate/burst and requests the API 26+ low-latency `AudioTrack` path, with device-paced blocking reads replacing sleep polling. Route/focus handling and AEC/NS behavior are unchanged.
- A pure-Java gain stage (`LiveGainProcessor`) adds a 300 ms start-up linear ramp, a persisted user gain (0..1), and a smooth soft limiter (a linear section below the 0.8 FS knee, then an exponential curve towards the ceiling, output always < 32767) preventing digital clipping.
- Feedback detection analyzes pre-gain PCM blocks using zero-crossing tonality scoring combined with an energy threshold and leaky accumulator to reliably identify sustained acoustic howl (even when muted by user gain). Detection auto-mutes the buffer immediately and stops the session, surfacing clear user instructions.
- In-flight recordings are registered in a thread-safe `ActiveRecordings` registry across the process lifetime, preventing active recordings from being swept or destroyed.
- Finalized recordings use readable timestamp pending names and collision-suffixed publication. Stale pending file sweeps only delete empty (0-byte) abandoned files older than 24 hours. Interrupted non-empty recordings are quarantined as `.unrecovered` files rather than deleted, and can be reviewed, permanently deleted, or shared via the system share sheet.
- Single dialog flow on live microphone prevents overlapping Bluetooth and safety dialogs; Bluetooth permission is only requested after start on API 31+ when a Bluetooth device is connected. Speaker safety warning includes a persistent "Don't show again" option.
- User ad on/off preference is preserved across sessions and activity recreation without startup overwrite.
- Production signing gate rejects builds configured or signed with debug keystores. AdMob test device IDs are restricted strictly to debuggable builds.
- Refactored duration formatting to `TimeFormat` utility with JVM unit tests, and renamed splash navigation to `navigateHome()`.

## Regression and maintenance safeguards

- Unit checks cover playback time bounds, consistent playback intent, immutable state and error identity.
- Instrumentation covers worker-thread recording publication, stopped/destroyed owners, actual service preparation without autoplay, error delivery and resource release.
- Installed-manifest checks protect API 37 targeting, API 24 minimum support, disabled backups, application identity and private app components/provider.
- CI retains API 24/34/36 coverage and adds API 37, including 200% font scale on APIs 36 and 37. Existing keyboard/inset assertions and seven fresh screenshot requirements are not weakened.
- `writeDependencyInventory` records resolved debug/release runtime modules. The policy fails on removed legacy dependencies, unreviewed previews, dynamic versions, missing required SDKs or unsupported ads/consent baselines. The exact vendor-preview exception below is reported explicitly. This is not a full vulnerability scanner.
- CI actions are pinned to resolved commits. Checkouts do not persist credentials. Gradle uses basic GitHub caching, not a new commercial caching service or a published Build Scan.
- Dependabot is configured for weekly Gradle and action update PRs, with ads/mediation/consent grouped. There is no automatic merge. Updates still require compatibility review and passing CI.

## Reviewed transitive dependency policy

The [failure on commit 0bdb769](https://github.com/Kh3rwa1/Live-Mic-to-Speakers/pull/7#issuecomment-5577179845) identified three preview modules in each runtime configuration. They require two different remedies:

- `androidx.viewpager2:viewpager2` has a [stable 1.1.0 release](https://developer.android.com/jetpack/androidx/releases/viewpager2#1.1.0). A version-catalog constraint in the ads module upgrades the transitive `1.1.0-beta02` request for that module and its app consumers. The beta remains rejected by policy.
- The existing ads dependency set resolves `androidx.privacysandbox.ads:ads-adservices:1.0.0-beta05` and `androidx.privacysandbox.ads:ads-adservices-java:1.0.0-beta05`. The [AndroidX release page](https://developer.android.com/jetpack/androidx/releases/privacysandbox-ads) lists beta releases and no stable release. Retain this already-resolved pair rather than remove runtime classes, invent a stable version or force a different preview API onto the ads SDK.
- The exception accepts only those two exact coordinates and versions, together in the same configuration, with `com.google.android.gms:play-services-ads:25.4.0`. A missing sibling, mixed versions, SDK changes, unreviewed previews and dynamic versions fail. This is not a group-wide or beta-wide exemption. CI prints each retained preview explicitly.
- Re-review or remove the exception when updating the ads SDK or adopting a verified stable replacement. Regression tests cover both runtime configurations, version drift, incomplete pairs, SDK drift, unrelated previews and the stable ViewPager2 remedy. The normal build, lint, unit, device, production-rejection and native-alignment checks remain mandatory. This exception is not live-ads or privacy certification.

## Verify the exact revision

Use [PR #7](https://github.com/Kh3rwa1/Live-Mic-to-Speakers/pull/7) for this pass, not older PR results. Only a completed successful run validates its own commit.

```bash
python3 -m unittest discover -s tools/tests -v
python3 tools/check_quality_contract.py
bash ./gradlew :app:writeDependencyInventory
python3 tools/check_dependency_policy.py app/build/reports/runtime-dependencies.json
```

The Android workflow also runs app/ads unit tests, lint, debug/release builds, separate production rejection checks, native ELF/ZIP alignment and device tests. Reports, the resolved dependency inventory and native screenshots are uploaded as artifacts.

## Still needs owner or hardware sign-off

A source pass cannot establish a measured 10/10. Real-phone latency, feedback safety, battery use, route/focus behavior, live UMP/mediation/privacy disclosures, human accessibility/visual review and the actual production-signed/Play-generated artifact remain release requirements. Static 16 KiB packaging checks do not replace a 16 KiB runtime test. See `VALIDATION.md`, `PRODUCTION_READINESS.md` and `RELEASE_CHECKLIST.md`.
