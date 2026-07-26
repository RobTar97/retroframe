# Which tablet should I use?

Almost certainly the one you already have. This page is for deciding whether the thing in
your drawer will work, and what to check before you commit an afternoon to it.

---

## The only three things that matter

**1. Android 5.1 or newer.**
Check **Settings → About tablet → Android version**. If it says 5.1, 6, 7, 8, 9 or anything
higher, RetroFrame runs. If it says 5.0 or 4.x, it will not — Android 5.1 is where the
Storage Access Framework arrived, which is how the app reads your folder without demanding
access to everything else on the device.

**2. It charges and holds a picture.**
The battery being tired does not matter — this thing lives on a charger. A cracked digitiser
in one corner does not matter much either; you need about four taps to set it up and none
after that. A dead backlight or a screen with large dead zones does matter.

**3. 1 GB of RAM or more.**
Below that Android itself struggles. RetroFrame is built for 1 GB and will use noticeably
less on anything newer.

Everything else — chip, brand, age, whether it still gets updates — is irrelevant. The
browser being too old for the modern web is exactly why the tablet is free for this.

---

## How to check, in one minute

1. **Settings → About tablet**
   - *Android version* — needs to be 5.1+
   - *Model number* — worth noting if you want to search for its specs
2. **Settings → Storage** — how much free space? A few hundred photos is well under 1 GB.
3. **Does it have a microSD slot?** If yes, that is the easiest way to get photos on and off.
4. **Plug it in and leave it an hour.** If it stays on and does not get alarmingly hot, it is
   a good candidate.

---

## Tablets people commonly have

These are the ones that turn up in drawers and are known to be in range. **Check your own
device's Android version rather than trusting this table** — the version shipped varied by
region, carrier and how far the owner took updates.

| Tablet | Usually ends up on | Notes |
|---|---|---|
| Nexus 7 (2013) | Android 6.0.1 | Excellent 1920×1200 screen, 2 GB RAM. One of the best options. |
| Nexus 10 | Android 5.1.1 | 2560×1600, 2 GB RAM. Big and very sharp. |
| Nexus 9 | Android 7.1.1 | 4:3 screen, which suits photos better than 16:10. |
| Samsung Galaxy Tab A 10.1 (2016) | Android 6 → 8 | Very common, 2 GB RAM, holds up well. |
| Samsung Galaxy Tab S2 | Android 6 → 7 | AMOLED — see the burn-in note below. |
| Amazon Fire HD 8 / 10 | Fire OS 5+ (Android 5.1+) | No Play Store, but sideloading the APK works normally. Cheap and abundant. |
| Lenovo Tab 4 / M10 | Android 7 → 8 | Plentiful secondhand. |

If yours is not listed, that means nothing — the checks above are what decide it.

### Worth avoiding

- **Anything on Android 5.0 or below.** Not supported, and it cannot be.
- **Tablets with under 1 GB of RAM** — some budget 7-inch models from 2014–2015. Android
  itself will be the bottleneck, not RetroFrame.
- **A tablet you still use.** RetroFrame wants to be the only thing running, fullscreen,
  permanently. It is a poor houseguest on a device with another job.

---

## Screen shape

Photos from a phone or camera are usually 4:3 or 3:2. Most tablets are 16:10.

RetroFrame never crops your photos — it fits them whole and lets the sides go black. That is
the right trade for a photo frame, but it means a **4:3 tablet shows a landscape photo larger
than a 16:10 tablet of the same size**. If you have a choice between two devices, the squarer
one is usually the better frame.

Portrait or landscape is up to you: stand it whichever way suits the photos you have most of.

---

## OLED and AMOLED screens

Some tablets (notably the Galaxy Tab S line) use AMOLED panels. Those can suffer **permanent
burn-in** when a static bright element sits in the same pixels for months — which is exactly
what a clock overlay on a photo frame does.

If your tablet is AMOLED:
- Use the sleep schedule so it is not displaying for 24 hours a day
- Keep brightness low
- Prefer a device with an LCD panel if you have the choice — LCDs do not burn in

Burn-in protection is on the roadmap; until it ships, the schedule is your protection.

---

## Living with it

**Heat is what kills these devices**, not use. A tablet permanently on charge with a bright
screen runs warm, and over months a warm lithium battery swells — which at best pops the
screen off its adhesive and at worst is a fire risk.

So:

- **Use the sleep schedule.** Twelve hours off a day roughly halves the heat.
- **Turn the brightness down.** Indoors you rarely need more than half, and it is the single
  biggest source of both heat and power draw.
- **Give it air.** Not face-down on a soft surface, not sealed in a tight frame.
- **Check it every few months.** If the back is bulging or the screen is lifting at an edge,
  the battery is swelling — stop using it and dispose of the battery properly.
- **If you are comfortable opening it**, many tablets run fine from the charger with the
  battery disconnected. That removes the risk entirely. Only do this if you know what you are
  doing; lithium cells are dangerous when punctured.

**Turn off the lock screen** (Settings → Security → Screen lock → None), or the wake schedule
will land on the lock screen instead of your photos.

---

## Making it look like furniture

The difference between "an old tablet on a shelf" and "a photo frame" is mostly cable
management.

- A cheap tablet stand costs very little and is better than anything improvised
- A **right-angle USB cable** stops the lead sticking straight out of the bottom
- Route the cable behind the shelf, not across it
- A slim photo frame with the mount cut out can hide the bezel entirely, if you are handy

---

## Still not sure?

Open an [issue](https://github.com/RobTar97/retroframe/issues/new?template=device_report.yml)
with the model and Android version. And if you do get one running, please report back — the
compatibility table above is built from what people tell us, and the project has no way to buy
this hardware to test it.
