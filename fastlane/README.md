# Store listing metadata

Listing text for [F-Droid](https://f-droid.org) and Google Play, in the standard
[fastlane supply](https://docs.fastlane.tools/actions/supply/) layout. F-Droid reads this
directory directly from the repository, so keeping it accurate here is all that's required
for that channel.

```
fastlane/metadata/android/en-US/
├── title.txt              max 30 chars (Play limit)
├── short_description.txt  max 80 chars (Play limit)
├── full_description.txt   max 4000 chars
├── changelogs/
│   └── <versionCode>.txt  max 500 chars — filename must match versionCode, e.g. 1.txt
└── images/
    ├── icon.png                512x512
    ├── featureGraphic.png      1024x500 (Play only)
    ├── phoneScreenshots/       1.png, 2.png, ...
    ├── sevenInchScreenshots/   ← the important ones for this app
    └── tenInchScreenshots/     ← and these
```

## Still needed

- [ ] `images/icon.png` — 512×512, derived from `art/icon-source.png`
- [ ] `images/featureGraphic.png` — 1024×500, required by Play
- [ ] Screenshots. **Take these on a real tablet**, not an emulator — the point of the app
      is how it looks on old hardware, and a pristine emulator render undersells it.
      Prioritise `sevenInchScreenshots` and `tenInchScreenshots`.
- [ ] `changelogs/<versionCode>.txt` for each release

Good screenshots to capture: the slideshow with a real photo, the controls overlay visible,
clock mode, and the settings dialog.

## Formatting

F-Droid and Play both accept a small subset of HTML in `full_description.txt`: `<b>`, `<i>`,
`<u>`, `<br>`. Markdown is **not** supported. Blank lines become paragraph breaks.

Keep the description honest about the project being early — see the last section of the
current text. Overpromising in a store listing is how you get one-star reviews from people
who expected a finished product.

## Adding a language

Copy `en-US/` to a new locale directory (`de-DE/`, `pl-PL/`, ...) and translate. Screenshots
can be shared or localised. Translations are very welcome — see
[CONTRIBUTING.md](../CONTRIBUTING.md).
