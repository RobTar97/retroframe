# Getting started

Two routes to the same place. Take the first one unless you have a reason not to.

- **[The simple way](#the-simple-way)** — a cable, a folder, four taps. No technical knowledge.
- **[The technical way](#the-technical-way)** — adb, building from source, how the folder
  access actually works.

Not sure which tablet to use? See **[Choosing a tablet](CHOOSING_A_TABLET.md)**.

---

# The simple way

## Step 1 — Get your photos onto the tablet

Pick whichever of these suits you. **Put the photos in the `Pictures` folder** — that is where
RetroFrame looks first, and Android is happy for apps to read it.

### With a USB cable (most common)

1. Plug the tablet into your computer with a USB cable.
2. On the tablet, a notification appears — something like *"Charging this device via USB"*.
   **Tap it**, then choose **File transfer** (sometimes called *MTP* or *Transferring files*).
   Without this the computer sees a charging device and no files.
3. The tablet now appears on your computer like a USB stick.
   - **Windows** — open *This PC*, your tablet is listed there
   - **Mac** — you need [Android File Transfer](https://www.android.com/filetransfer/); macOS
     cannot do this on its own
   - **Linux** — it appears in your file manager
4. Open **Internal storage → Pictures**.
5. **Make a new folder** inside it — call it `Frame`, or `Photos`, or whatever you like — and
   copy your pictures into it.
6. Eject the tablet safely and unplug it.

> **Why a folder inside Pictures?** Not required, but tidier: RetroFrame shows *every* image
> in whichever folder you choose, so a dedicated folder means you decide exactly what appears.

### With an SD card

If your tablet has a microSD slot, this is the easiest option of all — and it means you can
change the photos later without touching the tablet.

1. Put the card in your computer, copy your photos onto it into a folder.
2. Put the card in the tablet.
3. When you choose the folder in step 3 below, look for the card's name in the picker's
   left-hand menu (tap the ☰ icon) rather than *Internal storage*.

### Straight from the tablet

If the photos are already in an email, a chat, or Google Photos, just save them on the tablet
as you normally would. They will land in `Pictures` or `Download`.

⚠️ **If they land in `Download`, move them into `Pictures`.** Android does not let any app
read the Downloads folder — see [the folder Android refuses](#the-folder-android-refuses).

---

## Step 2 — Install RetroFrame

1. On the tablet, open the browser and go to
   **[the latest release](https://github.com/RobTar97/retroframe/releases/latest)**.
2. Download the file ending in **`.apk`**.
3. Open it. Android will say it *"can't install unknown apps"* — that is normal for anything
   not from the Play Store. Tap **Settings**, turn on **Allow from this source**, then go back
   and tap **Install**.
4. Open RetroFrame.

---

## Step 3 — Point it at your photos

RetroFrame explains this on first launch, then opens Android's folder browser for you.

1. It opens **inside `Pictures`** already.
2. Tap the folder you made (e.g. `Frame`) to open it. **You must be inside the folder, not
   looking at it from outside.**
3. Tap **USE THIS FOLDER** at the bottom.
4. Tap **ALLOW**.

Your photos start immediately. That is the whole setup.

### The folder Android refuses

If you see:

> **Can't use this folder**
> To protect your privacy, choose another folder

…you are trying to select either the **top level of your storage** or the **Downloads
folder**. Android blocks both for every app, and there is nothing RetroFrame can do about it.

**The fix:** open a folder *inside* it, or move your photos into `Pictures`.

Verified on Android 15:

| Folder | Can you choose it? |
|---|---|
| Top level of storage | ❌ Refused |
| `Download` | ❌ Refused |
| `Pictures` | ✅ Yes |
| `DCIM` | ✅ Yes |
| `Documents` | ✅ Yes |
| Any folder you create | ✅ Yes |

On Android 10 and older none of these restrictions exist and anything can be chosen.

---

## Step 4 — Make it a photo frame

Tap the screen to bring up the controls, then the ⚙ icon.

| Setting | Suggestion |
|---|---|
| **Seconds per photo** | 30–60 for something you live with. 10 is restless. |
| **Shuffle order** | On, unless your photos are numbered and you want them in order. |
| **Wake time** | When you get up, e.g. `07:00`. |
| **Sleep time** | When you go to bed, e.g. `23:00`. Saves the battery and the screen. |
| **Start automatically after reboot** | On. Power cuts happen. |

Then plug it in, put it on a shelf, and forget about it.

### Adding photos later

Copy more into the same folder. RetroFrame notices within a few minutes on its own — or tap
the screen and hit the ↻ button to pick them up immediately.

---

## When something is wrong

| What you see | What it means |
|---|---|
| **"That folder has no photos or videos in it"** | You chose a folder that only *contains* the folder with the pictures. RetroFrame reads one folder, not the ones inside it. Choose the folder the photos are actually in. |
| **"Can't use this folder"** | Storage root or Downloads. See [above](#the-folder-android-refuses). |
| **Screen turns off by itself** | *Keep screen on during slideshow* is off in settings, or the sleep schedule has started. |
| **Wakes a minute late** | Expected. RetroFrame uses relaxed alarms so the tablet can sleep properly. Only the morning alarm is exact. |
| **Wakes to the lock screen instead of photos** | Turn the lock screen off: Settings → Security → Screen lock → None. |
| **Videos are silent** | Deliberate. Turn on *Play video sound* in settings. |
| **Tablet is hot** | Turn the brightness down and use the sleep schedule. See [Choosing a tablet](CHOOSING_A_TABLET.md#living-with-it). |

---
---

# The technical way

## Install over adb

```bash
# Verify the download first
sha256sum retroframe-v0.3.0.apk
# compare against the checksum on the release page

adb install -r retroframe-v0.3.0.apk
```

Push some photos and launch:

```bash
adb shell mkdir -p /sdcard/Pictures/Frame
adb push ~/photos/*.jpg /sdcard/Pictures/Frame/
adb shell am start -n com.rober.photoframe/.MainActivity
```

The folder still has to be granted through the system picker — see below for why that cannot
be scripted.

## Build from source

Requires JDK 17 and the Android SDK (API 36 platform).

```bash
git clone https://github.com/RobTar97/retroframe.git
cd retroframe
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the application ID `com.rober.photoframe.debug`, so they install alongside a
release build rather than replacing it.

For a signed release build, create `keystore.properties` in the repository root:

```properties
storeFile=retroframe-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Then `./gradlew assembleRelease`. Without that file the release build still works, it is just
unsigned and therefore not installable.

## How folder access actually works

RetroFrame declares **no storage permissions at all**. It reads media through the Storage
Access Framework: `ACTION_OPEN_DOCUMENT_TREE` returns a tree URI, and
`takePersistableUriPermission` makes that grant survive reboots.

Consequences worth knowing:

- **The grant cannot be scripted.** There is no `adb` command and no permission to grant with
  `pm grant`; the user physically confirming it in the system picker *is* the security model.
- **The grant is per-folder**, not per-device. RetroFrame can read the folder you chose and
  nothing else on the tablet.
- **Grants are a limited resource.** Android caps how many persisted URI permissions an app
  may hold, so RetroFrame releases the old one whenever you change folders.
- **Restoring a backup does not restore the grant.** The saved URI is validated against
  `contentResolver.persistedUriPermissions` on startup, and the app falls back to the picker
  if the grant is gone — which also covers a revoked permission or a removed SD card.

The picker is launched with `EXTRA_INITIAL_URI` pointing at
`content://com.android.externalstorage.documents/document/primary%3APictures`, which is why it
opens somewhere selectable instead of on a refusal. That hint is honoured from API 26; older
versions ignore it and are unrestricted anyway.

## How scanning works

One `ContentResolver.query()` against
`DocumentsContract.buildChildDocumentsUriUsingTree(...)`, projecting document ID, display
name, MIME type, last-modified and size in a single pass.

The obvious `DocumentFile.listFiles()` costs an IPC round trip *per file per property* —
roughly 1,500 calls for a 500-photo folder — which is why it is not used. Details in
[ARCHITECTURE.md](ARCHITECTURE.md).

Subfolders are **not** scanned; only the folder you choose. Recursive scanning is on the
roadmap.

## Watching the logs

```bash
adb logcat | grep -E "PhotoRepository|FolderMonitor|DailySchedule|AlarmScheduler|AlarmCompat"
```

Testing the schedule without waiting overnight: set wake and sleep two minutes apart and
watch. Both use inexact alarms, so expect up to a minute of drift — that is deliberate, so the
tablet can stay asleep.

## Supported media

| | |
|---|---|
| Images | JPEG, PNG, GIF, BMP, WebP, HEIC/HEIF |
| Video | MP4, M4V, MKV, WebM, AVI, MOV, 3GP |

Detection prefers the MIME type reported by the storage provider and falls back to the file
extension, because some older document providers report `application/octet-stream` for
everything.

Whether a given video actually plays depends on the hardware decoder in your tablet, not on
RetroFrame. A file the device cannot decode is skipped rather than stalling the slideshow.
