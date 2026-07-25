# Known issues

An honest inventory of what is wrong with RetroFrame as of the initial open-source
release. Nothing here is hidden from users — if you are looking for somewhere to start
contributing, this is the to-do list.

Each item is labelled with where it lives and roughly how hard it is to fix.

**Legend** — 🔴 release blocker · 🟠 significant · 🟡 minor · ⚪ cleanup

---

## 🔴 Release blockers

### 1. Release builds fail — `proguard-rules.pro` does not exist

`app/build.gradle.kts:23` references `proguard-rules.pro`, but the file was never created.
`./gradlew assembleRelease` therefore fails. Only debug builds have ever worked, which is
why this went unnoticed.

**Fix:** create `app/proguard-rules.pro`. It can be empty to start with, since
`isMinifyEnabled = false`. If minification is ever turned on, Glide, ExoPlayer, and Gson
all need keep rules.

---

### 2. `targetSdk 34` is below the Google Play minimum

`app/build.gradle.kts:14`. Google Play has required API 35 for new app submissions since
31 August 2025, and requires API 36 from 31 August 2026. A new app targeting 34 will be
rejected outright.

**Fix is not just a number change.** Targeting API 35+ enforces edge-to-edge display, which
interacts directly with `Theme.Photoframe.Fullscreen` and the immersive-mode handling in
`MainActivity.hideSystemUI()`. This needs testing on a real device, not just a rebuild.

`minSdk 22` is unaffected — Play has no minimum there.

---

### 3. Folder polling burns battery on exactly the hardware this app targets

`util/DirectoryWatcher.kt:32` runs `DocumentFile.listFiles()` every 10 seconds, forever,
for as long as the app is open — which for a photo frame is permanently.

Every poll is a Storage Access Framework query that crosses a Binder boundary into the
external storage provider, builds a `DocumentFile` for every entry, and then calls
`lastModified()` on each one — a second IPC round trip per file. On a folder of 500 photos
this is thousands of IPC calls per minute, on a single-core tablet, indefinitely.

This is the single worst performance defect in the project, and it directly contradicts the
"good performance on old hardware" goal.

**Fix options, roughly in order of preference:**
- Register a `ContentObserver` on the tree URI and drop polling entirely
- If polling must stay, raise the interval to several minutes and only poll while the
  screen is actually on
- Cache `lastModified()` results instead of re-querying every entry each pass

---

### 4. Launcher icon is not shippable

`app/src/main/res/drawable/ic_launcher.png` is a single PNG in `drawable/`, with no density
buckets and no adaptive icon. On modern Android it will be letterboxed inside a white
circle; on old tablets it will be scaled badly.

**Fix:** generate `mipmap-*` density variants plus an adaptive icon
(`mipmap-anydpi-v26/ic_launcher.xml` with foreground and background layers). Android
Studio's Image Asset tool does this from the source art in `art/icon-source.png`.

---

## 🟠 Significant

### 5. The shuffle setting does nothing

`PhotoframePreferences.shuffle` (`settings/PhotoframePreferences.kt:33`) is written by the
settings dialog and read by nothing. `PhotoRepository` unconditionally calls `.shuffled()`
at both `data/PhotoRepository.kt:69` and `:85`.

The practical effect is that the slideshow is *always* random and the checkbox is a lie in
both directions — you cannot turn shuffle off.

**Fix:** branch on the preference, and add a deterministic ordering (by name or by date)
for the un-shuffled path.

---

### 6. Favouriting a photo jumps the slideshow

`ui/SlideshowFragment.kt:254` calls `viewModel.refreshMedia()` after every heart tap. That
reloads the entire folder and reshuffles it, so the photo you just favourited disappears
and is replaced by a random one.

**Fix:** update the favourite state in place. The re-weighting does not need to take effect
until the next natural reload.

---

### 7. Favourite weighting duplicates objects in the adapter list

`data/PhotoRepository.kt:66–86` implements 3× weighting by inserting the same `MediaItem`
into the list three times. Combined with `MediaItem.id = name.hashCode().toLong()`
(`data/PhotoRepository.kt:45`), the adapter now contains multiple items claiming the same
identity. `hashCode()` on a String is also not collision-free, so two differently named
photos can already collide.

This works today only because `SlideshowAdapter` uses `notifyDataSetChanged()` and has no
`DiffUtil` or stable IDs. It will break the moment either is introduced.

**Fix:** keep the media list unique and implement weighting in the *selection* of the next
index rather than by duplicating list entries.

---

### 8. The wake-up WakeLock is released immediately

`util/ScreenManager.kt:114` acquires a `SCREEN_BRIGHT_WAKE_LOCK` with a 3-second timeout,
but `acquire(timeout)` returns immediately and the `finally` block at `:126` releases the
lock microseconds later. The intended 3-second hold never happens.

It appears to work in practice because `ON_AFTER_RELEASE` resets the user-activity timer on
release, but the behaviour does not match either the code's intent or `SLEEP_WAKE_FIXES.md`,
and it is fragile across manufacturer power-management implementations.

**Fix:** let the timeout expire on its own, or hold the lock until the activity has actually
reported itself visible.

---

### 9. One ExoPlayer instance per ViewHolder

