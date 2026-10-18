# GitHub Copilot instructions — SpeedTrack (Location & Speed Tracker)

The app is a **native Android app** in `android/` (Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore,
WorkManager, Google Maps + MapLibre, Health Connect). Package `com.shivarya.locationspeedtracker`.
`mobile/` holds the deprecated Expo / React Native app (≤ 1.0.9) — don't add features there.

- Build / test: `cd android ; .\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.
- Architecture, conventions and gotchas: `CLAUDE.md` (repo root).
- Core logic (filtering, stats, splits, performance timer, export formats, Expo import) is plain Kotlin under
  `android/app/src/main/java/.../core/` with JVM unit tests in `android/app/src/test/` — keep it Android-free.
- Never commit `android/local.properties` (Maps key), `android/keystore.properties` or any keystore.
