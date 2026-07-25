# Contributing to RetroFrame

Thanks for considering it. This is a small project with a narrow purpose, and that shapes
what kind of help is most useful.

## The most valuable contribution is a device report

RetroFrame targets hardware that cannot be bought new and cannot be emulated faithfully.
Emulators do not reproduce slow eMMC storage, thermal throttling, aggressive manufacturer
power management, or the storage-provider quirks of old vendor Android builds — and that is
where nearly every real bug lives.

If you have an old tablet, running RetroFrame on it and reporting what happened is a genuine
contribution, and it needs no code at all.

**[File a device report →](../../issues/new?template=device_report.yml)**

Say what worked, what did not, and especially whether the wake/sleep schedule fired
correctly — that is the feature most likely to break in manufacturer-specific ways.

## Before you write code

Read [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md). It is the project's to-do list, sorted by
severity, with a suggested approach for each item. Issues labelled 🔴 are release blockers
and are the most useful things to pick up.

For anything larger than a bug fix, **open an issue first**. It is a poor experience to
finish a feature and then find out it conflicts with the project's goals.

### Things that will not be merged

Not because they are bad ideas, but because they contradict what this project is:

- **Analytics, crash reporting, or telemetry of any kind.** No exceptions, not even opt-in.
- **Cloud photo services** (Google Photos, Dropbox, iCloud). They need accounts, network
  permissions, and API keys. The whole premise is that your photos stay on your device.
- **Anything requiring Google Play Services.** It would disqualify the app from F-Droid and
  break it on de-Googled devices.
- **Raising `minSdk` above 22** without a very strong reason. Android 5.1 is the floor
  because that is where the useful tablets are.
- **Migrating to Compose.** Startup cost and APK size on 1 GB devices are not worth it here.

### Things that are actively wanted

- **Device reports.** Genuinely the scarce resource — see above
- Fixes for anything in [KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md)
- Tests, especially UI and instrumentation coverage, which does not exist yet
- Translations
- Accessibility improvements
- Anything that measurably reduces memory use, battery drain, or startup time

## Development setup

**Requirements:** JDK 17, Android SDK with the API 36 platform. Android Studio bundles both.

```bash
git clone https://github.com/RobTar97/retroframe.git
cd retroframe
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

Android Studio will create `local.properties` with your SDK path on first open. It is
gitignored — never commit it.

```bash
./gradlew assembleDebug     # debug build
./gradlew assembleRelease   # R8-minified release build (~3 MB, unsigned without a keystore)
./gradlew test              # 30 unit tests
./gradlew lint              # Android lint
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI runs all four on every pull request.

Debug builds use the application ID `com.rober.photoframe.debug`, so a debug build and a
release build can sit side by side on the same tablet.

### Testing on a real device

Please do, before opening a PR. If you do not have an old tablet, say so in the PR — a
maintainer or another contributor can verify. An untested change is still worth submitting;
just be upfront about it.

Useful during development:

```bash
adb logcat | grep -E "PhotoRepository|ScreenManager|FolderMonitor|AlarmScheduler|AlarmCompat"
```

Testing the schedule without waiting overnight: set wake and sleep a couple of minutes apart,
lock the device, and watch. The schedule now falls back to inexact alarms when the system
withholds exact ones, so it fires either way — but it may be up to a minute late, which is
worth knowing before you report it as broken.

**Please also test release builds.** R8 is newly enabled, and reflection failures in Media3 or
Glide would show up only there.

## Code style

Match what is already there. Concretely:

- Standard [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html);
  `kotlin.code.style=official` is set in `gradle.properties`
- Keep logic that can be pure, pure. `PlaylistBuilder` and `MediaTypes` have no Android
  dependencies, which is exactly why they are the parts under test
- 4-space indent, no tabs, LF line endings (enforced by `.gitattributes`)
- **Use `.commit()`, not `.apply()`, for SharedPreferences.** This is deliberate — a photo
  frame loses power abruptly, and an async write that has not reached disk is a setting the
  user must re-enter.
- **Fail gracefully.** One bad JPEG, one missing codec, or one folder that vanished must
  never take down the slideshow. Catch, log, continue.
- Log with a `TAG` constant, at `Log.d` for flow and `Log.e` for failures
- Comments explain *why*, not *what*

Keep an eye on what you are adding to the dependency tree. Every library is APK size and
method count on a device that has little of either to spare.

## Pull requests

1. Branch from `main` — `fix/shuffle-preference`, `feat/recursive-folders`
2. Keep it focused. One concern per PR.
3. Confirm `./gradlew assembleDebug assembleRelease test lint` all pass — CI checks all four
4. Write a clear description: what changed, why, and what you tested it on
5. Update `CHANGELOG.md` under `[Unreleased]`
6. If you fixed something from `KNOWN_ISSUES.md`, remove that entry in the same PR

Commit messages: a short imperative subject line, and a body explaining *why* if it is not
obvious.

```
Fix shuffle preference being ignored

PhotoRepository unconditionally called .shuffled(), so the setting
was written but never read and could not be turned off.
```

## Licensing

RetroFrame is GPL-3.0. By submitting a contribution you agree it is licensed under the same
terms. There is no CLA and no copyright assignment — you keep the copyright to what you write.

Do not paste code from sources you do not have the right to relicense under GPL-3.0. That
includes code produced by an AI assistant against a proprietary codebase.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). Be decent to each other.

## Questions

Open a [discussion](../../discussions) or a low-priority issue. Asking is fine — there is no
stupid question about a codebase you did not write.
