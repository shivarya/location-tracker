# Location Tracker - React Native GPS Tracking App

A comprehensive real-time GPS tracking application built with React Native and Expo, designed to track live speed, altitude, latitude, longitude, and other geolocation data on Android devices.

## 🎯 Features

### Core Tracking Features
- **Real-Time Speed Tracking**: Live speed monitoring with automatic conversion between metric (km/h) and imperial (mph) units
- **GPS Coordinates**: High-precision latitude and longitude tracking
- **Altitude Measurement**: Current elevation with metric/imperial conversion
- **Heading & Direction**: Compass bearing and directional information
- **GPS Accuracy**: Real-time accuracy indicator showing signal quality
- **Data Points**: Capture multiple location data points during a session

### Session Management
- **Session Recording**: Start/stop tracking sessions with automatic data collection
- **Session History**: View all past tracking sessions with detailed statistics
- **Session Statistics**: Duration, total distance, average/max/min speed calculations
- **Session Deletion**: Remove unwanted sessions
- **Data Export**: Export sessions in JSON or CSV formats for analysis

### User Interface
- **Dark Theme**: Eye-friendly dark interface optimized for mobile
- **4-Tab Navigation**: Home, Tracking, History, and Settings screens
- **Live Dashboard**: Real-time display of all tracking metrics
- **Unit Toggle**: Quick switch between metric and imperial units
- **Responsive Design**: Optimized for various Android screen sizes

### Data Persistence
- **Local Storage**: All sessions saved to device using AsyncStorage
- **Automatic Backups**: Sessions persisted automatically after tracking stops
- **Preference Storage**: Unit preference retained across app sessions

## 🚀 Quick Start

### Prerequisites
- Node.js v16+
- Android Studio / Android SDK
- Android device or emulator

### Installation

1. **Install Dependencies**
```bash
npm install --legacy-peer-deps
```

2. **Start Development Server**
```bash
npm start
```

3. **Configure Google Maps Key (Local Only)**
```bash
cp .env.example .env.local
```
Then set `GOOGLE_MAPS_ANDROID_API_KEY` in `.env.local`.

4. **Run on Android**
```bash
npm run android
```

If map tiles appear blank, verify the key is restricted in Google Cloud for package `com.shivarya.locationspeedtracker` and your release/debug SHA fingerprints.

## 📋 Project Structure

```
LocationTracker/
├── src/
│   ├── screens/          # UI screens
│   ├── components/       # Reusable components
│   ├── services/         # LocationService, StorageService
│   ├── store/           # Redux state management
│   └── utils/           # Utilities & conversions
├── app/
│   ├── (tabs)/          # Tab navigation
│   └── _layout.tsx      # Root with Redux
├── SETUP_GUIDE.md       # Detailed setup instructions
└── package.json         # Dependencies
```

## 📱 Screens

- **Home Screen**: Welcome and start tracking
- **Tracking Screen**: Live GPS data display
- **History Screen**: Past sessions management
- **Settings Screen**: Preferences and info

## 🔧 Technical Stack

- React Native 0.81.5
- Expo 54.0.23
- TypeScript 5.9.2
- Redux 4.2.1
- Expo Location 18.0.1
- AsyncStorage 1.23.1

## 📖 Documentation

- **[SETUP_GUIDE.md](./SETUP_GUIDE.md)** - Installation and run instructions
- **[PROJECT_PLAN.md](../PROJECT_PLAN.md)** - Detailed architecture

---

**Ready to track your movement? Start the app and begin tracking!**
