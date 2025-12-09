# Changelog

All notable changes to Location & Speed Tracker will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

**Version Code**: Must increment by 1 for each Google Play upload (currently: 5)
