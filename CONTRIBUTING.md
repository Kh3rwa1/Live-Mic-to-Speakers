# Contributing

Thank you for improving Live Mic to Speakers. Audio, privacy, storage, and lifecycle changes can have user-visible safety consequences, so changes should be small, testable, and explicit about their limits.

## Development environment

- JDK 17
- Android SDK 37
- Gradle wrapper 9.7.1
- A dedicated API 24+ emulator or device for instrumentation tests

Use the checked-in wrapper; do not replace it with a system Gradle installation.

## Required checks

```bash
bash ./gradlew :app:assembleDebug :app:assembleRelease
bash ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :ads:testDebugUnitTest :ads:testReleaseUnitTest
bash ./gradlew :app:lintDebug :app:lintRelease :ads:lintDebug
python3 tools/check_quality_contract.py
python3 -m unittest discover -s tools/tests -v
bash ./gradlew :app:writeDependencyInventory
python3 tools/check_dependency_policy.py app/build/reports/runtime-dependencies.json
```

With a dedicated device or emulator:

```bash
bash ./gradlew :app:connectedDebugAndroidTest
```

## Change rules

- Never commit signing keys, passwords, production ad identifiers, user recordings, or local SDK paths.
- Keep live capture and recording resources worker-owned; Activity callbacks must not block while waiting for cleanup.
- Preserve cancellation checks before platform start calls and cleanup on every failure path.
- Use app-specific storage and content URIs. Do not broaden the recording provider to cache, arbitrary external storage, or all private files.
- New permissions require a concrete runtime use case, least-privilege analysis, and an installed-manifest regression test.
- New text must use resources, remain understandable without color alone, and fit at 200% font scale.
- Update `README.md`, `ARCHITECTURE.md`, and release documentation when behavior or supported versions change.

## Pull requests

Create a focused branch, explain the user problem and risk, and include the exact commands or CI jobs that validated the change. A configured workflow is not evidence that it passed: link the checks for the final commit.

For audio or UI changes, attach real-device notes or screenshots when possible. Do not describe source review, emulator success, or static checks as production certification.
