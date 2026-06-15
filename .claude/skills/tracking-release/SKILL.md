---
name: tracking-release
description: Release a new SpeedTrack (Location Tracker) version to Google Play via EAS — bump version, build a production AAB, and submit. Use when shipping a tracking-app update.
---

Ship a new SpeedTrack release to Google Play using EAS. Package: `com.shivarya.locationspeedtracker` (EAS projectId is set in `app.json`).

## Steps

1. **Bump version** in `app.json` (`expo.version`; `expo.android.versionCode`). Note the `production` EAS profile has `autoIncrement: true`, so EAS bumps the build number automatically — still set a meaningful `version` string.
2. **Sanity check**: `cd "c:\Users\Ash\Documents\Projects\apps\tracking-app" ; npm run lint`
3. **Confirm the Maps key**: the production build must use a Google Maps Android key restricted to `com.shivarya.locationspeedtracker` with the release SHA fingerprint, injected via `app.config.js` (`GOOGLE_MAPS_ANDROID_API_KEY`). A wrong/unrestricted key renders the map blank.
4. **Build**: `eas build --platform android --profile production` (AAB).
5. **Submit**: `eas submit --platform android --profile production` (the `submit.production` profile is configured in `eas.json`).
6. **Add release notes**, review in Play Console, and roll out.

## References

- `GOOGLE_PLAY_GUIDE.md` (full publishing walkthrough), `PRIVACY_POLICY.md`, `eas.json` (profiles), `YOUTUBE_ASSETS.md` (marketing).
- For Play Store icons/feature graphic: root `play-store-assets` skill (`play-store-assets tracking-app`).

## Rules

- Background-location permissions are declared (`ACCESS_BACKGROUND_LOCATION`) — Play review requires a clear justification + privacy policy; keep `PRIVACY_POLICY.md` current and hosted.
- `npm install --legacy-peer-deps` if dependencies need reinstalling before a local build.
