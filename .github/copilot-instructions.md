# GitHub Copilot Instructions for Location & Speed Tracker

## Project Overview
This is a React Native + Expo application for real-time GPS location and speed tracking. The app tracks user location, speed, distance, and session history with support for both metric and imperial units, and includes a live Google map on Home.

## Technology Stack
- **Framework**: React Native 0.81.5 with Expo 54.0.23
- **Language**: TypeScript 5.9.2
- **State Management**: Redux 5.0.0 with React-Redux 9.1.0
- **Navigation**: Expo Router (file-based routing)
- **Location Services**: expo-location + expo-task-manager (foreground + background-capable)
- **Map SDK**: react-native-maps (Google provider)
- **Storage**: @react-native-async-storage/async-storage
- **Build System**: Gradle 8.14.3 (Android)
- **Target Platforms**: Android (Google Play Store)

## Project Structure
```
/app                    - Expo Router screens (tabs layout)
/src
  /components          - Reusable UI components
  /screens             - Main screen components
  /services            - Location & Storage services
  /store               - Redux store, actions, reducers, types
  /utils               - Constants and conversion utilities
/android               - Android native code and build configuration
/assets                - Images, icons, splash screens
/play-store-assets     - Google Play Store assets
```

## Key Architecture Patterns

### State Management
- Use Redux for global state (tracking sessions, settings, history)
- Actions are dispatched from screens/components
- Reducers handle state updates immutably
- Types are defined in `/src/store/types.ts`

### Location Tracking
- Background-capable location tracking is enabled and used for session continuity
- Uses `expo-location` with `watchPositionAsync()` for real-time updates
- Uses task-based background updates and syncs buffered points on foreground resume
- Home screen displays a live Google map with current marker and session route polyline

### Code Style
- Use TypeScript strict mode
- Functional components with React Hooks
- Async/await for asynchronous operations
- Proper error handling with try-catch blocks
- Use const for immutable values, let for reassignable variables

## Version Management

### CRITICAL: Version Syncing
- **ONLY** update versions in `app.json`
- The `android/app/build.gradle` automatically reads from `app.json`
- Update both `version` (semantic version) and `android.versionCode` together
- Never manually edit version in `build.gradle`

### CHANGELOG.md Updates
- **MUST** update `CHANGELOG.md` after each version upgrade
- Follow format: version number, date, and categorized changes
- Categories: Added, Changed, Fixed, Removed
- Changes should match Google Play release notes
- Keep entries concise and user-focused
- Update BEFORE building release AAB

### Version Increment Rules
```json
// In app.json
{
  "expo": {
    "version": "1.0.X",  // Increment for each release
    "android": {
      "versionCode": X   // Must increment by 1 for each Play Store upload
    }
  }
}
```

### Version Upgrade Workflow
1. Update version in `app.json` (both version and versionCode)
2. Update `CHANGELOG.md` with new version entry
3. Build release AAB: `cd android && .\gradlew bundleRelease`
4. Prepare Google Play release notes from CHANGELOG.md entry
5. Upload AAB and mapping.txt to Play Console

## Android Build Configuration

### Release Signing
- Keystore: `android/location-tracker-release.keystore`
- Credentials stored in `android/keystore-info.txt` (NOT in git)
- Signing configured in `android/app/build.gradle`

### Build Commands
```bash
# Clean build (if needed)
cd android && .\gradlew clean

# Build release AAB
cd android && .\gradlew bundleRelease

# Output location
android/app/build/outputs/bundle/release/app-release.aab

# Mapping file (for crash deobfuscation)
android/app/build/outputs/mapping/release/mapping.txt
```

### R8 Optimization
- R8 minification is ENABLED in release builds
- Reduces app size and obfuscates code
- Always upload mapping.txt to Google Play Console with each release

## Google Play Store Compliance

### Permissions Policy
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `ACCESS_BACKGROUND_LOCATION` are enabled
- Declared in both `app.json` and `android/app/src/main/AndroidManifest.xml`
- Must stay in sync between both files

### Google Maps API Key
- Configure Android key in `app.json` under `expo.android.config.googleMaps.apiKey`
- Restrict key in Google Cloud to package `com.shivarya.locationspeedtracker` with release SHA-1 fingerprints
- Keep release SHA fingerprints in sync with signing keys used for AAB generation

### Privacy Requirements
- Privacy policy must be hosted and accessible
- Located in `website/privacy.html` and `privacy.html`
- Must be uploaded to https://shivarya.dev or GitHub Pages
- Privacy policy URL must be added to Google Play Console

### Store Listing
- App name: "Location & Speed Tracker"
- Package: com.shivarya.locationspeedtracker
- Category: Maps & Navigation
- Content rating: Everyone
- All icons generated in `play-store-assets/` folder

