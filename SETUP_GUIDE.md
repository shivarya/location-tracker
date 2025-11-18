# Location Tracker - Setup & Run Guide

## Prerequisites

- Node.js (v16 or higher)
- npm or yarn
- Android Studio with Android SDK (for Android emulator)
- Or an Android physical device with USB debugging enabled

## Installation & Setup

### 1. Navigate to Project Directory
```bash
cd tracking-app
```

### 2. Install Dependencies (if not already done)
```bash
npm install --legacy-peer-deps
```

### 3. Configure Android (Optional - needed for physical device testing)

#### For Android Physical Device:
- Enable Developer Mode on your Android device
- Enable USB Debugging: Settings → About Phone → Build Number (tap 7 times) → Developer Options → USB Debugging
- Connect device via USB
- Run: `npm run android`

#### For Android Emulator:
- Open Android Studio
- Launch Android Virtual Device (AVD)
- Run: `npm run android`

## Running the App

### Start Development Server
```bash
npm start
```

This will display a menu with options:
- Press `a` for Android emulator/device
- Press `w` for web (limited functionality)
- Press `i` for iOS (Mac only)
- Press `j` to open debugger
- Press `o` to open in browser

### Direct Android Run
```bash
npm run android
```

### Web Preview (Limited)
```bash
npm run web
```

## Troubleshooting

### Issues with Location Permissions
- Make sure to grant location permission when prompted
- For Android 6.0+, the app requests runtime permissions
- Go to app settings and enable "Location" permission

### Emulator GPS Not Working
- Android Emulator may not have real GPS
- Use mock locations: Open emulator settings → Extended controls → Location → Set coordinates

### Port Already in Use
- If port 8081 is in use: `npm start --port 8082`

### Dependencies Issues
```bash
# Clear node modules and reinstall
rm -r node_modules package-lock.json
npm install --legacy-peer-deps
```

## Features Testing Checklist

- [ ] **Home Screen**: Start button appears, permission request works
- [ ] **Tracking Screen**: Shows live speed, location data, and updates in real-time
- [ ] **Unit Toggle**: Switch between km/h and mph
- [ ] **Location Data**: Latitude, longitude, altitude display correctly
- [ ] **Stop Tracking**: Session saves to history
- [ ] **History Screen**: Past sessions appear in list
- [ ] **Delete Session**: Can remove sessions from history
- [ ] **Export Data**: Can export as JSON/CSV
- [ ] **Settings**: Unit preference persists
- [ ] **Statistics**: Correct distance and speed calculations

## File Structure

```
tracking-app/
├── README.md            # Project documentation
├── .gitignore           # Git ignore rules
├── src/
│   ├── screens/         # UI screens
│   ├── components/      # Reusable components
│   ├── services/        # LocationService, StorageService
│   ├── store/           # Redux setup
│   └── utils/           # Conversions, constants
├── app/
│   ├── (tabs)/          # Expo Router tab navigation
│   └── _layout.tsx      # Root layout with Redux
├── app.json             # Expo config
├── package.json         # Dependencies
└── tsconfig.json        # TypeScript config
```

## API Reference

### LocationService (src/services/LocationService.ts)
- `requestPermissions()` - Request location access
- `checkPermissions()` - Check if permissions granted
- `startWatching()` - Start real-time location tracking
- `stopWatching()` - Stop location tracking
- `getCurrentLocation()` - Get single location point
- `cleanup()` - Clean up resources

### StorageService (src/services/StorageService.ts)
- `saveSession()` - Save tracking session
- `getSessions()` - Get all sessions
- `deleteSession()` - Delete single session
- `exportSessionsAsJSON()` - Export all as JSON
- `exportSessionsAsCSV()` - Export all as CSV

### Redux Store
Actions available:
- `startTracking()` - Begin tracking
- `stopTracking()` - End tracking
- `updateLocation()` - Update current location
- `addLocationPoint()` - Add point to session
- `setUnitSystem()` - Switch units
- `saveSession()` - Save completed session
- `deleteSession()` - Delete session

## Development Tips

### Hot Reload
- Shake device (or press Ctrl+M in emulator) to access menu
- Select "Reload" for fast refresh

### Debugging
- Use React Native Debugger: https://github.com/jhen0409/react-native-debugger
- Redux DevTools integration available

### Testing Location
Use these mock coordinates:
- New York: 40.7128, -74.0060
- London: 51.5074, -0.1278
- Tokyo: 35.6762, 139.6503

## Build for Production

### Android APK
```bash
cd android
./gradlew assembleRelease
```

APK will be at: `android/app/build/outputs/apk/release/app-release.apk`

### Android AAB (for Google Play)
```bash
cd android
./gradlew bundleRelease
```

---

**For detailed project documentation and architecture, check the README.md file.**
