# Publishing RetroFrame

Where this app can realistically be distributed, what each channel costs, and what has to
be fixed first.

**Summary:** GitHub Releases now, F-Droid next, Google Play if you want reach, Apple never.

---

## Prerequisites for any release

None of the channels below are reachable until these are done. All are tracked in
[KNOWN_ISSUES.md](KNOWN_ISSUES.md).

- [x] ~~Create `app/proguard-rules.pro`~~ — done in 0.2.0; release builds work and minify to ~3 MB
- [x] ~~Adaptive launcher icon with density buckets~~ — done in 0.2.0
- [x] ~~Remove the unused `READ_EXTERNAL_STORAGE` permission~~ — done in 0.2.0, along with
      `INTERNET` and `ACCESS_NETWORK_STATE`
- [x] ~~Raise `targetSdk` to the Play minimum~~ — now API 36
- [ ] **Verify the 0.2.0 rewrite on real hardware.** Nothing should ship before this
- [ ] Create a signing keystore and wire up `keystore.properties`
- [ ] Decide on a `versionCode` / `versionName` scheme and bump from 0.2.0

### Signing

Generate a keystore once and **never lose it**. On Google Play the upload key can be reset,
but for F-Droid-style self-signed distribution, losing the key means users cannot upgrade —
they must uninstall and reinstall, losing their settings.

```bash
keytool -genkey -v -keystore retroframe-release.jks \
  -keyalg RSA -keysize 4096 -validity 10000 -alias retroframe
```

Keep the keystore **out of the repository**. `.gitignore` already excludes `*.jks`,
`*.keystore`, and `keystore.properties`. Put the credentials in `keystore.properties`
(gitignored) and read them from `build.gradle.kts`:

```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }
}
```

Back the keystore up somewhere that is not the same machine.

---

## GitHub Releases — do this first

The lowest-friction channel and the right place to start. No fees, no review, no policy.

Attach a signed APK to a tagged release. Include the SHA-256 checksum so people can verify
what they are sideloading.

This also matters for the target audience specifically: **users on Android 5.1–7 are poorly
served by the Play Store**, which increasingly fails to deliver to old devices. A directly
downloadable APK may end up being the primary distribution route regardless of what else
you do.

---

## F-Droid — the natural home

RetroFrame is close to an ideal F-Droid candidate, and F-Droid users are exactly the people
who run old hardware deliberately.

**Already satisfied:**
- ✅ GPL-3.0 — a recognised free software license
- ✅ No proprietary dependencies (Glide, Media3, PhotoView, AndroidX are all FOSS)
- ✅ No Google Play Services, no Firebase
- ✅ No analytics, no tracking, no network calls at all
- ✅ Reproducible Gradle build from source

