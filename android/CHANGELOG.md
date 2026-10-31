# Changelog — SpeedTrack (native Android)

Releases up to 1.0.9 were the Expo / React Native app (`../mobile/CHANGELOG.md`). 2.0.0 is the native Kotlin/Compose
rewrite, shipped as an in-place update of the same Play package (`com.shivarya.locationspeedtracker`, same upload
key); previous sessions and the unit setting are imported on first launch.

## [2.0.7] - 2026-10-31

versionCode 21.

### Added
- **Recalculate stats** (session → ⋮): replays the saved route through the current filter and shows the before →
  after figures before applying them. Repairs sessions recorded by an older, stricter filter — e.g. a flight
  recorded as General on 2.0.4 (43 km → 1,739 km, max 395 → 925 km/h, moving 28 min → 2 h 27 min), which also
  becomes a Flight with its takeoff/landing card. Re-anchored fixes saved at speed 0 get their speed back from the
  neighbouring positions, auto splits are rebuilt (none for flights), calories are dropped where they don't apply,
  and elevation stays as recorded.

### Fixed
- Session detail: a heart-rate reload racing another update could put back stale chart data.

### Google Play Notes
```
SpeedTrack 2.0.7
• New "Recalculate stats" in a session's menu: repairs trips recorded by older versions, such as a flight saved as General with the wrong distance and speeds
```

## [2.0.6] - 2026-10-31

versionCode 20. Fixes found by replaying a real Bengaluru–Delhi flight that was recorded as "General" on 2.0.4.

### Added
- **Train** activity: up to 360 km/h (faster when Doppler confirms it), no auto-pause (stations and signal stops
  are part of the journey), no calories or Health Connect entry, GPX type `train`.
- **Automatic switch to Flight**: a session of any other type that holds an aircraft-only ground speed (≥ 450 km/h)
  for 20 s becomes a Flight in place — GNSS source, no barometer, no auto-pause, the flight panel — keeping its
  points, time and takeoff.
