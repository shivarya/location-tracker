# CLAUDE.md — SpeedTrack (Location Speed Tracker)

Guidance for Claude Code when working inside `tracking-app/`. GPS speed tracker: live speed, route recording,
sensors, fitness, performance timer. Play package `com.shivarya.locationspeedtracker` (listing "Location & Speed
Tracker"). Public GitHub repo `shivarya/location-tracker` — keep keys, keystores and infra identifiers out of it.

## Layout

| Path | What | Status |
|------|------|--------|
| `android/` | **Native Kotlin/Compose app** (2.0.0+) — the one that ships | Active |
| `mobile/` | Expo / React Native app (≤ 1.0.9) | **Deprecated** — kept for reference and a rollback build until native testing is done; then delete it |
| `branding/` | App logo masters (SVG) + rendered Play icon / feature graphic (`node branding/render.js mobile/node_modules`); see its README for the Android vectors to keep in sync | Active |
| `website/` + root `index.html` / `privacy.html` / `terms.html` / `styles.css` / `script.js` | Landing page + privacy policy (root copies are the gh-pages ones) | Active |
| `PRIVACY_POLICY.md`, `GOOGLE_PLAY_GUIDE.md`, `YOUTUBE_ASSETS.md` | Store / policy docs | Active |
| `location-tracker-release.keystore` (gitignored) | Upload key, alias `location-tracker-key`, SHA-1 `7E:80:9D:5F:97:14:DB:64:21:B8:52:53:40:1D:3C:01:09:D4:E8:E9` | Keep — lives at the repo root so deleting `mobile/` can't lose it |

## Claude Code skills (`.claude/skills/`)

- `tracking-dev` — build, install and drive the native app (emulator GPS simulation, legacy-import test).
- `tracking-release` — changelog, version bump, signed AAB, R8 smoke + upgrade tests, Play closed testing via
  `play-deploy`. Has a short "legacy Expo" section at the end for a rollback build.

## Native app (`android/`)

Toolchain copied from `diet-plan/android` (AGP 9.4, Kotlin 2.3, Compose BOM 2026.09 + Material 3 Expressive, Hilt,
Room, DataStore, WorkManager, type-safe Navigation). compileSdk 37 / targetSdk 36 / minSdk 26. Single `:app` module,
namespace = applicationId. No NDK/CMake (MapLibre ships prebuilt `.so`s, 16 KB aligned), so no MAX_PATH junction.

```powershell
cd "c:\Users\Ash\Documents\Projects\apps\tracking-app\android" ; .\gradlew.bat :app:testDebugUnitTest
cd "c:\Users\Ash\Documents\Projects\apps\tracking-app\android" ; .\gradlew.bat :app:installDebug
cd "c:\Users\Ash\Documents\Projects\apps\tracking-app\android" ; .\gradlew.bat :app:lintRelease :app:bundleRelease
```

Local, gitignored config in `android/`: `local.properties` (`sdk.dir`, `MAPS_API_KEY=` — copied from the Expo
`.env`), `keystore.properties` (→ `../location-tracker-release.keystore`), `debug.keystore` (the standard RN debug
key, so the Maps key restriction that covered Expo dev builds still applies). Without `MAPS_API_KEY` the build warns
and every map falls back to MapLibre. Version: `android/version.properties` (Expo 1.0.9 = versionCode 12; EAS's
remote counter is stale at 4 — always check Play Console's highest code).

### Architecture (`com.shivarya.locationspeedtracker`)

- `core/tracking/` — `TrackingService` (foreground service, type `location` + `connectedDevice` while a BLE sensor
  is connected; started only from a visible activity, widget or notification, which is why no background-location
  permission is needed), `TrackingEngine` (single-threaded recorder; batches points to Room every 5 s / 30 points;
  sticky-restart recovery), `PointFilter` (accuracy / interval / speed-spike / position-jump / acceleration gates,
  anchor-based jitter — port of the 1.0.7 `trackingQuality.ts` from the `master` branch, with its walking and
  >198 km/h bugs fixed), `StatsAccumulator` (moving time, confirmed max speed, elevation hysteresis), `SplitTracker`.
- **Add-ons** (`SessionAddon`, Hilt `@IntoSet`): `SensorRecorder` (G-force in the vehicle frame, lean, baro altitude
  calibrated to GNSS MSL, steps/cadence → `metrics` rows, 1 per second), `FitnessAddon` (calories, auto-pause),
  `HeartRateAddon` (BLE HR). `SessionFinishedHook`s run after save (Health Connect sync).
- `core/location/` — fused / GNSS-only sources (`FixMapper` adds MSL altitude via `core-location-altitude`),
  `GnssMonitor` (satellites, NMEA DOP, TTFF), `LocationDoctor` (GPS doctor checks + one-tap fixes),
  `NetworkMonitor`.
- `core/maps/` — `MapLibreSetup` (OpenFreeMap styles, UA + ≤4 parallel requests), `TileEstimator` (≤2 700 tiles /
  50 MB per area — OpenFreeMap's offline terms), `OfflineRegions` (light + dark region per area). Maps go through
  `feature/map/TrackMap`: Google Maps online, MapLibre when offline or keyless (`MapEnvironment`).
- `core/health/` — Health Connect write (idempotent `clientRecordId = "<session>/<kind>"`) and heart-rate backfill
  from watch apps (e.g. boAt via its companion app), `HealthSyncWorker` (now + again 3 h later).
- `core/migration/` — **Expo import**: reads `RKStorage` (`catalystLocalStorage`) in 500 k-char `substr()` chunks
  (the Expo app kept every session in one `tracking_sessions` value, easily > the 2 MB CursorWindow), replays the
  points through the native filter, imports `unit_preference`. `RKStorage` is left in place.
- `core/export/` — GPX/TCX/KML/CSV/JSON writers, GPX parser (SAX, no DTDs), `ImportManager`, ZIP `BackupManager`.
- `core/perf/PerformanceTimer` — pure state machine; launch extrapolated from the first two moving fixes;
  crossings interpolated; 1-ft rollout.
- `feature/*` — Compose screens (track, map, sensors, history, session, gps doctor, offline, perf, hr, health,
  profile, settings, hud); `widget/` — Glance widget, QS tile, `StartTrackingActivity` trampoline.
- Room DB `speedtrack.db` v1 (`app/schemas/`): sessions, track_points, laps, metrics, perf_runs. **Schema changes
  after 2.0.0 ships need a Room migration** (AutoMigration) — v1 is frozen once released.

### Gotchas

- **Play declarations** for this build: foreground-service types `location` and `connectedDevice` (with a video),
  Health apps declaration for the Health Connect permissions (approval needed before release), Data safety
  (location, health & fitness, Maps SDK), and remove the old background-location declaration. See
  `.claude/skills/tracking-release`.
- Maps key must be restricted to the package + debug / upload / Play app-signing SHA-1s, or Google maps render blank
  (MapLibre still works).
- Glance widget, QS tile and the shortcut trampoline are the only background start paths; a QS tile click is *not* an
  Android 14 while-in-use exemption, hence the trampoline activity.
- Health Connect sessions are end-exclusive: route points at exactly `endTime` make the insert throw.
- Emulator testing: `adb emu geo fix <lon> <lat> <alt> <sats> <knots>`; GNSS fixes from it arrive unevenly, so perf
  timer numbers on an emulator aren't meaningful. Git Bash needs `MSYS_NO_PATHCONV=1` for `adb shell` paths.

## Legacy Expo app (`mobile/`) — rollback only

Deprecated: no new features. To build it anyway: `cd mobile`, `npm install --legacy-peer-deps`, and the generated
`mobile/android/` (gitignored, hand-edited for signing; `keystore.play.properties` points at
`../../location-tracker-release.keystore`). `eas` commands run from `mobile/` (projectId in `mobile/app.json`).
