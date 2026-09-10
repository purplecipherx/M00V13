# M00V13 Android TV appliance

This directory contains the native Android TV shell for M00V13.

## Current implemented foundation

- TV/Leanback launcher activity that can be selected as HOME
- purple low-overhead programmatic UI
- cow + popcorn launcher/loading artwork
- Media3/ExoPlayer playback
- per-profile preferred language
- preferred audio first; preferred subtitles only when preferred audio is unavailable
- profile-local watched/progress state and resume
- profile-local watchlist and lightweight tag affinity
- catalog-backed horizontal home rows for Continue Watching, Next Up, Recommended for You, and Because You Watched
- lightweight per-profile recommendation scorer with no local ML runtime
- source option model with quality/video/HDR/audio/language/subtitle/size/cache/seeder labels
- fresh resolved-source cache and D-pad source-selection screen
- automatic playback source failover while preserving the current timestamp
- hard internal-storage reserve and critical-space thresholds
- watched-first emergency download cleanup policy
- persistent offline queue and Downloads/storage screen
- next-N episode download preference and watched-download auto-delete preference
- resumable HTTP(S) direct-download engine with periodic storage-reserve checks
- predictive artwork cache scoring and eviction policy
- release shrinking enabled
- GitHub Actions debug-APK compile gate

## Build environment

- JDK 17
- Android SDK / compileSdk 36
- targetSdk 36
- Android Gradle Plugin 9.3.1
- Gradle 9.5.x
- Android SDK Build Tools 36.0.0
- Media3 1.11.0

Open `android/` in Android Studio or build with a compatible Gradle 9.5.x installation once Android SDK 36 is installed.

## Device install

Development install flow:

```text
adb connect <device-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installation choose M00V13 as the HOME/launcher app when Android prompts. Google TV package debloating is intentionally postponed; it is not part of the current app build work.

## Next implementation slices

1. native Android search bridge to the M00V13 tiered scraper engine without embedding a Python runtime
2. movie/series metadata provider and Search/Movies/TV screens
3. background/foreground download execution service around the resumable transfer engine
4. automatic next-N episode and collection/trilogy queue scheduler
5. persistent completed-download catalog and cleanup history
6. actual artwork network fetcher + cache index + home-card images
7. pre-resolve scheduler for Next Up and a small backup-source window
8. HDMI/audio capability probing and passthrough policy
9. full profile picker/editor UI and optional profile PIN/content policy
10. device RAM/storage/playback benchmark pass on the Xiaomi TV Stick 4K (2nd Gen)

The Android process should remain small: prefer platform APIs and Media3 over large frameworks, cache only likely-near-future content, and measure RAM/storage on the actual 2 GB / 8 GB target before adding dependencies.
