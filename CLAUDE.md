# CLAUDE.md — SpeedTrack (Location Speed Tracker)

Guidance for Claude Code when working inside `tracking-app/`. GPS speed tracker: live speed, route recording,
sensors, fitness, performance timer. Play package `com.shivarya.locationspeedtracker` (listing "Location & Speed
Tracker"). Public GitHub repo `shivarya/location-tracker` — keep keys, keystores and infra identifiers out of it.

## Layout

| Path | What | Status |
|------|------|--------|
| `android/` | **Native Kotlin/Compose app** (2.0.0+) — the one that ships; 2.0.7 (versionCode 21) on Play closed testing since 2026-10-31 | Active |
| `mobile/` | Expo / React Native app (≤ 1.0.9) | **Deprecated** — kept for reference and a rollback build until native testing is done; then delete it |
| `branding/` | App logo masters (SVG) + rendered Play icon / feature graphic (`node branding/render.js mobile/node_modules`); see its README for the Android vectors to keep in sync | Active |
| `website/` + root `index.html` / `privacy.html` / `terms.html` / `styles.css` / `script.js` | Landing page + privacy policy (root copies are the gh-pages ones) | Active |
| `PRIVACY_POLICY.md`, `GOOGLE_PLAY_GUIDE.md`, `YOUTUBE_ASSETS.md` | Store / policy docs | Active |
| `location-tracker-release.keystore` (gitignored) | Upload key, alias `location-tracker-key`, SHA-1 `7E:80:9D:5F:97:14:DB:64:21:B8:52:53:40:1D:3C:01:09:D4:E8:E9` | Keep — lives at the repo root so deleting `mobile/` can't lose it |

## Claude Code skills (`.claude/skills/`)

- `tracking-dev` — build, install and drive the native app (emulator GPS simulation, legacy-import test, testing on
  the user's phone).
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
and every map falls back to MapLibre. Version: `android/version.properties` (Expo 1.0.9 = versionCode 12; 13 is
used up by a Play draft; native 2.0.0 = 14, 2.0.1 = 15, 2.0.2 = 16, 2.0.3 = 17, 2.0.4 = 18, 2.0.5 = 19, 2.0.6 = 20, 2.0.7 = 21, so the next release is 22. EAS's remote counter is stale at 4 — always check
Play Console's highest code).

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
  50 MB per area — OpenFreeMap's offline terms; per-zoom sizes are measured gzip bytes, low-zoom tiles are ~1 MB each), `OfflineRegions` (light + dark region per area; each area has a `MapDetail` — Streets z14, Roads z11,
  Overview z7, so a whole country fits for flights). Maps go through
  `feature/map/TrackMap`: Google Maps online, MapLibre when offline or keyless (`MapEnvironment`).
- `core/flight/` — **Flight activity** (2.0.5): `FlightLog` (pure; takeoff = 2 fixes ≥ 55 m/s, touchdown = 2
  positionally plausible fixes < 35 m/s, or one slow fix after a > 2 min dropout unless a fast one follows; phases from
  GNSS vertical speed over ~30 s) and `FlightAddon` (live summary; replays `live.track` after a sticky restart). Saved
  flights rebuild the summary from points — nothing extra is stored. Flights force the GNSS source, accuracy ≥ 100 m,
  no barometer (cabin pressure), no auto-pause, no auto-splits. `AirborneDetector` (2.0.6): ≥ 125 m/s Doppler for 20 s
  in any other activity → `TrackingEngine.switchActivity` re-opens the session as a Flight in place (same path as a
  sticky restart, but the live state never goes idle, so the service keeps running).
  `FlightReplayTest` runs a real exported flight through `SessionRecalculator` when `SPEEDTRACK_FLIGHT_JSON` points at
  one (none is checked in — exports are location history).
- `core/tracking/SessionRecalculator.kt` (2.0.7) — session ⋮ → **Recalculate stats**: replays a saved session's points
  through the current filter (before → after preview, then `SessionRepository.applyRecalculation` rewrites points'
  speed/distanceM, the auto laps and the stats row). Stored speed ~0 between points ≥ 20 m/s apart = an old re-anchored
  fix, so it gets the derived speed back; airliner cruise → Flight; elevation is kept as recorded (may be barometric).
  This is how pre-2.0.6 sessions get repaired — a filter change never rewrites history by itself.
- `core/sync/` (2.0.8) — **journey names + Google Drive sync**, one `JourneySyncWorker` (network required, KEEP +
  re-check `pending()` at the end so its own renames don't restart it; 12 h periodic catch-up), triggered by
  `JourneySyncTrigger` (started in `SpeedTrackApp`) on any change to finished sessions, the setting or the account.
  `PlaceNamer` = platform Geocoder; `routeName()` is pure and unit-tested ("A → B flight" between towns, sub-localities
  within one, "X run" for loops < 2 km; typed names are never replaced). `DriveAuth` = Identity
  `AuthorizationClient`, scope `drive.appdata` only (no Google Sign-In, no Drive SDK); `DriveClient` = Drive v3 REST on
  OkHttp. One `<sessionId>.json.gz` (ExportDocument with one session) per journey in `appDataFolder`, appProperties
  `sessionId`/`updatedAt`. Local wins: changed sessions upload, ones deleted here (purged, not in the bin) are deleted
  there, ones only in Drive are restored. Bookkeeping in `filesDir/journey_sync.json` (not DataStore, so a settings
  backup never carries a Drive account).
  **Cloud setup:** project `speedtrack-shivarya` (Drive API on), consent screen "SpeedTrack" with `drive.appdata`,
  Android OAuth clients for the package with the debug, upload and Play app-signing SHA-1s (see the release skill's
  Maps-key section for the values). No client id ships in the app — Play services matches package + signer, so a build
  signed with any other key gets `DEVELOPER_ERROR` on Connect. Verify uploads with `files.list(spaces=appDataFolder)`;
  they don't show in drive.google.com (Drive web → Settings → Manage apps shows the hidden data size).
- `core/health/` — Health Connect write (idempotent `clientRecordId = "<session>/<kind>"`) and heart-rate backfill
  from watch apps (e.g. boAt via its companion app), `HealthSyncWorker` (now + again 3 h later).
- `core/migration/` — **Expo import**: reads `RKStorage` (`catalystLocalStorage`) in 500 k-char `substr()` chunks
  (the Expo app kept every session in one `tracking_sessions` value, easily > the 2 MB CursorWindow), replays the
  points through the native filter, imports `unit_preference`. `RKStorage` is left in place.
- `core/export/` — GPX/TCX/KML/CSV/JSON writers, GPX parser (SAX, no DTDs), `ImportManager`, ZIP `BackupManager`,
  `ShareCard` (1080×1350 share image: MapLibre `MapSnapshotter` of OpenFreeMap + speed-coloured route + stats; route-only
  fallback on the brand gradient; "Hide start and finish" trims 200 m per end; "Add app links", on by default and
  remembered, puts a QR code to `AppShare.QR_URL` beside the title and both Play links in the message — QR via
  zxing `core`, encoder only).
- `core/share/AppShare` — "Share SpeedTrack": `res/raw/share_card.png` (= `branding/feature-graphic.png`; refresh it with
  the logo) + pitch + both Play links (join the closed test, then install); same pattern as Split Cash / Diet Plan.
  Drop the join-test link once the app is in production (`installSteps()`, used by both shares) and point `QR_URL`
  at `PLAY_URL`.
- `core/perf/PerformanceTimer` — pure state machine; launch extrapolated from the first two moving fixes;
  crossings interpolated; 1-ft rollout.
- `feature/*` — Compose screens (track, map, sensors, history, session, gps doctor, offline, perf, hr, health,
  profile, settings, hud); `widget/` — Glance widget, QS tile, `StartTrackingActivity` trampoline.
- Room DB `speedtrack.db` v1 (`app/schemas/`): sessions, track_points, laps, metrics, perf_runs. **Schema changes
  after 2.0.0 ships need a Room migration** (AutoMigration) — v1 is frozen once released.

### Gotchas

- The recording notification is on channel `tracking_live` (IMPORTANCE_DEFAULT, silent, public) so it shows on the
  lock screen — the original `tracking` channel was IMPORTANCE_LOW, which Android hides there, and channel importance
  can't be raised, hence the new id (the old one is deleted on start). It's also an Android 16 Live Update.
- `PointFilter`'s position-jump tolerance scales with speed (30 % of Doppler): at airliner speeds fix-timing jitter
  alone otherwise rejected whole stretches of cruise.
- `PointFilter` speed ceiling: above `ActivityType.maxValidSpeedMps` a fix survives only if Doppler and the position
  change agree (`confirmedMaxSpeedMps`, 340 m/s for `mayRideVehicle` activities). Before 2.0.6 a flight recorded as
  General lost 4 fixes in 5 and re-anchored the 5th at speed 0 — the speedometer flicked to 0 and distance stopped.
- The fused provider's altitude blends in the barometer, which is fiction in a pressurised cabin (it read ~2 000 m on
  the ground after landing); only GNSS-source sessions (flights) are safe there.
- OpenFreeMap's dark style draws water/rivers ~black and main roads #2a2a2a; `StyleTweaks` recolours them at runtime
  (layer ids are OpenFreeMap's — re-check after a style change). The offline zoom cap uses the middle half of the
  viewport, so a Streets area inside an Overview one can be zoomed into without centring on it exactly.
- **Play declarations** (done 2026-10-18 for 2.0.0): foreground-service types `location` and `connectedDevice`
  (each with an unlisted demo video), Health apps ("Activity and fitness" + reasons for `ACTIVITY_RECOGNITION` and
  `READ_HEART_RATE`). A new FGS type or health permission needs those forms updated first, or Play refuses the commit
  ("You must let us know whether your app uses any Foreground Service permissions"). The background-location
  declaration stays until the internal track (1.0.5) is replaced. Details: `.claude/skills/tracking-release` §6.
- Maps key is restricted to the package + debug (`5E:8F:…`), upload (`7E:80:…`) and Play app-signing
  (`00:D3:33:E2:95:AE:38:26:AC:87:5C:83:B8:07:10:27:5B:95:7F:99`) SHA-1s; a missing one renders Google maps blank
  (MapLibre still works).
- Offline, MapLibre draws nothing past an area's last zoom level (no overzoom from missing tiles), so
  `MapLibreTrackMap` caps the zoom at the covering area's level (`offlineZoomCap`) while there's no connection.
- Offline areas: MapLibre's `completedResourceSize` includes the style's fonts/icons, which every area shares, and the
  dark region reuses the light one's tile URLs. Sizes and the 50 MB stop use the light region's `completedTileSize`.
- Landscape phones (window height < 480 dp) get a side rail instead of Material's short bottom bar (`AppScaffold`),
  and the Track screen's dial is sized by the height — check landscape when changing either.
- Glance widget, QS tile and the shortcut trampoline are the only background start paths; a QS tile click is *not* an
  Android 14 while-in-use exemption, hence the trampoline activity.
- Health Connect sessions are end-exclusive: route points at exactly `endTime` make the insert throw.
- Emulator testing: `adb emu geo fix <lon> <lat> <alt> <sats> <knots>`; GNSS fixes from it arrive unevenly, so perf
  timer numbers on an emulator aren't meaningful. Git Bash needs `MSYS_NO_PATHCONV=1` for `adb shell` paths.

## Legacy Expo app (`mobile/`) — rollback only

Deprecated: no new features. To build it anyway: `cd mobile`, `npm install --legacy-peer-deps`, and the generated
`mobile/android/` (gitignored, hand-edited for signing; `keystore.play.properties` points at
`../../location-tracker-release.keystore`). `eas` commands run from `mobile/` (projectId in `mobile/app.json`).
