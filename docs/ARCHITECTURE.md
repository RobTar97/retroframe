# Architecture

How RetroFrame is put together, and why. This describes **only what the code actually does**;
where a design is still questionable it says so and links to [KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Design constraints

Every decision follows from one premise: the target device is a 2013–2018 tablet with 1 GB of
RAM, slow eMMC storage, a single- or dual-core CPU, and an Android version between 5.1 and 9.

That rules out a lot of modern Android practice:

| Not used | Why |
|---|---|
| Jetpack Compose | Startup cost and APK size; the Views version of this UI is trivial |
| Hilt / Dagger | Reflection and codegen overhead for six objects that never change |
| Room | There is no relational data — the folder *is* the database |
| Multi-module Gradle | Build complexity with no payoff at ~2,000 lines |
| WorkManager | Adds a database and a service for two alarms a day |

The app also does **no network I/O at all**, and the manifest enforces it: `INTERNET` is not
declared, so the claim is checkable rather than a promise.

## Stack

- **Kotlin** 2.0.21 · **AGP** 8.9.1 · **Gradle** 8.13 · JVM target 11
- `minSdk 22` · `targetSdk 36` · `compileSdk 36`
- Views + ViewBinding, MVVM (ViewModel + LiveData, coroutines for async work)
- **Glide** 4.16 — image decode and disk cache
- **PhotoView** 2.3 — pinch-to-zoom `ImageView`
- **Media3** 1.4.1 — video playback
- **ViewPager2** 1.1 — slideshow paging

Release builds run R8 with resource shrinking: ~3 MB, which matters for install time and
cold start on slow storage.

## Module layout

```
app/src/main/java/com/rober/photoframe/
├── PhotoframeApp.kt            Application — initialises four singletons, nothing more
├── MainActivity.kt             Single activity; owns immersive mode and screen-on policy
│
├── model/
│   └── MediaItem.kt            documentId, uri, name, type, dateModified, size
│
├── data/
│   ├── PhotoRepository.kt      Single-cursor SAF scan (see below)
│   ├── FolderMonitor.kt        ContentObserver + slow fallback poll
│   ├── PlaylistBuilder.kt      Pure: sorting, favourite weighting, interleaving
│   ├── MediaTypes.kt           Pure: MIME/extension → MediaType
│   ├── FavoritesManager.kt     Favourite document IDs, cached in memory
│   └── AlarmSettings.kt        The user's morning alarm
│
├── settings/
│   ├── PhotoframePreferences.kt   All settings; TimeOfDay and TransitionEffect live here
│   └── SettingsDialogFragment.kt  The one settings screen
│
├── ui/
│   ├── SlideshowFragment.kt    Photo mode: paging, controls, favourites
│   ├── SlideshowViewModel.kt   Library, playlist, advance timer
│   ├── SlideshowAdapter.kt     One page per slide
│   ├── SharedPlayer.kt         The single Media3 player
│   ├── ImageLoader.kt          Glide config tuned to the display
│   ├── SlideTransformers.kt    Fade / Slide / Zoom
│   └── ClockFragment.kt        Clock mode
│
├── schedule/                   Everything time-driven, in one package
│   ├── DailySchedule.kt        Wake/sleep times + its ScheduleReceiver
│   ├── AlarmScheduler.kt       The morning alarm clock
│   ├── AlarmReceiver.kt        Alarm notification; also holds AlarmDismissReceiver
│   └── AlarmCompat.kt          Version-safe scheduling — the API 22 crash guard
│
└── boot/
    └── BootReceiver.kt         Re-arms everything after a reboot
```

Scheduling used to be split across `util/` and `alarm/`, so "how does the wake schedule work"
and "how does the alarm clock work" lived in different places with their shared compatibility
helper in a third. They are one concern and now share one package.

## The two modes

One activity, two fragments. The distinction that matters is **who may turn the screen off**.

```
                        ┌──────────────────────────┐
          wake alarm    │                          │   sleep alarm
       or clock button  │       MainActivity       │   or clock button
              ┌─────────┤   (immersive fullscreen) ├─────────┐
              │         │                          │         │
              ▼         └──────────────────────────┘         ▼
   ┌────────────────────────┐                    ┌────────────────────────┐
   │   SlideshowFragment    │                    │     ClockFragment      │
   │  PHOTO MODE            │                    │  CLOCK MODE            │
   │  FLAG_KEEP_SCREEN_ON   │                    │  flag cleared →        │
   │  screen never sleeps   │                    │  device timeout applies│
   └────────────────────────┘                    └────────────────────────┘
```

Mode is a `"MODE"` string extra on the launch intent, handled in `onCreate` and `onNewIntent`.
`MainActivity.show()` refuses to replace a fragment with one of the same type, so a re-fired
alarm does not tear down and rebuild a running slideshow.

Immersive mode lives entirely in `applyImmersiveMode()` via `WindowInsetsControllerCompat`.
There is deliberately no `android:windowFullscreen` in the theme: from API 35 the platform
ignores it and enforces edge-to-edge, and two mechanisms that disagree on newer devices is
worse than one.

## Media pipeline

### Scanning: one query, not thousands

This is the most important performance decision in the app.

The obvious implementation is `DocumentFile.fromTreeUri(...).listFiles()`, which is what
RetroFrame used to do. `listFiles()` runs one query to get child IDs and wraps each in a
`DocumentFile`; every subsequent `name`, `lastModified()` or `isDirectory` read is *another*
query across a Binder boundary. A 500-photo folder cost roughly 1,500 IPC round trips — and it
ran on a 10-second timer.

`PhotoRepository.scan()` asks for every column it needs in a single cursor query and reads
them straight off the row:

```
DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
   │  projection: DOCUMENT_ID, DISPLAY_NAME, MIME_TYPE, LAST_MODIFIED, SIZE
   ▼
one ContentResolver.query() per folder
   │  MediaTypes.classify() · drop videos if disabled
   │  directories → queued for the next depth level
   ▼
List<MediaItem>
```

One IPC round trip regardless of folder size.

### Recursion, and why it is affordable

The walk is breadth-first, one query per directory, bounded three ways:

| Guard | Value | Why |
|---|---|---|
| `MAX_DEPTH` | 5 | Covers `Photos/2019/Italy/Rome`; stops an SD card becoming a thousand queries. **This is what guarantees the walk terminates.** |
| `MAX_ITEMS` | 10,000 | The target device has 1 GB of RAM |
| `visited` set | — | A provider can report a folder as its own descendant. This does not prevent an infinite loop — `MAX_DEPTH` does — it prevents a cycle being *re-walked at every remaining level*, which returns the same photo five times and makes the query count exponential |

That distinction is not academic. The first version of the test for this compared sets rather
than lists, so it passed with the visited set deleted. Mutation testing caught it.

`signature()` walks exactly the same ground. A signature that only covered the top folder
would report "nothing changed" forever while photos piled up in a subfolder — which is the
one failure the safety-net poll exists to catch.

### Ordering

```
library (unique)  ──►  PlaylistBuilder.build()  ──►  playlist (may repeat favourites)
                            │
                            ├── shuffle off → natural name sort, no weighting
                            └── shuffle on  → weight favourites ×3, then interleave
```

Two rules worth knowing before changing this:

- **Weighting only applies when shuffling.** Duplicating entries inside a name-sorted list
  would show the same photo three times consecutively, which is not what "show my favourites
  more often" means.
- **Interleaving is not a shuffle.** A plain shuffle of a weighted list sometimes puts two
  copies of a photo back to back, which reads as the slideshow being stuck. `interleave()`
  deals items into even slots then odd slots in descending weight order — provably free of
  adjacent duplicates whenever no single item exceeds half the playlist.

`PlaylistBuilder` and `MediaTypes` are pure and Android-free, and carry most of the test suite.

### Rendering and advance timing

```
SlideshowAdapter ──┬── IMAGE → ImageLoader (Glide) → PhotoView
                   └── VIDEO → SharedPlayer        → PlayerView
```

**One player, not one per page.** ViewPager2 keeps offscreen pages alive, so the old
one-player-per-ViewHolder design could hold three decoder sessions at once. Old tablets
typically have a single hardware decoder. `SharedPlayer` moves a single instance onto whichever
page is active.

**Images and videos advance differently:**

- Images advance on a timer in the ViewModel.
- Videos suspend the timer; `SharedPlayer` calls back on `STATE_ENDED`, so a clip always plays
  to its natural end regardless of the configured interval. A playback *error* triggers the
  same callback, so a codec the device lacks skips the video instead of stalling the frame.

The ViewModel does **not** own the current position — the pager does. It emits
`advanceRequests` ticks and the fragment moves the pager. The previous design had the
ViewModel write a position, the fragment observe it and move the pager, and the pager report
back, restarting the timer that had just fired — every automatic advance cancelled and
recreated its own coroutine.

### Image decoding

`ImageLoader` sizes decodes to the actual display rather than a fixed 2048×2048 (four times
more pixels than a 1280×800 panel can show), uses `RGB_565` on low-RAM devices to halve bitmap
memory, and preloads the next photo so an advance does not stall on slow storage.

## Watching the folder

`FolderMonitor` registers a `ContentObserver` on the tree's children URI and does nothing until
the provider reports a change, debounced by 2 seconds to collapse the burst you get when
copying 200 photos at once.

A 15-minute fallback poll remains, because old vendor document providers are not reliable about
notifying. It compares a cheap `FolderSignature` (file count + sum of modification times)
rather than building the full media list, and it is tied to the fragment's `onStart`/`onStop`,
so nothing runs while the slideshow is off screen.

## Scheduling

Three scheduled behaviours, all re-armed 24 hours ahead when they fire, and all re-registered
by `BootReceiver` because Android drops pending alarms on shutdown.

| | Owner | Exactness | Effect |
|---|---|---|---|
| **Wake** | `DailySchedule` | Inexact (±1 min) | Wake lock, launch in `PHOTO` mode |
| **Sleep** | `DailySchedule` | Inexact (±1 min) | Launch in `CLOCK` mode; screen may sleep |
| **Alarm clock** | `AlarmScheduler` | Exact if permitted | Full-screen notification + alarm sound |

`AlarmCompat` picks the best API for the running device — `setExactAndAllowWhileIdle` on 23+,
`setExact` on 19–22, `set` below. The old code called the API 23 method unconditionally with
`minSdk 22`, which would have crashed on Android 5.1: the oldest version the app claims to
support, and the hardest to test on.

Wake and sleep are deliberately inexact. A photo frame waking at 07:02 is indistinguishable to
the user, it lets the system batch the wake-up, and it means only the alarm clock needs the
`SCHEDULE_EXACT_ALARMS` justification. When exactness is unavailable the schedule still fires
approximately — the previous version returned silently and simply never ran.

## Persistence

SharedPreferences throughout. No database, no file I/O outside SAF.

| File | Contents |
|---|---|
| `photoframe_prefs` | Interval, shuffle, videos, sound, keep-screen-on, gallery URI, wake/sleep, transition, auto-start |
| `photoframe_favorites` | Favourite document IDs as a `StringSet` |
| `alarm_prefs` | Alarm hour, minute, enabled, label |

**Writes use `commit()`, not `apply()`.** This is deliberate and should be preserved: a photo
frame loses power abruptly, and an asynchronous write that has not reached disk is a setting
the user has to enter again. Lint's `ApplySharedPref` check is disabled in `build.gradle.kts`
for this reason.

Favourites are keyed by SAF **document ID**, not full URI. Document IDs survive the folder
being re-picked; URIs do not, so re-granting access to the same folder used to silently lose
every favourite. A migration converts the old format on first run.

The saved folder URI is validated against `contentResolver.persistedUriPermissions` on every
load. A restored cloud backup, a revoked permission or a removed SD card all leave a URI that
looks valid and is not; without the check the app sits on an empty screen insisting it has a
folder.

## Extending it

**Adding a setting**
1. Key + accessor in `PhotoframePreferences` — use `commit()`
2. Widget in `res/layout/dialog_settings.xml`
3. Bind in `SettingsDialogFragment`: field, `loadSettings`, `saveSettings`
4. Consume it somewhere — an unread preference is how `transitionEffect` sat dead for a year

**Adding a media type**
Add the extension to `MediaTypes`, and if it needs different rendering, branch in
`SlideshowAdapter.bind()`. Check hardware decode exists on old devices before adding a video
format.

**Adding a scheduled behaviour**
Follow `AlarmScheduler`: schedule through `AlarmCompat`, use a distinct `PendingIntent` request
code, re-arm inside the receiver, and register it in `BootReceiver`.

## Testing

30 unit tests cover the pure logic: `MediaTypes` classification, `TimeOfDay` parsing and
formatting, and `PlaylistBuilder` ordering, weighting and interleaving. Run with
`./gradlew test`.

`PlaylistBuilderTest` needs Robolectric only because `MediaItem` holds an `android.net.Uri`,
and pins `sdk = [34]` because Robolectric ships no shadows for API 36 yet.

There is no UI or instrumentation coverage, and **none of this has run on a physical tablet** —
see the first entry in [KNOWN_ISSUES.md](KNOWN_ISSUES.md). Emulators do not reproduce slow
eMMC, thermal throttling, aggressive manufacturer power management, or the storage-provider
quirks of old vendor Android builds, which is where nearly all real bugs come from.
