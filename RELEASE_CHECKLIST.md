# Release checklist — final-artifact evidence required

Record evidence for the exact signed artifact and commit being published. Source review, emulator success, and a configured workflow are not production certification.

## Automated gates

- [ ] Debug and R8-processed release builds pass on the final commit.
- [ ] App and ads JVM tests and Android lint pass for every configured variant.
- [ ] Quality-contract, helper-regression, dependency-policy, native ELF, and ZIP-alignment checks pass.
- [ ] API 24, 34, 36, and 37 instrumentation jobs pass; APIs 36 and 37 also pass at 200% font scale.
- [ ] API 37 runs on the intended 16 KiB image.
- [ ] All seven native screenshots are fresh and manually inspected.
- [ ] The signed release bundle passes the production-configuration gate and installs through a representative delivery path.

Evidence links:

- Final commit:
- CI run:
- Signed artifact/version:
- Test report archive:
- Screenshot review:

## Real-device audio matrix

For each tested device record Android version, manufacturer/model, output route, measured latency range, underruns, battery interval, and result.

- [ ] Representative Android 7, 12, 14, and current Android devices.
- [ ] Screen opened without starting: no microphone resource or privacy indicator remains active.
- [ ] Rapid start/stop/restart and cancellation during preparation: no overlap, late capture, or stale UI.
- [ ] Permission deny, grant, revoke, and Settings revocation paths.
- [ ] Phone speaker at low volume; wired, USB, and Bluetooth routes; route disconnect/reconnect.
- [ ] Calls, focus loss/ducking, headphone unplug, screen lock, Home, Back, and process recreation.
- [ ] Measured end-to-end latency, underruns, and battery use; no unmeasured “zero latency” claim.
- [ ] Short, cancelled, storage-full, runtime-error, and finalization-failure recordings.
- [ ] New M4A files play in another app; shared targets receive temporary read-only access.
- [ ] Large and empty libraries, missing tracks, search, filter changes, and rapid navigation.

Evidence/device matrix:

## UX and accessibility

- [ ] TalkBack labels, order, actions, announcements, and hold-to-record fallback.
- [ ] Keyboard/D-pad focus and visible focus indicators.
- [ ] 200% font scale, smallest supported phone, cutouts, gesture and three-button navigation.
- [ ] RTL, contrast, dark theme, tablet/window resizing, and every supported orientation.
- [ ] No essential state communicated by color alone.
- [ ] README/store listing screenshots and descriptions match the tested build.

Evidence:

## Privacy, security, and store sign-off

- [ ] UMP configured and tested for fresh install, prior consent, denial, offline/error, revocation, and applicable regions.
- [ ] No ad or mediation request occurs before the applicable consent/initialization gate opens.
- [ ] Banner, native, fullscreen, and app-open resources clean up across pause, resume, destruction, and privacy-state changes.
- [ ] FileProvider traversal, unrelated files, write attempts, and grant revocation tested from another app.
- [ ] Cleartext traffic remains disabled and exported-component/permission tests pass on the installed release.
- [ ] Privacy policy, Data Safety, content rating, ad declarations, target audience, and store metadata reviewed.
- [ ] Dependency/security advisories and current Play requirements reviewed.
- [ ] Application ID, provider authority, signing key, version code/name, and upgrade path verified.
- [ ] License choice and third-party notices reviewed by the owner.

Evidence:

## Release decision

- [ ] Every blocking item above has evidence or an explicit owner-approved exception.
- [ ] Rollback and support plan prepared.
- [ ] Changelog and release notes finalized.
- [ ] Tag and release point to the exact verified commit.

Decision, owner, and date:

Background playback/capture remains out of scope. Do not advertise it without a correctly typed foreground service, a user-visible persistent notification, platform permissions, policy review, and dedicated tests.
