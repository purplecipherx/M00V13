# M00V13 Android TV appliance

This directory contains the native Android TV shell for M00V13.

## Current implemented foundation

- TV/Leanback launcher activity that can be selected as HOME
- purple low-overhead programmatic UI
- cow + popcorn launcher/loading artwork
- Media3/ExoPlayer playback
- per-profile preferred language
- preferred audio first; preferred subtitles only when preferred audio is unavailable
- profile-local watched/progress state
- hard internal-storage reserve and critical-space thresholds
- watched-first emergency download cleanup
- predictive artwork cache scoring and eviction
- release shrinking enabled

## Build environment

- JDK 17
- Android SDK / compileSdk 37
- Android Gradle Plugin 9.3.1
- Media3 1.11.0

Open `android/` in Android Studio or build with a compatible Gradle 9.5.x installation once the Android SDK is installed.

## Device install

Development install flow:

```text
adb connect <device-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation choose M00V13 as the HOME/launcher app when Android prompts. Do not disable Google TV packages until the exact package list from the target Xiaomi device has been audited.

## Next implementation slices

1. source/search bridge to the M00V13 scraper engine
2. movie/series metadata provider and real home rows
3. download queue with resumable transfers and per-series next-N automation
4. persistent download catalog and cleanup history
5. artwork fetcher backed by predictive cache metadata
6. source-selection screen with audio language/codec/HDR/debrid-cache labels
7. recommendation scorer per profile
8. pre-resolve/failover for Next Up
9. HDMI/audio capability probing and passthrough policy
10. Xiaomi package audit + safe debloat profile

The Android process should remain small: prefer platform APIs and Media3 over large frameworks, and measure RAM/storage on the actual 2 GB / 8 GB target before adding dependencies.
