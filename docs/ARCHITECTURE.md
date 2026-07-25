# Architecture

How RetroFrame is put together, and why. This document describes **only what the code
actually does today** — where a design is questionable, it says so and links to
[KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Design constraints

Every decision follows from one premise: the target device is a 2013–2018 tablet with
1 GB of RAM, a slow eMMC chip, a single- or dual-core CPU, and an Android version between
5.1 and 9.

That rules out a great deal of modern Android practice:

| Not used | Why |
|---|---|
| Jetpack Compose | Startup cost and APK size; the Views version of this UI is trivial |
| Hilt / Dagger | Reflection and codegen overhead for six classes that never change |
| Room | There is no relational data — the folder *is* the database |
| Multi-module Gradle | Build complexity with no payoff at 1,600 lines |
| Coroutines Flow throughout | LiveData is lighter and adequate for this UI |

The app also deliberately does **no** network I/O. It holds `INTERNET` and
`ACCESS_NETWORK_STATE` permissions only as leftovers from a removed weather feature; see
[KNOWN_ISSUES.md](KNOWN_ISSUES.md).

## Stack

- **Kotlin** 1.9.20 · **AGP** 8.2.0 · **Gradle** 8.5 · JVM target 1.8
- `minSdk 22` · `targetSdk 34` · `compileSdk 34`
- Views + ViewBinding, MVVM-ish (ViewModel + LiveData)
- **Glide** 4.16 — image loading and disk cache
- **PhotoView** 2.3 — pinch-to-zoom `ImageView`
- **ExoPlayer** 2.19.1 — video playback (deprecated; Media3 migration is on the roadmap)
- **ViewPager2** 1.0 — slideshow paging

## Module layout

```
app/src/main/java/com/rober/photoframe/
├── PhotoframeApp.kt            Application — initialises the three preference singletons
├── MainActivity.kt             Single activity; swaps fragments, owns immersive mode
│
├── model/
│   └── MediaItem.kt            Data class + MediaType enum (IMAGE | VIDEO)
│
├── data/
│   ├── PhotoRepository.kt      Enumerates the SAF folder, filters, applies weighting
│   ├── FavoritesManager.kt     Favourite URIs in SharedPreferences
│   └── AlarmManager.kt         Alarm-clock model + persistence (NOT android.app.AlarmManager)
│
├── settings/
│   ├── PhotoframePreferences.kt   All slideshow/schedule settings
│   └── SettingsDialogFragment.kt  The one settings screen
│
├── ui/
│   ├── SlideshowFragment.kt    Photo mode: paging, controls overlay, favourite button
│   ├── SlideshowViewModel.kt   Advance timer, media list, playing state
│   ├── SlideshowAdapter.kt     ViewHolder per page: Glide for stills, ExoPlayer for video
│   └── ClockFragment.kt        Clock mode: fullscreen TextClock
│
├── util/
│   ├── ScreenManager.kt        Wake/sleep exact alarms + the receiver that acts on them
│   └── DirectoryWatcher.kt     Polls the folder for changes
│
├── alarm/
│   ├── AlarmScheduler.kt       Schedules the morning alarm
│   └── AlarmReceiver.kt        Fires the notification; also holds AlarmDismissReceiver
│
└── boot/
    └── BootReceiver.kt         Reschedules alarms and optionally auto-launches
```

> **Naming trap:** `data/AlarmManager.kt` is a RetroFrame object that stores the user's
> alarm-clock setting. It is unrelated to `android.app.AlarmManager`, which is used in
> `util/ScreenManager.kt` and `alarm/AlarmScheduler.kt`. Both appear in the same files.
> This is a bad name and should be changed to `AlarmSettings`.

## The two modes

RetroFrame is a single activity that swaps between two fragments. The distinction that
matters is **who is allowed to turn the screen off**.

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
   │                        │                    │                        │
   │  FLAG_KEEP_SCREEN_ON   │                    │  flag cleared →        │
   │  screen never sleeps   │                    │  device timeout applies│
   └────────────────────────┘                    └────────────────────────┘
```

`MainActivity.enableKeepScreenOn()` / `disableKeepScreenOn()` are the whole mechanism.
Mode is selected by a `"MODE"` string extra (`"PHOTO"` or `"CLOCK"`) on the launch intent,
handled in both `onCreate()` and `onNewIntent()`.

## Media pipeline

```
User picks folder
   │  ActivityResultContracts.OpenDocumentTree
   │  takePersistableUriPermission()  ← survives reboot
   ▼
PhotoframePreferences.galleryUriString
   │
   ▼
PhotoRepository.loadMedia()                         [Dispatchers.IO]
   │  DocumentFile.fromTreeUri(...).listFiles()
   │  filter by extension → MediaType
   │  drop videos if includeVideos == false
   │  applyFavoriteWeighting()  ← duplicates favourites 3× then shuffles
   ▼
SlideshowViewModel._mediaItems  (LiveData)
   │
   ▼
SlideshowAdapter.submitList()  → notifyDataSetChanged()
   │
   ├── MediaType.IMAGE → Glide → PhotoView
   └── MediaType.VIDEO → ExoPlayer → PlayerView
```

Subfolders are skipped — enumeration is one level deep only.

### Advance timing

Images and videos advance by different mechanisms, which is the subtlest part of the app:

- **Images** advance on a timer. `SlideshowViewModel.startSlideshow()` runs a coroutine that
  delays for `slideIntervalSeconds` and then increments `_currentPosition`.
- **Videos** do not use the timer at all. `onPageSelected` calls `stopSlideshow()` when the
  new page is a video, and playback advances via the `onVideoEnded` callback when ExoPlayer
  reaches `STATE_ENDED`. A video therefore plays to its natural end regardless of the
  configured interval.

Videos are also prepared with `playWhenReady = false` and only started once the page is
actually visible, after a 100 ms settle delay (`SlideshowFragment.startVideoAtPosition`).
Without this, audio from the next video begins before it is on screen.

> There is a feedback path here worth understanding before editing:
> `startSlideshow` → `_currentPosition` → LiveData observer → `viewPager.currentItem` →
> `onPageSelected` → `updateCurrentPosition` → `startSlideshow(restart = true)`.
> It terminates, but it restarts the timer coroutine on every page change.

## Scheduling

Three independent scheduled behaviours, all built on `android.app.AlarmManager` with
`setExactAndAllowWhileIdle` and `RTC_WAKEUP`, all rescheduled 24 hours ahead each time they
fire.

| | Owner | Effect |
|---|---|---|
| **Wake** | `util/ScreenManager.kt` | Acquires a screen wake lock, launches in `PHOTO` mode |
| **Sleep** | `util/ScreenManager.kt` | Launches in `CLOCK` mode; screen may then sleep |
| **Alarm clock** | `alarm/AlarmScheduler.kt` | High-priority notification + system alarm sound |

`BootReceiver` re-registers all of them after a reboot, because Android drops pending alarms
on shutdown. This is why auto-start on boot matters for an unattended device.

On API 31+ every path checks `canScheduleExactAlarms()` and **silently returns** if the
permission is absent. The user gets no feedback that their schedule will not fire — a UX gap
worth closing.

## Persistence

Everything is SharedPreferences. There is no database and no file I/O outside SAF.

| File | Contents |
|---|---|
| `photoframe_prefs` | Interval, shuffle, videos, sound, gallery URI, wake/sleep, transition, clock style, auto-start |
| `photoframe_favorites` | Favourite URIs, one `\|\|\|`-delimited string |
| `alarm_prefs` | Alarm hour, minute, enabled, label |

`PhotoframePreferences` uses `.commit()` rather than `.apply()` throughout. This is
intentional and should be preserved: a photo frame is a device that loses power abruptly,
and an asynchronous write that has not reached disk is a setting the user has to enter
again. `FavoritesManager` does not follow this rule, which is a bug.

All three are `object` singletons initialised from `PhotoframeApp.onCreate()`. They must be
initialised before use, which is why `BootReceiver` calls `PhotoframePreferences.init()`
defensively — broadcast receivers can run in a process where the Application has just been
created.

## Extending it

**Adding a setting**
1. Key + accessor in `PhotoframePreferences` — use `.commit()`
2. Widget in `res/layout/dialog_settings.xml`
3. Bind it in `SettingsDialogFragment.onViewCreated`, read in `loadSettings`, write in `saveSettings`
4. Consume it somewhere — otherwise you have created another issue #12

**Adding a media type**
Add the extension to `PhotoRepository.getMediaType()`, and if it needs different rendering,
add a branch in `SlideshowAdapter.bind()`. Verify hardware decode exists on old devices
before adding a video format.

**Adding a scheduled behaviour**
Follow `AlarmScheduler`: check `canScheduleExactAlarms()`, use a distinct
`PendingIntent` request code, reschedule from within the receiver, and re-register in
`BootReceiver`.

## Testing

There is currently no test suite. See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) #19.

Emulators are a poor proxy for this project. They do not reproduce slow eMMC, thermal
throttling, aggressive manufacturer power management, or the storage-provider quirks of
old vendor Android builds — which is where nearly all real bugs come from. Device reports
from actual hardware are the most valuable contribution this project can receive.