## Common Tasks

### Adding New Features
1. Create types in `/src/store/types.ts` if needed
2. Add actions in `/src/store/actions.ts`
3. Update reducer in `/src/store/trackingReducer.ts`
4. Implement UI in components/screens
5. Test thoroughly before release

### Modifying Location Tracking
- Only modify `/src/services/LocationService.ts`
- Keep foreground and background permission requests consistent with policy
- Keep `/src/tasks/backgroundLocation.ts` and foreground sync flow aligned
- Test permission flows and app background/foreground transitions on Android

### Updating Dependencies
```bash
# Check for updates
npx expo install --check

# Update specific package
npx expo install expo-location@latest

# Rebuild after updates
cd android && .\gradlew bundleRelease
```

### Icon Generation
```bash
# Regenerate all icons
npm run generate-icons

# Icons are generated in play-store-assets/
# Transparent backgrounds with safe padding
```

## File Modification Guidelines

### DO NOT Edit Directly
- `android/app/build.gradle` version fields (auto-synced from app.json)
- Generated files in `android/app/build/`
- `node_modules/` directory

### Always Update Together
- `app.json` permissions ↔ `android/app/src/main/AndroidManifest.xml` permissions
- `app.json` `expo.android.config.googleMaps.apiKey` ↔ Google Cloud key restrictions (package + SHA)
- `app.json` version ↔ Release notes in `GOOGLE_PLAY_GUIDE.md`

### Sensitive Files (Not in Git)
- `android/keystore-info.txt`
- `android/location-tracker-release.keystore`
- `.env` files

## Testing Checklist

### Before Release Build
- [ ] Version incremented in `app.json`
- [ ] CHANGELOG.md updated with version changes
- [ ] Permission model in `app.json` and `AndroidManifest.xml` is intentionally aligned
- [ ] App.json permissions match AndroidManifest.xml
- [ ] Google Maps API key is configured and restricted to package + release SHA
- [ ] Privacy policy is accessible online
- [ ] Icons are transparent with safe padding
- [ ] Test location tracking in foreground and during app background transitions
- [ ] Test live map tile loading and route rendering on Home
- [ ] Test unit conversion (metric ↔ imperial)
- [ ] Test session history persistence

### After Build
- [ ] AAB file size is reasonable (~55 MB)
- [ ] Mapping file generated (for R8 builds)
- [ ] Test install on real Android device
- [ ] Verify permissions dialog shows correct text
- [ ] Upload both AAB and mapping.txt to Play Console
- [ ] Copy release notes from CHANGELOG.md to Play Console

## Common Issues & Solutions

### "Version code X already used"
- Increment `versionCode` in `app.json`
- Rebuild: `cd android && .\gradlew bundleRelease`

### "Background location permission found"
- Verify permission policy for the current release scope
- Ensure `app.json` and `AndroidManifest.xml` are intentionally aligned
- Update release notes/policy text if permission model changed

### "Build fails with version error"
- Ensure `build.gradle` has JsonSlurper import
- Verify `app.json` is valid JSON
- Check projectRoot path is correct

### Location not updating
- Verify foreground and background permissions granted as required
- Check GPS is enabled on device
- If app resumed from background, verify buffered points are syncing
- Check LocationService.startWatching() is called

## Code Conventions

### Naming
- Components: PascalCase (e.g., `LocationCard.tsx`)
- Services: PascalCase (e.g., `LocationService.ts`)
- Utils: camelCase (e.g., `conversions.ts`)
- Constants: UPPER_SNAKE_CASE (e.g., `METERS_PER_SECOND_TO_KMH`)

### Imports Order
1. React/React Native imports
2. Third-party libraries
3. Local components
4. Services
5. Utils/Constants
6. Types

### TypeScript
- Always define prop types
- Use interfaces for object shapes
- Avoid `any` type - use `unknown` if type is truly unknown
- Export types from `/src/store/types.ts`

## Git Workflow
- Main branch: `main`
- Commit messages: Descriptive and concise
- Never commit sensitive files (keystore, credentials)
- Tag releases: `git tag v1.0.X`

## Documentation
- `README.md` - Project overview and setup
- `CHANGELOG.md` - Version history and release notes for Google Play
- `GOOGLE_PLAY_GUIDE.md` - Play Store submission guide
- `PRIVACY_POLICY.md` - Privacy policy content
- `SETUP_GUIDE.md` - Development setup instructions

## Contact & Support
- Developer: Ashish Tripathi
- Organization: ShivArya
- Email: contact@shivarya.dev
- Website: https://shivarya.dev
- Repository: https://github.com/shivarya/location-tracker
