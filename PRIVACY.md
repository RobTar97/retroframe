# Privacy Policy

**Last updated: 4 August 2026**
**Applies to: the RetroFrame Android application, all versions**

## The short version

RetroFrame collects nothing, sends nothing, and stores nothing about you.

It cannot do otherwise. The app does not request the `INTERNET` permission, which means
Android refuses every network call it could ever attempt, at the operating-system level. There
is no account to create, no server to talk to, and no analytics library in the build.

## What this document is, and is not

This is a factual description of what the software does, written by the people who wrote the
software. It is not a legal contract, and it makes no promises about anything other than the
app itself.

It exists because Google Play and F-Droid both require a published privacy policy, and because
"we don't collect anything" is a claim that deserves to be checkable rather than simply
asserted.

## Data this app collects

None.

Specifically, RetroFrame does not collect, transmit, sell, share or process:

- Personal information of any kind — no name, email address, phone number or account
- Your photographs, videos, or any information derived from them
- Location data, of any precision, by any means
- Device identifiers, advertising IDs, or the IMEI
- Usage analytics, crash reports, telemetry, or performance metrics
- Contacts, calendar entries, messages, or call logs
- Anything typed into the app

There is no exception to this list, and no setting that turns any of it on.

## Data this app stores on your device

RetroFrame keeps a small amount of state so it behaves the same way after a restart. All of it
lives in the app's own private storage, is readable only by RetroFrame, and is deleted by
Android when you uninstall the app.

| What | Why | Where |
| --- | --- | --- |
| Your settings — interval, transition, schedule, brightness | So the frame is the way you left it | App-private preferences |
| A reference to the folder you chose | So it does not ask again on every start | App-private preferences |
| A list of which photos you marked as favourites | So they come around more often | App-private preferences |
| Decoded image thumbnails | So the slideshow does not stall on slow storage | App-private cache |

Two things are worth being precise about:

- **The folder reference is a URI, not a copy.** RetroFrame stores a pointer to the folder you
  granted it, not the photos inside it. Your photos are never duplicated, moved or altered.
- **Favourites are stored as document IDs**, which are identifiers assigned by Android's
  storage system. They mean nothing outside your device.

None of this leaves the device, because nothing in this app can leave the device.

## Permissions, and why each one exists

RetroFrame declares exactly four permissions. This is the complete list — there is an automated
test in the project's continuous integration that fails the build if a fifth ever appears.

| Permission | What it is for |
| --- | --- |
| `RECEIVE_BOOT_COMPLETED` | Restart the slideshow after a power cut, if you turned that on |
| `WAKE_LOCK` | Keep the screen on while photos are showing |
| `SCHEDULE_EXACT_ALARM` | Make the wake, sleep and alarm times land on the minute |
| `POST_NOTIFICATIONS` | Show the morning alarm notification, if you set one |

Note what is *not* there:

- **No `INTERNET`.** The app is incapable of network access. This is the important one.
- **No `ACCESS_NETWORK_STATE`.** A media library tried to add this for adaptive streaming
  RetroFrame never does; it is actively removed from the manifest, and a test checks it stays
  removed.
- **No storage permissions.** Photos are read through Android's Storage Access Framework, which
  grants access to exactly the one folder you pick and nothing else. RetroFrame cannot see the
  rest of your device's storage, and never asks to.

## Access to your photos

When you choose a folder, Android — not RetroFrame — shows the picker and issues a permission
scoped to that folder alone. RetroFrame reads image and video files from it in order to display
them. That is the entire extent of the access.

If you enable "Include folders inside it", the same applies to folders within your chosen one,
up to five levels deep.

You can revoke this at any time, from Android's Settings, under Apps → RetroFrame. The app will
simply ask you to pick a folder again.

## Children

RetroFrame is not directed at children, and collects no data from anyone regardless of age. It
has no user accounts, no user-generated content, no messaging, no in-app purchases and no
advertising. Under the United States' Children's Online Privacy Protection Act (COPPA) and
comparable rules elsewhere, there is nothing here to collect and therefore nothing to consent
to.

## Your rights under GDPR, UK GDPR and CCPA

These frameworks grant rights over personal data that an organisation holds about you — the
right to access it, correct it, delete it, port it, or object to its processing.

RetroFrame's developers hold no personal data about you whatsoever. No data controller
relationship arises, because there is no processing of personal data to control. There is
nothing for us to disclose, correct, export or erase, and no data has ever been sold or shared,
because none has ever been received.

To remove everything RetroFrame has stored, uninstall it. Android deletes the app's private
storage as part of that, and nothing survives elsewhere.

## Third-party software

RetroFrame is built with open source libraries — AndroidX, Material Components, AndroidX Media3,
Glide, PhotoView, and the Kotlin standard library. Full attributions are in
[NOTICE](NOTICE) and inside the app under **Settings → About RetroFrame**.

None of these libraries is a software development kit for analytics, advertising, attribution or
crash reporting. None of them can reach a network in this app even if it wanted to, because the
app has no network permission to lend them.

## Distribution

If you install RetroFrame from **GitHub Releases**, **F-Droid** or **Google Play**, that
platform's own privacy policy governs your interaction with the platform — for example, the
fact that you downloaded something. That is between you and them, and is outside this policy's
scope. It has no bearing on the app itself, which behaves identically however you got it.

## How to verify all of this

You do not have to take our word for any of it. In rough order of effort:

1. **Check the permissions on your device.** Settings → Apps → RetroFrame → Permissions.
2. **Inspect the APK.** `aapt dump permissions retroframe-v0.3.0.apk` lists exactly what the
   installed package declares.
3. **Watch the network.** Put the tablet behind any traffic monitor you like and use the app for
   as long as you care to. You will see nothing, because the operating system will not permit
   it.
4. **Read the source.** It is all at
   [github.com/RobTar97/retroframe](https://github.com/RobTar97/retroframe) under the GPL-3.0,
   including the [manifest](app/src/main/AndroidManifest.xml) and the
   [test that enforces the permission list](app/src/androidTest/java/com/rober/photoframe/ManifestInstrumentedTest.kt).
5. **Build it yourself** and compare, or install the F-Droid build, which is compiled from this
   source by F-Droid rather than by us.

## Changes to this policy

If RetroFrame's behaviour ever changes in a way that affects this document, the change will
appear in [CHANGELOG.md](CHANGELOG.md) and in the release notes, and the date at the top of this
file will be updated. The version history of this file is public in the repository, so any edit
is visible and attributable.

Adding network access would be a fundamental change to what this project is, not a routine
update. It is not planned.

## Contact

Questions about this policy, or about anything above that you think is inaccurate, belong in a
[GitHub issue](https://github.com/RobTar97/retroframe/issues) — a public answer helps the next
person to ask. For a security concern, please follow [SECURITY.md](SECURITY.md) instead.