**Still needed:**
- Fastlane metadata — **already scaffolded** in `fastlane/metadata/android/en-US/`
- Screenshots in `fastlane/metadata/android/en-US/phoneScreenshots/`
- A tagged release for F-Droid's build server to track
- A merge request against [fdroiddata](https://gitlab.com/fdroid/fdroiddata)

**Tradeoffs:** F-Droid builds everything from source on their own infrastructure and signs
with their key, so inclusion takes time (weeks) and updates lag your tags by days. In
exchange you get a genuinely aligned audience and no policy risk whatsoever.

---

## Google Play — possible, with friction

Reach is the argument for Play. Almost everyone with a tablet has it installed, and it
handles updates automatically.

**Costs:** $25 one-time registration. Since 2023 new personal developer accounts also
require **12 testers running a closed test for 14 continuous days** before you may apply for
production access. Budget weeks, not days.

### Policy issues specific to this app

**1. `SCHEDULE_EXACT_ALARMS` is a restricted permission.**
Play limits it to apps whose *core* purpose is alarms, timers, or calendars. RetroFrame does
have a genuine alarm-clock feature, so a declaration is defensible.

Version 0.2.0 already narrowed the surface as recommended: the wake/sleep schedule now uses
inexact `setWindow` alarms, because waking at 07:02 instead of 07:00 is irrelevant for a photo
frame. The exact-alarm permission is therefore justified **solely** by the morning alarm clock,
which is a much cleaner story to defend in review.

**2. `RECEIVE_BOOT_COMPLETED` + auto-launch on boot.**
Legitimate for a kiosk-style app, but starting an activity from a boot receiver is
increasingly restricted. Expect questions. Document the use case in the listing.

**3. Data safety form.**
This is the easy part and a genuine selling point: no data collected, no data shared, no
network access. Say it plainly in the listing — it is a real differentiator against every
commercial photo-frame app, which invariably want an account.

**4. `targetSdk` treadmill.**
The app targets API 36 today, which satisfies the requirement taking effect 31 August 2026 —
so this is handled for now. But it is a permanent obligation: an app you wanted to write once
and forget will need a compatibility pass every year just to remain listed. Weigh that
honestly against F-Droid, which imposes no such treadmill.

**5. Storage permissions.** Already resolved — the app declares none at all and reads
everything through the Storage Access Framework, which avoids the far stricter
`MANAGE_EXTERNAL_STORAGE` review path entirely.

### Verdict

Worth doing if reach matters to you. Not worth doing first — get real-hardware testing done,
publish on GitHub Releases, gather device reports, submit to F-Droid, and then decide whether
the annual `targetSdk` obligation is a trade you want to sign up for.

---

## What about iOS?

**Short answer: no, and the reason is not App Store policy.**

You suspected this would be difficult. It is worse than difficult — the App Store rules are
the *least* of the obstacles.

### It is not a port, it is a rewrite

RetroFrame is Kotlin against the Android SDK: `Activity`, `Fragment`, `ViewPager2`,
`BroadcastReceiver`, `AlarmManager`, `SharedPreferences`, the Storage Access Framework.
None of these exist on iOS. There is no shared layer to lift. An iOS version means writing
the app again in Swift, or rewriting both in Flutter or Kotlin Multiplatform and maintaining
that instead.

### iOS structurally forbids what this app is for

This is the real blocker. The core features are not merely restricted on iOS — they are
impossible:

| Feature | iOS reality |
|---|---|
| Auto-start on boot | No equivalent. Nothing launches an app after a reboot, ever. |
| Wake the screen at 07:00 | Apps cannot turn the display on. A local notification is the ceiling. |
| Sleep at 23:00 | Apps cannot control the display or the idle timer beyond `isIdleTimerDisabled`. |
| Run unattended for weeks | The OS suspends and eventually terminates background apps. |
| Read an arbitrary folder | Sandboxed. Photos library or a security-scoped bookmark; no equivalent of a persisted tree URI over an SD card. |
| SD card / USB storage | Not a thing on iPad in the way this app assumes. |

What you could ship is a foreground-only slideshow that requires the user to launch it
manually, keeps the screen awake while open, and stops when the device sleeps. That is a
meaningfully worse product wearing the same name, and it would need permanent Guided Access
to stay put.

### The economics are also bad

$99/year, forever, versus $25 once for Google. A Mac is required to build and submit. And
the audience barely exists: the old iPads people actually have in drawers are stuck on iOS
9–12, below the deployment target of any toolchain you would want to use. You would pay an
annual fee to reach devices that cannot install the result.

### If you ever want to revisit

Do it as a **separate project with a separate name**, not as a port. Scope it honestly as a
manual-launch slideshow. Do not let it constrain the Android app's architecture in the
meantime — writing RetroFrame in Flutter today to preserve a hypothetical iOS future would
cost real performance on the 1 GB tablets that are the actual point of the project.

---

## Recommended sequence

1. **Test 0.2.0 on real tablets.** Everything else is blocked on this
2. Generate and back up a signing keystore
3. Tag `v0.2.0`, publish a signed APK on GitHub Releases with its SHA-256
4. Collect device reports — this is what the project most needs
5. Take screenshots on a real tablet for the store listings
6. Submit to F-Droid
7. Reach 1.0 once real devices have confirmed the rewrite
8. Reconsider Google Play — the exact-alarm surface is already narrowed
