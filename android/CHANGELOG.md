# Changelog — SpeedTrack (native Android)

Releases up to 1.0.9 were the Expo / React Native app (`../mobile/CHANGELOG.md`). 2.0.0 is the native Kotlin/Compose
rewrite, shipped as an in-place update of the same Play package (`com.shivarya.locationspeedtracker`, same upload
key); previous sessions and the unit setting are imported on first launch.

## [2.0.0] - 2026-10-18

### Added
- **Native app** (Kotlin, Jetpack Compose, Material 3) — faster, smaller, works fully offline.
- **Reliable recording**: a location foreground service keeps recording with the screen off; every point is saved as
  it arrives, so a crash or reboot loses at most a few seconds. Pause, resume, laps, automatic km/mile splits,
  auto-pause.
- **GPS filtering**: rejects inaccurate fixes, position jumps and speed spikes; Doppler speed cross-checked against
  movement; walks at 1 Hz now keep their distance.
- **Maps**: Google Maps online (follow mode you can pan away from, full screen, satellite/terrain/hybrid, dark style)
  plus **offline maps** — download OpenStreetMap areas and SpeedTrack switches to them automatically without a
  connection. Speed / elevation-coloured routes, replay.
- **GPS doctor**: checklist with one-tap fixes (precise location, Google Location Accuracy, Wi-Fi and Bluetooth
  scanning, battery saver, notifications), live satellites sky view, signal strength per constellation, HDOP/VDOP,
  time to first fix, dual-frequency detection, A-GPS refresh / cold start.
- **Sensors**: G-force meter (vehicle frame), lean angle, compass, barometric altitude and vertical speed, steps
  and cadence, light / temperature / humidity where available, and a list of every sensor on the phone. Recorded
  with each session and charted.
- **Fitness**: walk / run / hike / cycle / drive / motorbike types, pace, calories (heart rate based when
  available), heart-rate zones, profile.
- **Bluetooth heart-rate sensors** (any standard strap or broadcasting watch).
- **Health Connect**: saves workouts (route, distance, steps, calories, elevation, speed, heart rate) and reads back
  heart rate from your watch's app.
- **Performance timer**: 0–60 mph / 0–100 km/h, ¼ mile / 400 m, rolling 50–70 / 80–120, braking — with launch
  detection, 1-ft rollout and history.
- **Export / import**: GPX, TCX, KML, CSV and full-fidelity JSON; GPX import; full backup and restore.
- **Quick access**: home-screen widget, Quick Settings tile, app shortcuts, picture-in-picture speedometer, mirrored
  HUD mode, Android 16 Live Update notification.
- Session detail with charts (speed, elevation, heart rate, cadence, G-force), splits and laps; History with weekly /
  monthly totals, filters, swipe to delete with undo.
- Light and dark themes, wallpaper colours (Android 12+), tablets and landscape.

### Changed
- New app icon: a speedometer whose needle is a GPS arrow (also the themed icon, splash and notification icon).
- Background-location permission is no longer requested — recording works through the foreground service.
- Minimum Android version is 8.0.

### Fixed (from 1.0.9)
- Start/stop leaking the previous session's points into the next, keep-awake never holding, the tracking
  notification never going away.
- Export JSON/CSV that did nothing; history rows that couldn't be opened; the map snapping back on every fix.

### Google Play Notes
```
SpeedTrack 2.0 — rebuilt from the ground up as a native app.
• Works offline, with downloadable maps
• GPS doctor: one-tap signal fixes, see your satellites
• Sensors: G-force, lean angle, compass, barometer, steps
• Heart-rate straps and Health Connect (watch heart rate too)
• Performance timer: 0–60 / 0–100, quarter mile, braking
• Export GPX/TCX/KML/CSV, import GPX, full backup
• Widget, Quick Settings tile, picture-in-picture, HUD mode
Your existing sessions carry over automatically.
```
