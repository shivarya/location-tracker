---
name: tracking-dev
description: Build, install and drive the native SpeedTrack (Location Tracker) app locally — Gradle, emulator, simulated GPS routes, the Expo-import test. Use to run/test tracking-app locally.
---

Run the native SpeedTrack app (`android/`) for development.

## Setup (once per machine)

- JDK 17, Android SDK (`D:\Android_SDK` here). `android/local.properties`: `sdk.dir=…` and `MAPS_API_KEY=…` (the
  Google Maps Android key; it was in the Expo app's `mobile/.env`). Without it the build warns and all maps use
  MapLibre / OpenFreeMap.
- `android/debug.keystore`: copy of the standard React Native debug key (`mobile/android/app/debug.keystore`), so
  the Maps key restriction for debug builds keeps working.

## Build, test, install

```powershell
cd "c:\Users\Ash\Documents\Projects\apps\tracking-app\android" ; .\gradlew.bat :app:testDebugUnitTest
cd "c:\Users\Ash\Documents\Projects\apps\tracking-app\android" ; .\gradlew.bat :app:installDebug
```
Emulator: root `scripts/start-emulator.ps1`, or `D:\Android_SDK\emulator\emulator.exe -avd Medium_Phone_API_36.1`.
If a Gradle build stalls with little free RAM, `gradlew --stop` and retry.

## Drive it

- Grant permissions without dialogs: `adb shell pm grant com.shivarya.locationspeedtracker android.permission.ACCESS_FINE_LOCATION` (also `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS`, `ACTIVITY_RECOGNITION`).
- Simulated GPS (emulator): `adb emu geo fix <lon> <lat> <altitude> <satellites> <speed-knots>` — one fix per command;
  loop it from a script for a route. Fix timing over adb is uneven, so don't judge the perf timer on an emulator.
- Offline: `adb shell cmd connectivity airplane-mode enable|disable` (maps switch to downloaded MapLibre areas).
- Quick Settings tile: `adb shell cmd statusbar add-tile com.shivarya.locationspeedtracker/.widget.TrackingTileService`.
- HUD: `adb shell am start -n com.shivarya.locationspeedtracker/.MainActivity --es open hud`.
- Git Bash mangles `/data/...` and `/sdcard/...` paths: prefix commands with `MSYS_NO_PATHCONV=1`.

## Test the Expo import on a debug build

1. Build an `RKStorage` SQLite file with table `catalystLocalStorage(key TEXT PRIMARY KEY, value TEXT NOT NULL)` and
   keys `tracking_sessions` (JSON array of the Expo `TrackingSession`s — make it > 2 MB to exercise the chunked
   reader) and `unit_preference` (`imperial`).
2. `adb shell pm clear com.shivarya.locationspeedtracker`, `adb push RKStorage /data/local/tmp/`, then
   `adb shell "run-as com.shivarya.locationspeedtracker sh -c 'mkdir -p databases && cp /data/local/tmp/RKStorage databases/'"`.
3. Launch; logcat `LegacyMigration: Legacy migration done (N sessions imported, …)`.

## Notes

- Architecture, gotchas and where things live: `tracking-app/CLAUDE.md`.
- Room schema is v1 and frozen once 2.0.0 ships — add a migration for any entity change after that. Before release,
  a schema change on a dev device needs `adb uninstall` (or clear data).
- The deprecated Expo app (`mobile/`) is not for development; see the end of `tracking-release` for a rollback build.
