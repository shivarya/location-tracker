# CLAUDE.md — Location Speed Tracker

Guidance for Claude Code when working inside `tracking-app/`. A real-time GPS tracking app with live speed monitoring, route recording, and session analytics. **React Native + Expo using Expo Router (file-based routing) and Redux** — note this differs from the React Navigation + Context pattern used by expense-tracker and split-app.

## Claude Code Skills (`.claude/skills/`, load when launched here)

- `tracking-dev` — run the app locally (legacy-peer-deps install + Maps key setup).
- `tracking-release` — ship a version to Google Play via EAS (build AAB → submit).

## Commands

```bash
npm install --legacy-peer-deps        # legacy-peer-deps is required
cp .env.example .env.local            # Set GOOGLE_MAPS_ANDROID_API_KEY
npm start                             # Expo dev server
npm run android                       # Run on Android
npm run web                           # Run on web
npm run lint                          # expo lint
npm run generate-icons                # Regenerate Play Store icons (or use the root play-store-assets skill)
npm run reset-project                 # Reset Expo project scaffold
```
There is no test script.

## Architecture

- **Routing — Expo Router**: `app/(tabs)/` holds tab screens (`index.tsx` = main tracking, `history.tsx`, `settings.tsx`, `_layout.tsx` = tab navigator). File-based, not React Navigation stacks.
- **State — Redux** (`src/store/`: index, reducer, actions, types) with `react-redux` + `redux-thunk`. Core shapes:
  - `TrackingSession`: `{ id, startTime, endTime, points[], statistics: { totalDistance, avgSpeed, maxSpeed, minSpeed, duration } }`
  - `LocationPoint`: `{ latitude, longitude, altitude, speed, accuracy, heading, timestamp }`
  - `TrackingState`: `{ isTracking, currentSession, sessions[], unitSystem, currentLocation, error }`
- **Services** (`src/services/`): `LocationService` (`expo-location` + `expo-task-manager` for background GPS), `StorageService` (AsyncStorage session persistence), `NetworkService`.
- **Components** (`src/components/`): LocationCard, SpeedDisplay, StatisticsCard, GPSStrengthIndicator, UnitToggle.
- **Utils** (`src/utils/`): unit conversion (km/h ↔ mph), constants.
- Maps via `react-native-maps`. High-accuracy GPS with ~1s update intervals; foreground tracking (Google Play compliant). Session export to JSON/CSV. Dark theme.

## Configuration & Gotchas

- **`app.config.js`** (not static app.json) injects `GOOGLE_MAPS_ANDROID_API_KEY` into the native config. **`eas.json`** defines development/preview/production build profiles.
- **Google Maps key must be restricted** in Google Cloud to package `com.shivarya.locationspeedtracker` with your release/debug SHA fingerprints, or the map renders blank.
- **`npm install --legacy-peer-deps` is required** (Expo Router + peer dep conflicts). A plain `npm install` may fail.

## Environment Variables

`.env.local`:
```
GOOGLE_MAPS_ANDROID_API_KEY=...
```

## Deployment

Google Play Store via EAS Build. Icon/asset generation lives as the root-level `play-store-assets` skill (wraps this project's `scripts/generate-icons.js`). See `GOOGLE_PLAY_GUIDE.md` for the full publishing walkthrough and `YOUTUBE_ASSETS.md` for marketing templates.