`ui/SlideshowAdapter.kt` builds a full `ExoPlayer` in each `SlideshowViewHolder`. ViewPager2
keeps offscreen pages alive, so up to three decoder instances can exist at once. On a 1 GB
tablet with a single hardware decoder, this is a meaningful risk of OOM and of decoder
contention.

**Fix:** use a single shared player, attached to whichever `PlayerView` is currently on
screen.

---

### 10. `READ_EXTERNAL_STORAGE` is requested but never used

`AndroidManifest.xml:5`. The app reads media exclusively through the Storage Access
Framework, which grants access per-folder and needs no storage permission at all.

Declaring it costs a scary permission prompt on Android 6–12, triggers extra Google Play
data-safety review, and buys nothing.

**Fix:** remove the line. If it turns out to be needed for legacy paths on API 22–18, scope
it with `android:maxSdkVersion`.

---

## 🟡 Minor

### 11. Glide decodes images larger than any target screen

`ui/SlideshowAdapter.kt:84` caps decoding at 2048×2048. A typical old tablet is 1280×800.
That is roughly four times more pixels than can ever be displayed, held in memory, on the
devices least able to afford it.

**Fix:** size to the actual display, or let Glide size to the `ImageView` automatically.

---

### 12. Two preferences are stored but never read

`transitionEffect` and `clockStyle` (`settings/PhotoframePreferences.kt:46–52`) are
persisted and have no consumer anywhere in the codebase. The transition-effect and
analog-clock features described in the old documentation were never implemented.

Both are on the roadmap. Until then they are dead code that implies features that do not exist.

---

### 13. `FavoritesManager` contradicts the project's own persistence rule

`data/FavoritesManager.kt:75` uses `.apply()`, while `PhotoframePreferences` deliberately
uses `.commit()` everywhere — a decision the old docs called out as critical, because an
asynchronous write can be lost if the device is cut off mid-write. A photo frame that loses
power unexpectedly is the normal case, not the edge case.

Favourites are also serialised as a single `|||`-delimited string rather than a `StringSet`,
which is fragile and unnecessary.

**Fix:** use `putStringSet` and `.commit()`.

---

### 14. `androidx.documentfile` is used but not declared

`data/PhotoRepository.kt:6` imports `androidx.documentfile.provider.DocumentFile`, but the
dependency appears nowhere in `app/build.gradle.kts`. It currently resolves transitively
through another library, so the build succeeds — until that library changes its own
dependencies and the build breaks for no visible reason.

**Fix:** declare `androidx.documentfile:documentfile` explicitly.

---

### 15. Rotation reloads everything

There is no `configChanges` handling and no `screenOrientation` lock. Rotating the tablet
destroys and recreates the activity, which reloads and reshuffles the folder and loses your
position.

For a wall-mounted frame this may be best solved by simply locking orientation, with a
setting to choose which.

---

### 16. Backup restores a folder permission that cannot exist

`android:allowBackup="true"` backs up all SharedPreferences, including the saved SAF tree
URI. Restored onto a different device, that URI points at nothing and the app has no
persisted permission for it, so it starts in a broken state rather than showing the folder
picker.

**Fix:** exclude the gallery URI key from `backup_rules.xml`, or validate the URI on startup
and fall back to the picker.

---

## ⚪ Cleanup

### 17. Three unused dependencies

`app/build.gradle.kts:57`, `:58`, `:61` declare Retrofit, the Gson converter, and DataStore.
All are leftovers from the weather feature, which was removed. Nothing imports them.

They inflate the APK and the method count for zero benefit — on a device where both matter.

---

### 18. ExoPlayer2 is deprecated

The build emits ten deprecation warnings. `com.google.android.exoplayer2` is end-of-life and
superseded by `androidx.media3`. It still works, but receives no fixes.

**Fix:** migrate to `androidx.media3:media3-exoplayer` + `media3-ui`. Media3 supports API 21+,
so the `minSdk 22` floor is not an obstacle.

---

### 19. No tests

`app/build.gradle.kts` configures JUnit, Espresso, and an instrumentation runner, and there
is not a single test file. There is no `src/test/` or `src/androidTest/` directory at all.

The highest-value targets are the pure-logic pieces that need no device:
`PhotoframePreferences` time parsing, `getMediaType()` extension matching, and the favourite
weighting algorithm.

---

### 20. `SCHEDULE_EXACT_ALARMS` will attract Play Store scrutiny

Not a code defect, but worth recording. Google Play restricts this permission to apps whose
core function is alarms, timers, or calendars. RetroFrame does have a genuine alarm feature,
so a declaration is defensible — but expect to justify it during review.

The wake/sleep *schedule* is a different matter: it does not need second-level precision and
would be better served by an inexact alarm, leaving the exact-alarm permission to be
justified solely by the alarm clock feature. See [PUBLISHING.md](PUBLISHING.md).

---

## Documentation debt (resolved)

The repository previously contained seven Markdown files (`PROJECT_COMPLETE.md`,
`FINAL_STATUS.md`, `IMPLEMENTATION_SUMMARY.md`, and others) describing features that do not
exist in the code — a weather widget, `SettingsMonitor.kt`, `UriUtils.kt`, transition
effects, analog clock faces, and custom photo naming. Several contradicted each other about
whether the alarm feature was 0%, 80%, or 100% complete.

These have been removed and replaced by [ARCHITECTURE.md](ARCHITECTURE.md), which documents
only what the code actually does. The full history remains in git.
