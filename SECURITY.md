# Security Policy

## Supported versions

RetroFrame has not yet had a stable release. Until 1.0, only the latest commit on `main`
receives fixes.

| Version | Supported |
| ------- | --------- |
| `main`  | ✅ |
| Pre-1.0 tags | ❌ |

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

Report it privately through GitHub's
[private vulnerability reporting](../../security/advisories/new), or email the maintainer.

Please include:

- What the issue is and why it matters
- Steps to reproduce
- Android version and device, if relevant
- Any suggested fix

You can expect an acknowledgement within a week. This is a hobby project maintained in spare
time — it is not a product with an on-call rotation, and the response time reflects that.
Fixes for confirmed issues will be prioritised over features.

You will be credited in the advisory and the changelog unless you would rather not be.

## Threat model

RetroFrame's attack surface is deliberately small, which is worth stating explicitly:

**The app does not:**
- Make any network requests — there is no server, no API, no telemetry, no update check
- Collect, transmit, or store any personal data off-device
- Require or use an account
- Include an analytics or crash-reporting SDK
- Request storage permissions beyond the single folder you explicitly grant

**What it does have access to:**
- One folder tree, chosen by you through the system picker, retained via a persistable URI
  permission. It reads that folder. It never writes to it, and never touches anything else.
- Its own SharedPreferences (settings, favourites, alarm time)

**Known non-issues, so you do not need to report them:**
- `INTERNET` and `ACCESS_NETWORK_STATE` are declared in the manifest but never used. They
  are leftovers from a removed weather feature and are on the list to be deleted. No code
  path opens a socket.
- `READ_EXTERNAL_STORAGE` is likewise declared and unused; media access goes exclusively
  through the Storage Access Framework. Also scheduled for removal.
- Settings are stored in plain SharedPreferences, unencrypted. This is intentional — the
  contents are a folder URI and a slideshow interval, not secrets. Anyone with the physical
  device and root access has your photos anyway.

## Things genuinely worth reporting

- Any way the app reads or exposes files outside the granted folder
- Any unexpected network activity
- An exported component that can be triggered by another app to do something harmful
- A way to bypass the lock screen via the wake alarm or the fullscreen activity
- Anything that lets a crafted media file execute code

## Verifying a build

Release APKs published on GitHub Releases include a SHA-256 checksum. If you are sideloading,
check it:

```bash
sha256sum retroframe-vX.Y.Z.apk
```

You can also build from source and compare — the whole point of the project being open is
that you do not have to take anyone's word for it.
