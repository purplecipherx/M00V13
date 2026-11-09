# M00V13 standalone Android TV APK

This directory is the installable **standalone native Android application** for M00V13. It does not run inside Kodi and requires no Kodi installation or Python runtime.

## Current implemented foundation

- TV/Leanback launcher activity that can be selected as HOME
- purple low-overhead programmatic UI
- cow + popcorn launcher/loading artwork
- first-class Real-Debrid integration: official device-code login, secure token storage, cached-source probing, and magnet resolution
- native source discovery with tiered provider search and build-time-compiled indexer definitions
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

## Verification and next improvements

1. Validate the APK build and installation on target Android/Google TV devices.
2. Smoke-test Real-Debrid device authorization, cached-source ranking, magnet resolution, and playback.
3. Exercise native provider/indexer compatibility and tiered search against permitted endpoints.
4. Test downloads, resume, profile isolation, and cleanup at low available storage.
5. Benchmark RAM, storage, remote responsiveness, video codecs, and HDMI/audio behavior on the Xiaomi TV Stick 4K (2nd Gen).
6. Improve remaining behavior based on observed device and playback results rather than duplicating features already implemented.

The Android process should remain small: prefer platform APIs and Media3 over large frameworks, cache only likely-near-future content, and measure RAM/storage on the actual 2 GB / 8 GB target before adding dependencies.
