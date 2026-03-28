# Changelog

All notable changes to Location & Speed Tracker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.8] - 2026-03-28

### Added
- Live Google map section on Home with current location marker and route polyline from session points.
- Configured Google Maps Android API key for release builds to support production map rendering.

### Changed
- Bumped app version to 1.0.8 and Android versionCode to 11 for Play Store release.

### Google Play Notes
- Added live Google map on the Home screen with your current position and route line.
- Added production Google Maps key setup for stable map rendering.
- Internal release version update for Play Store submission.

## [1.0.7] - 2026-03-28

### Added
- Live in-app Google map on Home with current location marker and route polyline.
- Offline area preparation on the tracking screen to prewarm nearby map tiles before travel.
- Offline map prepared-area management in Settings.

### Changed
- Improved offline tracking accuracy with GPS quality filtering and smarter speed/distance calculation.
- Added session-scoped background location buffering with ordered merge on resume.
- Improved tracking session lifecycle handling for start/stop stability.

### Fixed
- Reduced duplicate point merges after returning from background.
- Improved consistency of session statistics in low-signal conditions.

### Google Play Notes
- Better offline tracking accuracy for speed and distance.
- New live map view during tracking with visible route.
- Added offline map prep to cache nearby areas before your trip.
- Improved background tracking continuity and stability.

## [1.0.5] - 2025-11-25

### Changed
- Minor bug fixes and stability improvements
- Performance optimizations
- Enhanced user experience

## [1.0.4] - 2025-11-24

### Changed
- Enabled R8 code optimization for smaller app size
- Reduced app size from 57.1 MB to 54.8 MB
- Added ProGuard/R8 mapping file for crash deobfuscation

### Fixed
- Improved app performance with code minification

## [1.0.3] - 2025-11-24

### Removed
- Removed ACCESS_BACKGROUND_LOCATION permission from AndroidManifest
- App now uses only foreground location permissions for full Google Play compliance

### Changed
- Updated privacy policy to reflect foreground-only location tracking

## [1.0.2] - 2025-11-24

### Changed
- Version increment for release testing

## [1.0.1] - 2025-11-24

### Changed
- Version increment for initial Google Play submission

## [1.0.0] - 2025-11-24

### Added
- Real-time GPS location tracking (foreground only)
- Live speed monitoring with visual speedometer
- Distance and time tracking
- Session history with detailed statistics
- Metric and imperial units support
- Dark/light theme support
- Session recording and replay
- Maximum speed tracking
- Average speed calculations
- Location permission handling (foreground only)
- Session persistence with AsyncStorage

### Features
- **Location Tracking**: Track your location in real-time while app is open
- **Speed Display**: Visual speedometer showing current speed
- **Distance Tracking**: Monitor total distance traveled
- **Session History**: View past tracking sessions with details
- **Unit Conversion**: Toggle between metric (km/h, km) and imperial (mph, miles)
- **Privacy First**: No background location tracking, all data stored locally

### Technical
- React Native 0.81.5 with Expo 54.0.23
- TypeScript 5.9.2
- Redux state management
- Expo Router navigation
- expo-location for GPS tracking
- AsyncStorage for data persistence
- R8 code optimization enabled
- Foreground-only location permissions

---

## Release Notes Format for Google Play Console

When uploading to Google Play, copy the relevant version section and format as:

```
<en-US>
Version X.X.X Update

Added:
- Feature 1
- Feature 2

Changed:
- Change 1
- Change 2

Fixed:
- Bug fix 1
- Bug fix 2
</en-US>
```

## Version Numbering Guide

- **Major (X.0.0)**: Breaking changes or major new features
- **Minor (1.X.0)**: New features, backwards compatible
- **Patch (1.0.X)**: Bug fixes and minor improvements

**Version Code**: Must increment by 1 for each Google Play upload (currently: 11)
