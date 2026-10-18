---
name: tracking-release
description: Release a new SpeedTrack (Location Tracker) version to Google Play — the native Kotlin app in android/ — changelog, version bump, signed AAB, R8 smoke + upgrade tests, signing check, publish to closed testing via play-deploy. Use when shipping a tracking-app update.
---

Ship the native SpeedTrack app (`android/`, package `com.shivarya.locationspeedtracker`) to Google Play closed
testing. Production is always a manual, staged rollout in Play Console and needs the user's go-ahead.

## 1. Changelog and version

1. Add a `## [x.y.z] - YYYY-MM-DD` entry to `android/CHANGELOG.md` with Added / Changed / Fixed and a
   **Google Play Notes** block (≤ 500 chars, pasted verbatim as release notes).
2. Bump `android/version.properties` (`versionCode` +1, `versionName`). The versionCode must be above **every** code
   Play has seen — check Play Console → App bundle explorer. Lineage: Expo 1.0.9 = 12, native 2.0.0 = 13. EAS's remote
   counter (`eas build:version:get -p android` in `mobile/`) is stale (4) — ignore it.
3. Commit as `Release x.y.z (N): …` only when the user asks.

## 2. Build

```powershell
cd "c:\Users\Ash\Documents\Projects\apps\tracking-app\android" ; .\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:bundleRelease :app:assembleRelease
```
- AAB: `android/app/build/outputs/bundle/release/app-release.aab`; APK: `.../apk/release/app-release.apk`.
- Lint must report 0 errors.
- If `android/keystore.properties` is missing the build silently signs with the debug key — never publish that.
  Verify the signer:
  ```powershell
  keytool -printcert -jarfile "c:\Users\Ash\Documents\Projects\apps\tracking-app\android\app\build\outputs\bundle\release\app-release.aab"
  ```
  SHA-1 must be `7E:80:9D:5F:97:14:DB:64:21:B8:52:53:40:1D:3C:01:09:D4:E8:E9` (upload key
  `location-tracker-release.keystore` at the repo root, alias `location-tracker-key`).
- 16 KB page alignment (MapLibre ships native code): `zipalign -c -P 16 -v 4 app-release.apk` (build-tools) must
  pass for `lib/arm64-v8a` and `lib/x86_64`.
- Low RAM: if `minifyReleaseWithR8` stalls, stop the emulator / other Gradle daemons (`gradlew --stop`) and retry.

## 3. Test the release build (R8)

On a device or emulator (`adb` = `D:\Android_SDK\platform-tools\adb.exe`; in Git Bash prefix `MSYS_NO_PATHCONV=1`):
1. `adb uninstall com.shivarya.locationspeedtracker` (release and debug keys differ), `adb install -r app-release.apk`.
2. Smoke: `adb shell monkey -p com.shivarya.locationspeedtracker 1`, then walk every screen (Track, Sensors, History,
   Settings, session detail, GPS doctor, offline maps, perf timer, HR sensor, Health Connect, backup, HUD) and record a
   short session (`adb emu geo fix <lon> <lat> <alt> <sats> <knots>` on an emulator). `adb logcat -d -b crash` must be
   empty — watch for R8 issues in Room, Hilt workers, kotlinx.serialization (navigation routes, export JSON), Glance,
   MapLibre and Health Connect.
3. Dark/light: `adb shell cmd uimode night yes|no`.

## 4. Upgrade test (Expo → native) — mandatory for the first native releases

1. Install the last Expo build with the **same key**: `build-artifacts/tracking-app-1.0.9-release.apk` (gitignored,
   at the repo root). Grant location, record one or two short sessions (Start → move → Stop), set Imperial.
2. `adb install -r android/app/build/outputs/apk/release/app-release.apk` over it.
3. Launch: logcat shows `LegacyMigration: Legacy migration done (N sessions imported…)`, History shows the sessions
   (tagged "from v1"), units stay Imperial, the RKStorage file is untouched.
4. Large histories: the chunked reader is unit-tested; to re-check on a debug build push a big `RKStorage` with
   `run-as` (see `tracking-dev`).

## 5. Publish to closed testing

```powershell
cd "c:\Users\Ash\Documents\Projects\apps\play-deploy" ; node bin/publish-to-play.js publish --app tracking-app --aab "c:\Users\Ash\Documents\Projects\apps\tracking-app\android\app\build\outputs\bundle\release\app-release.aab" --notes "<Google Play Notes>" --dry-run
```
Drop `--dry-run` only after the user confirms. `play-deploy share --app tracking-app --aab …` gives an Internal App
Sharing link for a Play-signed test install without touching any track.

## 6. Play Console — one-time items for 2.0.0 (user does these)

- **App content → Location permissions**: the bundle no longer requests `ACCESS_BACKGROUND_LOCATION`; remove the
  background-location declaration.
- **App content → Foreground service permissions**: declare `location` (live tracking with the screen off) and
  `connectedDevice` (keeping a Bluetooth heart-rate sensor connected during a session), each with a short demo video.
- **App content → Health apps**: declare the Health Connect permissions (write exercise, exercise route, distance,
  steps, total calories, elevation, speed, heart rate; read heart rate). Google reviews this — submit it before the
  release, or the release is rejected.
- **Data safety**: location (precise, app functionality, not shared), health & fitness (heart rate, steps/fitness
  info — on device / Health Connect), app activity none; follow Google's Maps SDK data-disclosure guidance; no data
  collected by the developer, no ads.
- **Privacy policy**: publish `privacy.html` (and `website/privacy.html`) to
  `https://shivarya.dev/location-tracker/privacy.html` (and gh-pages) before submitting.
- **Store listing**: new screenshots from the native app; upload the new icon and feature graphic from `branding/`
  (`play-icon-512.png`, `feature-graphic.png`; re-render with `node branding/render.js mobile/node_modules`). The root
  `play-store-assets` skill only regenerates the deprecated Expo app's icons.
- **Maps key**: in Google Cloud Console restrict the key to the package with the debug (`5E:8F:16:06:2E:A3:CD:2C:4A:0D:54:78:76:BA:A6:F3:8C:AB:F6:25`),
  upload (`7E:80:9D:…:E8:E9`) and Play app-signing SHA-1s (Play Console → App integrity).

## 7. Production

Manual, in Play Console: promote the tested closed-testing release to production with a staged rollout
(20% → 100%), only on the user's go-ahead. Once the native app is stable in production, delete `mobile/` (the keystore
lives at the repo root, so it is safe).

## Legacy Expo app — only for a rollback

`cd mobile ; npm install --legacy-peer-deps`, then build the generated `mobile/android/` locally
(`keystore.play.properties` → `../../location-tracker-release.keystore`) or with EAS from `mobile/`. Its versionCode
must also exceed the native one to roll back.
