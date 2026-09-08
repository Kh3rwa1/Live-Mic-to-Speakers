# Library usability and live-audio recovery

This focused quality pass addresses code-level gaps from the app review. It is not a measured 10/10 rating, a production/privacy certification, or permission to publish.

## Improvements

- Live monitoring now carries typed failure categories from permission, audio-focus, configuration, input and output failures to distinct, resource-based recovery messages. Existing cancellation, worker ownership, sample-rate fallback and buffer sizes are preserved; there is no speculative latency tuning or automatic restart.
- Device-library search is now actually present in its fragment. Queries survive screen recreation, and history/device libraries distinguish loading, unavailable, empty and no-match states with retry, permission-review and clear-search actions.
- Preview controls and the library use separate, independently scrollable panes rather than overlapping. Empty previews cannot start playback, and preview errors remain visible instead of relying on a transient toast.
- History and library rows use consistent typography, wrapping filenames, visible focus and named controls. Sharing is a visible action; the existing long-press option and read-only provider boundaries remain intact.
- Labels in the changed library/history surfaces are Android string/plural resources, and relative modification dates use Android's locale-aware formatter. This enables translation; it does not claim that translations have been supplied for every locale.
- History deduplication uses a set rather than a quadratic list scan. Directory/MediaStore failures are not silently represented as a successful empty library, and stale/cancelled loads cannot overwrite the current screen.

## Validation

Offline checks performed during authoring:

- Compiled the new platform-independent production classes and checks with Java 8 compatibility.
- Ran live-error category/wrapper/cancellation/cyclic-cause checks 1,000 times.
- Ran the library-state checks over every valid total/visible-count combination through 100 files and invalid-count cases.
- Passed the five new Python source-contract tests, including negative fixtures.
- Parsed the changed/new Java and XML sources. Parsing does not perform Android type checking.

Commands:

```sh
python3 tools/check_library_contract.py
python3 -m unittest discover -s tools/tests -v
bash ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
bash ./gradlew :app:connectedDebugAndroidTest
```

New Android checks exercise all live-error UI mappings without starting capture, history search/clear/recreation, visible named 48dp sharing controls, library search restoration and non-overlapping preview layout in LTR/RTL. They run in the existing API/font-scale matrix; no previous test, release gate, permission or dependency policy is removed. Additional native screenshots (`history-library`, `library-preview-layout`, `library-rtl`) use the existing fresh-run screenshot output directory.

The authoring sandbox cannot download the Android toolchain. Read the checks on the **final PR head** for Android compilation, lint and device-test results. Native screenshots must be inspected before visual sign-off; generating them is not inspection.

## Unchanged boundaries and remaining work

Application identity, SDK levels, signing/ad configuration, manifests, recording locations, finalized-file publication, provider grants and release workflows are unchanged. No production signing, main-branch merge or store publication is performed by this change.

Physical-device audio testing is outside this pass. Real-ad/UMP/mediation behavior, privacy disclosures and the actual signed artifact still need owner verification under RELEASE_CHECKLIST.md. TalkBack usability and native visual inspection remain separate from automated bounds checks. Existing settings/player surfaces outside this targeted pass are not claimed to have received a complete audit.
