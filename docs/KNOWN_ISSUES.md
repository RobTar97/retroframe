# Known issues

An honest inventory of what is wrong with RetroFrame today. Nothing here is hidden from
users — if you are looking for somewhere to start contributing, this is the to-do list.

**Legend** — 🔴 blocker · 🟠 significant · 🟡 minor · ⚪ cleanup

---

## 🔴 Not yet verified on real hardware

**Still the most important entry on the page**, though it is narrower than it was.

CI now runs 14 instrumented tests on emulators at **API 22, 28 and 36** on every push. That
retires one specific fear: the app's scheduling code is executed for real on Android 5.1, so
another call to an API that does not exist there — the bug that made this section necessary —
turns the build red instead of shipping.

What an emulator still cannot tell you:

| Change | What an emulator does not reproduce |
|---|---|
| `ContentObserver` folder watching | Old vendor document providers are inconsistent about firing change notifications. A stock emulator image is not a Samsung ROM from 2016. |
| Single shared Media3 player | Real tablets have one hardware decoder and real thermal limits. |
| Media3 on API 22–24 | Vendor codecs are their own world. |
| Wake/sleep schedule | Manufacturer power management is the whole problem, and it is exactly what emulator images lack. |
| Slow eMMC storage | Emulators run off an SSD. Scan and decode timings are not comparable. |
| Battery and heat over months | Unmeasurable in CI by definition. |

So: the code is now known to *run* on API 22. Whether it *behaves* on a real 2015 tablet left
on a shelf for a fortnight is still unknown, and only a device report can answer it.

If you have an old tablet, **running this and reporting back is the most valuable thing you
can do for the project** — more valuable than code.
[File a device report →](../.github/ISSUE_TEMPLATE/device_report.yml)

---

## 🟠 Significant

### 1. Subfolders deeper than five levels are ignored

The scan descends five levels below the folder you pick, which covers
`Photos/2019/Italy/Rome` comfortably. Anything deeper is not found, and neither is anything
past the 10,000-item cap.

Both limits exist because the app has to point at an SD card without turning one folder pick
into an unbounded number of queries on a single-core tablet. Raising them is a one-line change
in `PhotoRepository`; knowing whether they are the *right* numbers needs a device report from
someone with a genuinely large library.

### 2. Video thumbnails are not shown before playback starts

A video page is black until the player attaches and the first frame decodes. Extracting a
thumbnail for the poster frame would remove the flash of black, at some cost in scan time.

---

## 🟡 Minor

### 3. Favourite weighting cannot space duplicates in tiny libraries

With one or two photos where at least one is favourited, the playlist is mathematically too
short to separate the copies — a favourite accounts for more than half the entries. The
interleave algorithm degrades quietly rather than looping, and this is covered by a test, but
the slideshow will visibly repeat.

Only affects libraries of one or two photos, which is not a realistic photo frame.

### 4. The alarm notification cannot be silenced from the app

Dismissing clears the notification, but the sound is owned by the system notification
channel. Once created, a channel's sound cannot be changed programmatically — the user has to
go into system settings. Correct Android behaviour, but not obvious.

### 5. Only English

All strings are extracted to `strings.xml` and ready for translation, but no other locale
exists yet. `resourceConfigurations` is pinned to `en` to keep the APK small; that line needs
updating when translations are added.

### 6. Thin UI test coverage

52 unit tests cover the pure logic — including the recursive folder walk, against a fake
document provider — and 14 instrumented tests cover scheduling on the
running API level, the manifest's permission and receiver guarantees, and clock mode. CI runs
them on emulators at API 22, 28 and 36.

Still uncovered: the slideshow fragment and the settings screen. Photo mode opens the system
folder picker when no folder is granted, and a SAF grant cannot be scripted — the user
confirming it in the picker is the security model — so testing it means either driving another
app's UI or adding a test-only backdoor to production code.

---

## ⚪ Cleanup

### 7. Robolectric is pinned to SDK 35

The Robolectric tests set `@Config(sdk = [35])` because Robolectric's SDK 36 shadows require
Java 21 and this project builds on Java 17. Harmless — the logic under test is SDK-independent
— but it will need revisiting when the toolchain moves.

### 8. `SCHEDULE_EXACT_ALARM` still requires a Play justification

Reduced but not eliminated. The wake/sleep schedule now uses inexact `setWindow` alarms, so
only the morning alarm clock needs exactness — a much easier case to defend in review. The
permission is still declared. See [PUBLISHING.md](PUBLISHING.md).

### 9. `PhotoView` is unmaintained

`com.github.chrisbanes:PhotoView` has not had a release since 2019 and is pulled from JitPack.
It works, and it is small, but it is a dependency with no upstream. Pinch-to-zoom could be
implemented directly against `ScaleGestureDetector` if it ever becomes a problem.

---

## Fixed in 0.3.0

| Was | Now |
|---|---|
| Subfolders were ignored entirely — photos in `2019/` reported the folder as empty | Scanned five levels deep, capped at 10,000 items, on by default |
| No indication that a large folder was being read | A spinner, which a recursive scan now genuinely needs |
| Turning "Include videos" back on did nothing until something else forced a reload | Settings that change *what is read* trigger a rescan; settings that only change ordering do not |
| The safety-net folder poll never looked inside subfolders | It walks the same ground the scan does |
| Rotating the tablet stopped it holding the screen awake | Window flags reapplied on the restore path |
| Sleep time meant full brightness or nothing | A night-brightness slider, applied per-window so Android undoes it on focus loss |
| A static clock could permanently mark an OLED panel | It drifts a few millimetres a minute |
| Times had to be typed as exactly `HH:mm` | The system clock picker |
| Guava shipped in the APK unattributed | Listed in `NOTICE` and the in-app licence screen |

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
