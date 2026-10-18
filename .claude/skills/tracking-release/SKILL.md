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
   Play has seen — check Play Console → App bundle explorer. Lineage: Expo 1.0.9 = 12; 13 = used up (uploaded to a Play
   draft, then replaced); native 2.0.0 = **14**, so the next release is 15. A versionCode is spent as soon as a bundle
   is uploaded, even into a draft. EAS's remote counter (`eas build:version:get -p android` in `mobile/`) is stale
   (4) — ignore it.
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

- **Declaration errors.** The dry run doesn't check declarations; the real commit does. If it fails with a policy
  error (2.0.0's was *"You must let us know whether your app uses any Foreground Service permissions"*, because the
  bundle added `connectedDevice`), Play discards the whole edit and nothing is published. Fix the declaration in Play
  Console → Monitor and improve → Policy and programs → **App content** → *Actioned* tab → Manage (*Need attention*
  stays empty, since nothing was committed), then publish again. Alternatively upload the AAB by hand (Test and
  release → Testing → Closed testing → Alpha → Create new release): its review screen links to each missing
  declaration. That uses up the versionCode.
- **Saved is not rolled out.** A hand-made release only goes to review after *Start rollout to Closed testing* (and
  *Send changes for review* if Publishing overview lists unsent changes). A submission with "0 changes" means the
  release is still a draft.
- **Checking the result.** Read-only via the API: `edits.insert` → `edits.tracks.list` / `edits.bundles.list` →
  `edits.delete` (never commit), authenticating with `play-deploy/lib/google-auth.js`. A rolled-out release shows
  `status=completed`; Google's review state isn't in the API — that's Publishing overview and Policy status.

## 6. Play Console declarations (user does these; done for 2.0.0 on 2026-10-18)

Re-check them whenever a release adds a permission. Play refuses the commit until they cover the bundle.
- **Foreground service permissions**: Location → *User-initiated location sharing*; Connected device → *Continuous
  data transfer to an external device*; each with an unlisted YouTube demo video. The videos are captioned emulator
  recordings (local copies: `build-artifacts/speedtrack-fgs-location.mp4`, `speedtrack-fgs-connected-device.mp4`,
  gitignored). The connected-device one shows Bluetooth and a scan but no real strap: if Google rejects it, re-film
  with a strap, or drop `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
- **Health apps**: *Activity and fitness* only, plus a description per permission Play asks about
  (`ACTIVITY_RECOGNITION`: step counter/detector for steps and cadence, activity transitions for type suggestions and
  auto-pause; `READ_HEART_RATE`: reads the session's time window after a workout, again ~3 h later, or on "Refresh
  from Health Connect"). Keep them accurate if that behaviour changes. Google reviews this one.
- **Location permissions**: the background-location declaration stays for now. The internal track still serves
  1.0.5 (6), which requests `ACCESS_BACKGROUND_LOCATION`. Remove it once internal moves to a native build or its
  release is ended.
- **Data safety**: only the Google Maps SDK sends data off the phone, so enter the types Google lists for it
  (developers.google.com/maps/documentation/android-sdk/play-data-disclosure); health & fitness data isn't collected
  (it stays on the device / in Health Connect). Re-check this if networking changes.
- **Privacy policy**: the 2.0 text is live at `https://shivarya.dev/location-tracker/privacy.html` and on gh-pages
  (2026-10-18); republish both copies when `PRIVACY_POLICY.md` changes.
- **Store listing**: icon and feature graphic from `branding/` (`play-icon-512.png`, `feature-graphic.png`; re-render
  with `node branding/render.js mobile/node_modules`) and screenshots of the native app. The root `play-store-assets`
  skill only regenerates the deprecated Expo app's icons.
- **Maps key** (Google Cloud Console → Credentials, "New SpeedTrack Android Maps"): allows the package with the
  debug (`5E:8F:16:06:2E:A3:CD:2C:4A:0D:54:78:76:BA:A6:F3:8C:AB:F6:25`), upload (`7E:80:9D:…:E8:E9`) and Play
  app-signing (`00:D3:33:E2:95:AE:38:26:AC:87:5C:83:B8:07:10:27:5B:95:7F:99`) SHA-1s. The Play one is on Play Console →
  Protected with Play → Play Store protection → Play app signing. With `gcloud services api-keys update`, pass every
  `--allowed-application` plus `--api-target=service=maps-android-backend.googleapis.com`, since the update replaces
  the whole restriction.

## 7. Production

Manual, in Play Console: promote the tested closed-testing release to production with a staged rollout
(20% → 100%), only on the user's go-ahead. Once the native app is stable in production, delete `mobile/` (the keystore
lives at the repo root, so it is safe).

## Legacy Expo app — only for a rollback

`cd mobile ; npm install --legacy-peer-deps`, then build the generated `mobile/android/` locally
(`keystore.play.properties` → `../../location-tracker-release.keystore`) or with EAS from `mobile/`. Its versionCode
must also exceed the native one to roll back.
