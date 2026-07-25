# Known issues

An honest inventory of what is wrong with RetroFrame today. Nothing here is hidden from
users — if you are looking for somewhere to start contributing, this is the to-do list.

**Legend** — 🔴 blocker · 🟠 significant · 🟡 minor · ⚪ cleanup

---

## 🔴 Not yet verified on real hardware

**This is the single most important entry on the page.**

RetroFrame was substantially rewritten in `0.2.0`. The build is green — it compiles, R8
minifies it, 30 unit tests pass and lint is clean — but **none of the rewritten code has run
on a physical tablet.** A green CI build says the code is well-formed. It says nothing about
whether a 2014 tablet's storage provider behaves the way the code assumes.

Specifically unproven:

| Change | What could go wrong on real hardware |
|---|---|
| `ContentObserver` folder watching | Old vendor document providers are inconsistent about firing change notifications. The 15-minute fallback poll should cover it, but that is untested. |
| Single shared Media3 player | The attach/detach timing as pages change is the subtlest part of the rewrite. |
| Media3 on API 22–24 | Media3 claims API 21+, but old vendor codecs are their own world. |
| `targetSdk 36` edge-to-edge | Enforced edge-to-edge interacts with immersive mode; verified only by reading the docs. |
| R8 minification | Release builds are newly possible, so release-only reflection failures have never been observed either way. |
| Wake/sleep schedule | Timing changes were made blind; manufacturer power management varies wildly. |

If you have an old tablet, **running this and reporting back is the most valuable thing you
can do for the project** — more valuable than code.
[File a device report →](../.github/ISSUE_TEMPLATE/device_report.yml)

---

## 🟠 Significant

### 1. Subfolders are ignored

The folder scan is one level deep. Photos organised into `2023/`, `2024/` subfolders will not
be found, and the app will report the folder as empty.

The cursor-based scanner added in `0.2.0` makes recursion cheap to implement — each directory
is one more query rather than hundreds of IPC calls — but it needs a depth limit and a cycle
guard before it can ship.

### 2. No visible progress while a large folder is scanning

A folder with several thousand photos takes a noticeable moment to enumerate on slow eMMC
storage. During that time the UI shows the previous state with no indication that anything is
happening. `SlideshowState.Loading` exists and is simply not rendered.

### 3. Video thumbnails are not shown before playback starts

A video page is black until the player attaches and the first frame decodes. Extracting a
thumbnail for the poster frame would remove the flash of black, at some cost in scan time.

---

## 🟡 Minor

### 4. Favourite weighting cannot space duplicates in tiny libraries

With one or two photos where at least one is favourited, the playlist is mathematically too
short to separate the copies — a favourite accounts for more than half the entries. The
interleave algorithm degrades quietly rather than looping, and this is covered by a test, but
the slideshow will visibly repeat.

Only affects libraries of one or two photos, which is not a realistic photo frame.

### 5. The alarm notification cannot be silenced from the app

Dismissing clears the notification, but the sound is owned by the system notification
channel. Once created, a channel's sound cannot be changed programmatically — the user has to
go into system settings. Correct Android behaviour, but not obvious.

### 6. Only English

All strings are extracted to `strings.xml` and ready for translation, but no other locale
exists yet. `resourceConfigurations` is pinned to `en` to keep the APK small; that line needs
updating when translations are added.

### 7. No UI or instrumentation tests

The 30 unit tests cover the pure logic — media type classification, `HH:mm` parsing, playlist
ordering and weighting. Nothing covers the fragments, the adapter, or the alarm receivers,
all of which need a device or an emulator.

---

## ⚪ Cleanup

### 8. Robolectric is pinned to SDK 34

`PlaylistBuilderTest` sets `@Config(sdk = [34])` because Robolectric 4.13 ships no shadows for
API 36 while the app targets it. Harmless — the logic under test is SDK-independent — but it
will need revisiting when Robolectric catches up.

### 9. `SCHEDULE_EXACT_ALARMS` still requires a Play justification

Reduced but not eliminated. The wake/sleep schedule now uses inexact `setWindow` alarms, so
only the morning alarm clock needs exactness — a much easier case to defend in review. The
permission is still declared. See [PUBLISHING.md](PUBLISHING.md).

### 10. `PhotoView` is unmaintained

`com.github.chrisbanes:PhotoView` has not had a release since 2019 and is pulled from JitPack.
It works, and it is small, but it is a dependency with no upstream. Pinch-to-zoom could be
implemented directly against `ScaleGestureDetector` if it ever becomes a problem.

---

## Fixed in 0.2.0

Recorded here because several of these were long-standing and the reasoning is worth keeping.

| Was | Now |
|---|---|
| `assembleRelease` failed — `proguard-rules.pro` did not exist | Release builds work, R8-minified to ~3 MB |
| **Crash on Android 5.1**: `setExactAndAllowWhileIdle` is API 23, `minSdk` is 22 | `AlarmCompat` picks the right API per version |
| Folder polled every 10s forever, ~1500 IPC calls per scan | `ContentObserver` plus a 15-minute fallback |
| `DocumentFile.listFiles()` — one IPC per file per property | One cursor query for the whole folder |
| Shuffle setting was stored and never read | Works, and off now sorts naturally (IMG_2 before IMG_10) |
| Favouriting a photo rescanned and reshuffled, losing your place | Updates in place |
| Favourites duplicated into the adapter list, breaking identity | Weighting via interleaving; list stays coherent |
| Wake lock released microseconds after acquire | Timeout allowed to expire as intended |
| Schedule silently did nothing without exact-alarm permission | Falls back to inexact, and warns in settings |
| One ExoPlayer per ViewHolder — up to 3 decoders at once | One shared player |
| Images decoded at 2048×2048 regardless of screen | Sized to the display; RGB_565 on low-RAM devices |
| `targetSdk 34` — below the Play minimum | `targetSdk 36` |
| `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_EXTERNAL_STORAGE` declared, unused | Removed |
| Rotation destroyed the activity and lost your place | Handled via `configChanges` |
| Launcher icon was one PNG with no density buckets | Adaptive icon, 5 densities, monochrome variant |
| `transitionEffect` preference read by nothing | Fade / Slide / Zoom implemented |
| Favourites keyed by URI, lost when the folder was re-picked | Keyed by document ID, with migration |
| `FavoritesManager` used `apply()`, against the project's own rule | Uses `commit()` and a `StringSet` |
| Restored backups left a dead folder URI and a blank screen | Validated against persisted permissions on startup |
| `androidx.documentfile` used but never declared | Declared |
| ExoPlayer2, deprecated and end-of-life | AndroidX Media3 |
| `data/AlarmManager` collided with `android.app.AlarmManager` | Renamed `AlarmSettings` |
| No tests at all | 30 unit tests |
