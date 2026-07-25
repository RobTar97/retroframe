<!--
Thanks for contributing to RetroFrame.
Please read CONTRIBUTING.md if you haven't — especially the "will not be merged" list.
-->

## What does this change?

<!-- A short description. If it fixes an issue, write "Fixes #123". -->

## Why?

<!-- The motivation. If this fixes an entry in docs/KNOWN_ISSUES.md, say which one. -->

## How was it tested?

<!--
Real hardware matters more than anything else in this project. Emulators don't reproduce
slow storage, thermal throttling, or manufacturer power management.
-->

- **Device(s):**
- **Android version(s):**
- **What I verified:**

<!--
No old tablet available? Say so — that's fine, and a maintainer or another contributor
can verify. Just be upfront rather than silent.
-->

## Checklist

- [ ] `./gradlew assembleDebug` passes
- [ ] `./gradlew lint` passes without new warnings
- [ ] Tested on a real device (or explained above why not)
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] If this fixes a documented issue, the entry has been removed from `docs/KNOWN_ISSUES.md`
- [ ] Documentation updated if behaviour changed

### Project constraints

- [ ] No new network calls, analytics, or telemetry
- [ ] No dependency on Google Play Services
- [ ] `minSdk 22` still builds and runs
- [ ] Any new SharedPreferences writes use `.commit()`, not `.apply()`
- [ ] New dependencies are justified below, or none were added

<!-- If you added a dependency, note what it is and why it's worth the APK size here. -->

## Screenshots / recordings

<!-- For UI changes. Photos of an actual tablet screen are perfectly acceptable. -->
