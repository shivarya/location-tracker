---
name: tracking-dev
description: Start the SpeedTrack (Location Tracker) app for local development with the right install flags and Google Maps key setup. Use to run/test tracking-app locally.
---

Run the SpeedTrack app locally for development.

## Steps

1. **Install** (the `--legacy-peer-deps` flag is required — a plain `npm install` may fail on Expo Router peer deps):
   ```powershell
   cd "c:\Users\Ash\Documents\Projects\apps\tracking-app" ; npm install --legacy-peer-deps
   ```
2. **Maps key**: ensure `.env.local` has `GOOGLE_MAPS_ANDROID_API_KEY`. `app.config.js` injects it into the native config; the key must be restricted in Google Cloud to package `com.shivarya.locationspeedtracker` with your debug/release SHA, or the map is blank.
3. **Start**:
   ```powershell
   cd "c:\Users\Ash\Documents\Projects\apps\tracking-app" ; npm start
   ```
   Press `a` for Android (use the root `start-emulator.ps1` to launch an emulator first), or `npm run android` to build+run directly. `npm run web` for the web target.
4. **Lint** before committing: `npm run lint` (expo lint). There is no test script.

## Notes

- Routing is Expo Router (`app/(tabs)/`); state is Redux (`src/store/`). Background GPS runs via `expo-task-manager` in `src/services/LocationService.ts`.
- `npm run reset-project` scaffolds a clean Expo project — destructive to the current `app/`, use with care.
