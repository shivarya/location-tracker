# SpeedTrack — GPS speed tracker for Android

A native Android app (Kotlin, Jetpack Compose) for live speed, route recording and everything your phone's sensors
can measure. Works fully offline. On Google Play as **Location & Speed Tracker** (`com.shivarya.locationspeedtracker`).

## Features

- **Live speed & recording** — big speedometer (speed or pace), distance, moving time, average / max speed, climb;
  records in the background with the screen off; pause, laps, automatic km/mile splits, auto-pause.
- **Maps** — Google Maps online; downloadable **offline maps** (OpenStreetMap via OpenFreeMap) used automatically
  without a connection; speed- or elevation-coloured routes, replay.
- **GPS doctor** — one-tap fixes for location settings (Google Location Accuracy, Wi-Fi / Bluetooth scanning, battery
  saver), sky view of satellites, signal strength per constellation, DOP, time to first fix, dual-frequency check,
  A-GPS refresh.
- **Sensors** — G-force meter, lean angle, compass, barometric altitude & vertical speed, steps & cadence,
  environment sensors, a list of every sensor on the phone.
- **Fitness** — activity types, calories, heart-rate zones, Bluetooth heart-rate straps, **Health Connect** sync
  (and heart rate from your watch's app).
- **Performance timer** — 0–60 mph / 0–100 km/h, ¼ mile / 400 m, rolling and braking runs.
- **Your data** — GPX / TCX / KML / CSV / JSON export, GPX import, full backup & restore. No account, no server,
  no ads, no analytics.
- **Quick access** — widget, Quick Settings tile, shortcuts, picture-in-picture, mirrored HUD mode.
- Metric and imperial throughout; light and dark themes.

## Repository layout

| Path | Contents |
|------|----------|
| `android/` | The app (Gradle project). See [`CLAUDE.md`](CLAUDE.md) for architecture and build notes. |
| `mobile/` | The previous Expo / React Native app (≤ 1.0.9) — **deprecated**, kept until the native app finishes testing. |
| `website/`, `index.html`, `privacy.html`, `terms.html` | Landing page and policies. |
| [`PRIVACY_POLICY.md`](PRIVACY_POLICY.md) | Privacy policy. |

## Build

Requires JDK 17 and the Android SDK.

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:installDebug
```

Put `MAPS_API_KEY=...` (a Google Maps Android key) in `android/local.properties` for Google maps; without it the app
uses OpenStreetMap maps everywhere. Release signing reads `android/keystore.properties` (not in the repo).

## Map data

Online maps © Google. Offline maps © OpenStreetMap contributors, © OpenMapTiles, served by
[OpenFreeMap](https://openfreemap.org).
