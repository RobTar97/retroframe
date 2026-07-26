# Changelog

All notable changes to RetroFrame are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- **`SCHEDULE_EXACT_ALARMS` was not a real permission.** The manifest had it plural; the
  actual name is singular. The system therefore never offered it, and the morning alarm ran
  on inexact timing on every device since the feature was written.
- **`ACCESS_NETWORK_STATE` was present in the installed app**, merged in from Media3 for
  adaptive streaming this app never does. Removed, so "no network access" is true on
  inspection and not just in the readme.
- **Save and Cancel were below the fold** in settings on an 800px landscape tablet. They are
  now a fixed footer outside the scroll area.

### Added

- 14 instrumented tests, and a CI emulator matrix at **API 22, 28 and 36**. API 22 is the
  point: it executes the scheduling code on the oldest supported Android, where a call to a
  newer API previously would have crashed with nothing noticing.
- Tests that assert the manifest declares exactly four permissions and no network permission,
  and that every scheduled receiver is still reachable after a refactor or an R8 rename.
- ktlint, `.editorconfig`, `CODEOWNERS` and a `docs/` index.

### Changed

- Scheduling consolidated from `util/` and `alarm/` into one `schedule/` package;
  `ScreenManager` renamed `DailySchedule`, `FolderMonitor` moved to `data/`.
- Dependencies: AGP 8.13.2, and measured ceilings recorded for everything blocked by
  `minSdk 22` — Material 1.14, Media3 1.10, lifecycle 2.11, fragment 1.8.9, Glide 5,
  core-ktx 1.19, AGP 9.x and Gradle 9.x all require a floor this project will not raise.

## [0.2.0] — 2026-07-26

A substantial rewrite focused on performance, correctness, and being publishable at all.

> ⚠️ **Not yet verified on physical hardware.** The build is green and 30 unit tests pass,
> but none of this has run on a real tablet. See [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md).

### Fixed

- **Crash on Android 5.1.** `setExactAndAllowWhileIdle` is API 23, but `minSdk` is 22 — every
  scheduled alarm would have thrown `NoSuchMethodError` on the oldest version the app claims
  to support. `AlarmCompat` now picks the right API per version.
- **Release builds were impossible.** `proguard-rules.pro` was referenced but never existed,
  so `assembleRelease` always failed. Only debug builds had ever been produced.
- **The shuffle setting did nothing.** It was written by the settings screen and read by
  nothing; the playlist was always shuffled and could not be turned off.
- **Favouriting a photo jumped the slideshow.** Every heart tap rescanned the folder and
  reshuffled, so the photo you had just favourited disappeared.
- **The wake-up wake lock was released immediately.** `acquire(timeout)` returns at once, and
  a `finally` block released it microseconds later, so the intended hold never happened.
- **The schedule silently did nothing** when the system withheld exact-alarm permission. It
  now falls back to inexact alarms and warns in the settings screen.
- **Favourites were lost when the folder was re-picked**, because they were keyed by full URI.
  They are now keyed by SAF document ID, with a migration from the old format.
- **Restored backups left the app on a blank screen** holding a folder URI it had no
  permission for. The URI is now validated against persisted grants on startup.
- **Rotation destroyed the activity**, rescanning the folder and losing your place.
- `FavoritesManager` used `apply()` against the project's own documented `commit()` rule.
- SAF permission grants were never released when changing folders, slowly exhausting the
  per-app quota.

### Performance

- **Folder scanning is one cursor query instead of thousands of IPC calls.**
  `DocumentFile.listFiles()` costs a Binder round trip per file *per property* — roughly 1,500
  calls for a 500-photo folder. `PhotoRepository` now projects every column it needs in a
  single `ContentResolver.query()`.
- **Folder watching uses a `ContentObserver` instead of a 10-second poll.** The old watcher
  ran that expensive scan every 10 seconds, forever, which for a photo frame means
  permanently. A 15-minute signature-only poll remains as a safety net for providers that do
  not notify, and it stops while the slideshow is off screen.
- **One shared video player instead of one per page.** ViewPager2 keeps offscreen pages alive,
  so the old design could hold three decoder sessions on devices with a single hardware
  decoder.
