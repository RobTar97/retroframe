# Sleep/Wake Timer Fixes - Implementation Summary

## Problem Identified

The sleep/wake timers were not working because:

1. **FLAG_KEEP_SCREEN_ON was permanently enabled** - This prevented the device from ever sleeping
2. **No WakeLock to wake device** - Sleep alarm couldn't actually wake the device or turn the screen on
3. **Sleep mode didn't turn screen off** - Just switched to clock mode but screen stayed on
4. **Missing MODE parameter in wake intent** - Wake alarm didn't explicitly set photo mode

## Changes Implemented

### 1. MainActivity.kt - Conditional Screen Management

**Key Changes:**
- ❌ **Removed:** Permanent `FLAG_KEEP_SCREEN_ON` flag from `onCreate()`
- ✅ **Added:** `enableKeepScreenOn()` - Enables screen keep-on for photo mode
- ✅ **Added:** `disableKeepScreenOn()` - Disables screen keep-on for clock/sleep mode
- ✅ **Updated:** `switchToClockMode()` - Now disables screen keep-on
- ✅ **Updated:** `switchToPhotoMode()` - Now enables screen keep-on
- ✅ **Updated:** `onCreate()` - Conditionally sets screen state based on mode
- ✅ **Updated:** `onNewIntent()` - Handles both "CLOCK" and "PHOTO" mode intents

**Behavior:**
- Photo mode: Screen stays on indefinitely (FLAG_KEEP_SCREEN_ON enabled)
- Clock mode: Screen can turn off naturally (FLAG_KEEP_SCREEN_ON disabled)

### 2. ScreenManager.kt - WakeLock Implementation

**Key Changes:**
- ✅ **Added:** PowerManager import
- ✅ **Added:** `WAKELOCK_TAG` constant for WakeLock identification
- ✅ **Updated:** `ACTION_WAKE_UP` handler:
  - Acquires `SCREEN_BRIGHT_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP` 
  - Turns on screen even if device is in deep sleep
  - Explicitly sets "PHOTO" mode in intent
  - Properly releases WakeLock after 3 seconds
- ✅ **Updated:** `ACTION_SLEEP` handler:
  - Added logging for better debugging
  - Explicitly sets "CLOCK" mode

**WakeLock Flags Used:**
- `SCREEN_BRIGHT_WAKE_LOCK` - Turns screen on at full brightness
- `ACQUIRE_CAUSES_WAKEUP` - Wakes device immediately when acquired
- `ON_AFTER_RELEASE` - Keeps screen on briefly after release

## How It Works Now

### Wake-Up Flow (e.g., 7:00 AM)
1. AlarmManager triggers at wake time
2. WakeLock is acquired → Device wakes up, screen turns on
3. MainActivity launches with MODE="PHOTO"
4. Photo mode starts → `enableKeepScreenOn()` called
5. Screen stays on for slideshow
6. WakeLock releases after 3 seconds (screen stays on due to FLAG_KEEP_SCREEN_ON)
7. Next day's alarms are rescheduled

### Sleep Flow (e.g., 11:00 PM)
1. AlarmManager triggers at sleep time
2. MainActivity receives intent with MODE="CLOCK"
3. `switchToClockMode()` called → `disableKeepScreenOn()`
4. Clock fragment displays
5. Screen can now turn off based on device timeout settings
6. Next day's alarms are rescheduled

## Permissions Required

Already present in AndroidManifest.xml:
- ✅ `android.permission.WAKE_LOCK` - Required for WakeLock functionality
- ✅ `android.permission.SCHEDULE_EXACT_ALARMS` - Required for exact alarm timing

## Testing Recommendations

1. **Set wake time** to 1-2 minutes in the future
2. **Set sleep time** to 5 minutes in the future
3. **Lock device** and wait for wake alarm
4. **Verify:** Device wakes up, screen turns on, photo slideshow starts
5. **Wait** for sleep alarm
6. **Verify:** Clock mode activates, screen can turn off when timeout expires
7. **Check logs** for "Wake up alarm triggered" and "Sleep alarm triggered" messages

## Notes

- Screen timeout during clock mode is controlled by device settings
- WakeLock is held for only 3 seconds to conserve battery
- FLAG_KEEP_SCREEN_ON keeps screen on during photo mode without needing continuous WakeLock
- Alarms reschedule automatically for the next day after firing
