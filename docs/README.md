# RetroFrame documentation

## If you want to use it

| | |
|---|---|
| **[Getting started](GETTING_STARTED.md)** | Setup, twice over: a simple path with no assumed knowledge, and a technical one with adb and build instructions. Includes how to copy photos across and what to do when Android refuses a folder. |
| **[Choosing a tablet](CHOOSING_A_TABLET.md)** | Whether the device in your drawer will work, how to check it in a minute, screen shape and burn-in, and how to run one for years without cooking the battery. |

## If you want to work on it

| | |
|---|---|
| **[Architecture](ARCHITECTURE.md)** | How the code is put together and why — the single-cursor folder scan, the shared video player, the advance timer, and the constraints that produced them. |
| **[Known issues](KNOWN_ISSUES.md)** | Every current defect, ranked, with a suggested approach for each. This is the to-do list. |
| **[Contributing](../CONTRIBUTING.md)** | How to get set up, what will and won't be merged, and why a device report counts as a contribution. |

## If you want to ship it

| | |
|---|---|
| **[Publishing](PUBLISHING.md)** | GitHub Releases, F-Droid, Google Play and their tradeoffs — plus why there is no iOS version and won't be. |
| **[Changelog](../CHANGELOG.md)** | What changed in each version. |
| **[Store listing metadata](../fastlane/README.md)** | Fastlane text and images for F-Droid and Play. |

---

## The short version of the design

RetroFrame targets a 2013–2018 tablet with 1 GB of RAM, slow storage and an Android version
between 5.1 and 9. Every architectural decision falls out of that one constraint, which is why
the project uses Views rather than Compose, no dependency-injection framework, no database, and
pins several libraries below their latest release.

It also makes no network requests at all. The `INTERNET` permission is absent from the
manifest, so this is enforced by the operating system rather than promised in a readme.
