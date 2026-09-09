# Security policy

## Reporting a vulnerability

Please do not open a public issue for an exploitable vulnerability, exposed credential, unsafe file-provider path, consent bypass, or recording-data disclosure.

Use GitHub's private vulnerability-reporting or Security Advisory flow when it is available for this repository. Otherwise contact the repository owner privately through the contact method on the owner's GitHub profile. Include affected versions, reproduction steps, impact, and a minimal proof of concept. Do not include real user recordings, signing material, or production ad credentials.

The maintainer should acknowledge a complete report within seven days, provide a status update within fourteen days, and coordinate disclosure after a fix is available. These are response goals, not a warranty.

## Supported versions

Security fixes target the latest published version and the current `main` branch. Older builds may require upgrading before a fix can be applied.

## Security boundaries

- Microphone access begins only after permission and an explicit user action.
- Capture and playback are foreground-screen features unless a future release explicitly documents otherwise.
- Recording sharing uses temporary read-only content-URI grants through restricted provider roots.
- Application backups are disabled and cleartext network traffic is blocked by the app manifest.
- Ads must remain behind consent and initialization gates.
- Production signing and ad configuration must be supplied outside the repository.

Automated checks help preserve these boundaries but do not replace dependency advisories, penetration testing, real-device verification, privacy review, or store-policy review.