- Full-screen map: a north-up button whenever the map is rotated (the maps' own compasses sat under the toolbar).
- Picture-in-picture: elapsed time and altitude under the speed.

### Fixed
- Fast trips recorded as anything but Flight: above ~400 km/h every fix was dropped as a glitch and every 5th one
  was taken back with speed 0, so the speedometer flicked between the real speed and 0, distance stopped (43 km
  for a 1 740 km flight), and max/avg speed, moving time were wrong. A speed that both Doppler and the positions
  agree on is now kept in vehicle activities, up to ~1 200 km/h; a re-anchored fix keeps its speed.
- Flights: a stale or glitched pair of slow fixes mid-air no longer counts as a landing (the replay showed a phantom
  second leg); a slow fix after a long dropout only lands the flight if the next one isn't fast again.
- Altitude in flight: the fused provider blends in the barometer, which in a pressurised cabin read ~2 000 m on the
  ground at the destination; flights (including auto-switched ones) read GNSS altitude. Altitude is formatted the
  same way everywhere (12,346 m), and shows in the notification, full-screen map and picture-in-picture.
- Calories only for walks, runs, hikes and rides — not drives, flights, trains or General.
- No per-kilometre splits during a flight.
- Offline maps: zooming from an Overview area into a Streets/Roads area now carries on as soon as the detailed area
  covers the middle of the screen (it used to stop at the overview's level until the exact centre was inside it).
- Dark map at country zoom (Overview areas, flights): rivers, lakes and the sea were drawn almost black on black
  and main roads nearly invisible; they now stand out, with brighter town names. Rivers and main roads are bolder
  at low zoom on the light map too.
- Picture-in-picture text was dark-on-dark in the dark theme.

### Google Play Notes
```
SpeedTrack 2.0.6
• New Train mode
• A flight recorded as another activity now switches to Flight mode by itself
• Fixed speed flicking to 0, lost distance and wrong max speed above 400 km/h
• Correct altitude after landing; altitude on every screen
• Calories only for walks, runs, hikes and rides
• Offline maps: zoom from an overview straight into a detailed area; clearer rivers and roads at night
• North-up button on the full-screen map
```

## [2.0.5] - 2026-10-31

versionCode 19.

### Added
- **Flight mode** — a "Flight" activity that records a whole flight, gate to gate, entirely offline (airplane mode):
  - Accepts airliner ground speeds up to ~1 200 km/h. Every other activity treats anything above ~400 km/h as a GPS
    glitch, so a flight recorded as a drive lost every cruise fix. 1 000 km/h gauge, no calories or Health Connect
    entry.
  - Reads the GNSS chip directly (no network assistance in airplane mode, and in-flight Wi-Fi can place the fused
    provider at the ground station) and accepts the ±30–100 m fixes you get through a window.
  - Barometer off: the cabin is pressurised to ~2 000 m whatever the aircraft does. Altitude and vertical speed come
    from GNSS.
  - No auto-pause: gate holds and stopovers stay in the recording.
  - Live flight panel: phase (at the gate, taxiing, takeoff roll, climbing, cruising, descending, landed), takeoff
    time, time in the air (keeps counting through GPS dropouts), vertical speed.
  - Saved flights get a Flight card: takeoff and landing times, time in the air, distance flown, direct distance,
    max altitude, cruise speed, max ground speed, legs. Rebuilt from the points, so re-tagging an old session as a
    flight works too.
  - GPX type `flying`; imported tracks with "fly"/"flight"/"plane" in their type map to it.
- **Live metrics on the lock screen**: the recording notification now shows on the lock screen (it was on a
  low-importance channel, which Android hides there by default; it moved to a silent default-importance channel,
  `tracking_live`) with speed, distance and time, then max/avg speed and altitude, or, on a flight, the phase, time
  in the air, altitude and vertical speed. On Android 16 it is also a Live Update with the speed in the status bar.
- Full-screen live map: a solid panel with every metric (distance, time, avg/max speed, altitude, heading; the
  flight panel during a flight) instead of three numbers on a see-through pill.

### Fixed
- Offline maps: a network blip during a download (e.g. "Unable to resolve host tiles.openfreemap.org") stayed on
  the area in red even after it finished downloading. Connection errors now read "Waiting for a connection…" and
  clear as soon as the download moves again; a finished area always shows "Ready offline".
- Home-screen widget: the unit and distance were dark-on-dark on some launchers, and on a 2-row widget the header,
  time and Stop button were cut off. Fixed colours, and the layout now fits the widget's actual size.
- Dark theme: stat labels and units are brighter (~6.5:1 contrast instead of ~4.3:1); long values ("Descending",
  "1,234.56") shrink to fit their tile instead of being clipped.

### Google Play Notes
```
SpeedTrack 2.0.5
• New Flight mode: records a whole flight offline in airplane mode: takeoff, climb, cruise, landing, time in the air, altitude and speeds up to 1,200 km/h
• Live speed, distance, time and altitude on the lock screen
• Full-screen map now shows every metric
• Clearer stats and widget in dark mode
• Offline maps no longer show a stale network error on a finished download
```

## [2.0.4] - 2026-10-23

versionCode 18.

### Added
- **App links on a shared session image**: a QR code beside the title that opens the join-the-test page, and the
  message under the image ends with both links — join the closed test, then install from Play. "Add app links" in
  the share screen turns both off; the choice is remembered.
- ZXing (the QR encoder) in Settings → Map data & open-source licences.

### Fixed
- A long session name no longer runs off the edge of the shared image; it ends in "…".

### Google Play Notes
```
SpeedTrack 2.0.4
• Shared session images now carry a QR code so friends can get SpeedTrack, and the message includes the Play links
• Prefer not to? Turn off "Add app links" in the share screen
• Long session names no longer run off the shared image
```

## [2.0.3] - 2026-10-22

versionCode 17.

### Added
- **Share SpeedTrack** (Settings → Spread the word): sends friends the app's logo card with a short list of what's
  new and both links — join the closed test, then install from Play.

### Google Play Notes
```
SpeedTrack 2.0.3
• Share SpeedTrack with friends from Settings: the app card, what's new and the links to join and install
• Share any session as an image with your route and stats (2.0.2)
```

## [2.0.2] - 2026-10-22

versionCode 16.

### Added
- **Share an image of your session**: the route on a map, coloured by speed, with distance, time, pace or speed,
  climb and more — ready for WhatsApp or Instagram (Session → Share → Image with map and stats). Works offline in
  downloaded areas, and draws the route on its own when there's no map. "Hide start and finish" leaves the first and
  last 200 m off the image.

### Changed
- New app icon: an S-shaped road on violet. The old speedometer looked too much like a speed-test app.

### Google Play Notes
```
SpeedTrack 2.0.2
• Share your session as an image: your route on a map with distance, time, pace and more
• "Hide start and finish" keeps your home off the shared map
• New app icon
```

## [2.0.1] - 2026-10-18

versionCode 15.

### Added
- Offline maps: choose how much detail to download — **Streets**, **Roads** or **Overview**. Overview covers a whole
  country for flights (all of India is about 25 MB); when an area is too big, SpeedTrack suggests the level that fits.
- Zoom buttons on the offline-area map.

### Fixed
- The keyboard no longer covers the area name and Download button on the Offline maps screen, or the fields on the
  Profile screen.
- Offline, maps stay at a zoom the downloaded area can draw instead of going blank when zoomed in.
- Pinch-zooming or panning a map inside a scrolling screen no longer turns into a page scroll.
- An offline area's dark-mode copy could start downloading twice.

### Google Play Notes
```
SpeedTrack 2.0.1
• Offline maps: pick the detail level. "Overview" fits a whole country for flights (all of India in about 25 MB)
• Offline maps stay visible when you zoom in without a connection
• Easier zooming: zoom buttons, and pinch no longer scrolls the page
• The keyboard no longer covers text boxes
```

## [2.0.0] - 2026-10-18

versionCode 14 (13 was uploaded to a Play draft and never released).

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
