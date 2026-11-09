# M00V13 — Standalone Android TV app

M00V13 is a **standalone Android/Google TV application (APK)** for movie and TV browsing, source discovery, and media playback. It is a native Android app written in Java/Kotlin: **Kodi is not required**, and M00V13 is not a Kodi add-on.

## Real-Debrid integration

**Real-Debrid is the app's specifically supported debrid service.** The Android app includes:
- Real-Debrid device-code authorization and account connection/disconnection.
- Token and credential storage using the app's secure storage component.
- Real-Debrid cached-source checks and source ranking.
- Real-Debrid-assisted magnet/source resolution and playback.

The app can display and process some direct sources without Real-Debrid; magnet playback requires a connected Real-Debrid account. Do not mistake the provider indexer engine for a replacement for the Real-Debrid integration.

## Native Android architecture

- Android TV / remote-friendly screens, search, catalog browsing, profiles, watch history, and recommendations.
- Native `TieredSearchEngine` and `NativeScraperEngine` for source discovery and ranking.
- Public movie/TV indexer definitions, compiled into Android JSON assets at **build time**; no Python runtime or Prowlarr server required on the device.
- Media3/ExoPlayer playback, source selection, playback resume and failover.
- Local download queue, resumable downloads, and storage-safety policies.
- Optional Android TV HOME/launcher integration.

Indexer synchronization and definition compilation use small Python **developer/build tools** in `tools/`; they are not Android runtime components.

## Build and installation

The installable Android project is in `android/`.

- JDK 17, Android SDK 36 and Build Tools 36.0.0.
- Open `android/` in Android Studio or build with a compatible Gradle 9.5.x installation.
- Run `gradle -p android :app:assembleDebug` after installing the Android SDK.
- Install `android/app/build/outputs/apk/debug/app-debug.apk` with `adb install -r`.

The `Android build` GitHub Actions workflow compiles a debug APK and, on a successful main-branch build, publishes `M00V13.apk` to the `test-build` release.

## Source indexer maintenance

The app consumes `android/app/src/main/assets/cardigann_providers.json`. To refresh the build-time source catalog:

```sh
python -m pip install -r requirements-dev.txt
python tools/sync_indexers.py
python tools/compile_cardigann_android.py
```

These tools curate compatible public English indexer definitions. M00V13 respects access controls and does not implement CAPTCHA solving or anti-bot bypass.

## More documentation

- [Android app and device setup](android/README.md)
- [Native architecture and source-search policy](docs/ARCHITECTURE.md)
- [Profiles, offline downloads and recommendations](docs/PROFILE_OFFLINE_RECOMMENDATIONS.md)