- **Images decode to the display size**, not a fixed 2048×2048 — about four times fewer pixels
  on a 1280×800 panel. Low-RAM devices additionally use `RGB_565`, halving bitmap memory.
- The next photo is preloaded so an advance does not stall on slow eMMC storage.
- R8 minification and resource shrinking enabled: the release APK is ~3 MB.
- `Log.v`/`Log.d` calls are stripped from release builds, avoiding the string concatenation
  entirely rather than discarding the result.

### Added

- **Transition effects** — fade, slide and zoom. The `transitionEffect` preference had existed
  from the beginning and was read by nothing.
- **Adaptive launcher icon** with five density buckets and a monochrome variant, generated
  from the source art. Previously a single PNG in `drawable/`.
- **Natural filename sorting** when shuffle is off, so IMG_2 comes before IMG_10.
- **Favourite spacing** — a favourite never appears twice in a row, using an interleave that is
  provably free of adjacent duplicates whenever that is possible at all.
- **Keep-screen-on setting**, so the display timeout can apply during photo mode.
- **Empty states** with a folder picker button, instead of a blank screen.
- **Input validation** on all `HH:mm` fields; invalid times are rejected rather than silently
  stored as nonsense.
- **Exact-alarm warning** in settings, with a shortcut to the system screen that grants it.
- Full-screen alarm notification, so the morning alarm shows over the lock screen.
- Stale favourites are pruned when their photos disappear from the folder.
- 30 unit tests covering media type classification, `HH:mm` parsing, and playlist ordering,
  weighting and interleaving.
- HEIC/HEIF and 3GP support.

### Changed

- **Migrated ExoPlayer2 → AndroidX Media3.** ExoPlayer2 is end-of-life.
- **`targetSdk` 34 → 36**, meeting the Google Play requirement effective 31 August 2026.
- **Wake/sleep alarms are now inexact.** Waking at 07:02 is indistinguishable for a photo
  frame, it lets the system batch the wake-up, and it means only the alarm clock needs the
  restricted `SCHEDULE_EXACT_ALARMS` justification.
- Toolchain: Gradle 8.5 → 8.13, AGP 8.2.0 → 8.9.1, Kotlin 1.9.20 → 2.0.21, and dependencies
  moved to a version catalog.
- `data/AlarmManager` renamed to `AlarmSettings` — it sat in the same files as
  `android.app.AlarmManager` and made every reference ambiguous.
- The ViewModel no longer owns the slideshow position, removing a feedback loop where every
  automatic advance cancelled and recreated the coroutine that had just fired it.
- Debug builds use the `.debug` application ID suffix, so both variants can be installed at
  once.
- All user-facing strings extracted to `strings.xml`, and content descriptions added
  throughout.
- Default accent colour changed from the Android Studio template purple to a warm amber that
  sits better against photographs.

### Removed

- **`INTERNET` and `ACCESS_NETWORK_STATE` permissions.** The app makes no network requests, so
  the manifest now enforces that claim rather than merely stating it.
- **`READ_EXTERNAL_STORAGE`.** Media is read exclusively through the Storage Access Framework.
- Unused Retrofit, Gson and DataStore dependencies, left over from a removed weather feature.
- The `clockStyle` preference, which was stored and never read.

## [0.1.0] — unreleased

Open-sourced the project as **RetroFrame** under GPL-3.0.

### Added

- GPL-3.0 licence, README, contribution guide, Contributor Covenant, security policy
- Issue templates for bugs, features and device reports; pull request template
- GitHub Actions CI and Dependabot
- `docs/ARCHITECTURE.md`, `docs/KNOWN_ISSUES.md`, `docs/PUBLISHING.md`
- Fastlane metadata for F-Droid and Play listings

### Changed

- Renamed from Photoframe to RetroFrame. The application ID stays `com.rober.photoframe`,
  since it can never change once published.

### Removed

- Seven stale status documents describing features that were never implemented (weather
  widget, `SettingsMonitor`, `UriUtils`, transition effects, analog clock), several of which
  contradicted each other on whether the alarm feature was complete. Superseded by
  `docs/ARCHITECTURE.md`; the history remains in git.

[Unreleased]: https://github.com/RobTar97/retroframe/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/RobTar97/retroframe/releases/tag/v0.2.0
