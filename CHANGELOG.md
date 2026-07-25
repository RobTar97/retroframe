# Changelog

All notable changes to RetroFrame are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Open source project scaffolding: GPL-3.0 license, contribution guide, code of conduct,
  security policy, issue and pull request templates, and GitHub Actions CI
- `docs/ARCHITECTURE.md` — an accurate description of how the app is actually built
- `docs/KNOWN_ISSUES.md` — a full, honest inventory of current defects
- `docs/PUBLISHING.md` — distribution options and their tradeoffs
- Fastlane metadata for F-Droid and Google Play listings

### Changed
- Renamed the app to **RetroFrame**. The application ID stays `com.rober.photoframe`,
  since it can never change once published.

### Removed
- Seven stale status documents that described features which were never implemented
  (weather widget, `SettingsMonitor`, `UriUtils`, transition effects, analog clock) and
  which contradicted each other on whether the alarm feature was complete. The content is
  preserved in git history and superseded by `docs/ARCHITECTURE.md`.
- Committed build logs (`build_log.txt`, `build_error.txt`, `build_error2.txt`)

---

## Pre-history

Development before the project was opened up is not versioned. The initial git import
captures the codebase as it stood at that point. In summary, the app at that time could:

- Display a slideshow of photos and videos from a folder chosen through the Storage
  Access Framework
- Play videos with ExoPlayer, muted by default, advancing when playback ends
- Mark photos as favourites, weighted to appear roughly 3× as often
- Switch between photo mode and a fullscreen clock mode
- Turn the screen on at a set wake time and drop to clock mode at a set sleep time
- Fire a daily morning alarm with a notification and the system alarm sound
- Restart itself after a reboot

No release was ever published.

[Unreleased]: https://github.com/YOUR-GITHUB-USERNAME/retroframe/commits/main
