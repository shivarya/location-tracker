# Privacy Policy for Location & Speed Tracker (SpeedTrack)

**Effective Date**: October 18, 2026
**Last Updated**: October 31, 2026 (version 2.0.8 — Google Drive sync, journey names)

---

## Introduction

This Privacy Policy describes how Location & Speed Tracker ("SpeedTrack", "the app") handles your information.

**Developer**: Shivarya
**Website**: https://shivarya.dev
**Contact**: contact@shivarya.dev

**In short:** SpeedTrack has no account and no server of its own. Everything it records stays on your phone unless you
export it, back it up, turn on Google Drive sync, or turn on Health Connect sync. It shows no ads and contains no analytics or tracking SDKs.

---

## Information the app uses

All of the following is processed and stored **on your device** — and, if you turn on Google Drive sync, in a private
folder of your own Google Drive (see below).

### Location
- GPS / GNSS position (latitude, longitude), altitude, speed, heading, accuracy and timestamps.
- Satellite status (number of satellites, signal strength) for the GPS doctor.
- Used to show live speed, record sessions and routes, and compute statistics.
- Location is used **only while you use the app or while a recording you started is running** (shown by an ongoing
  notification). The app does not request background location access.

### Motion and environment sensors
- Accelerometer, gyroscope, magnetometer (compass), barometer, and — where your phone has them — light, temperature
  and humidity sensors. Used for the G-force meter, lean angle, compass, barometric altitude and the Sensors screen,
  and recorded with sessions.

### Physical activity
- Step count, cadence and the activity Google Play services detects (walking, running, cycling, in a vehicle).
  Requires the "Physical activity" permission, which is optional.

### Heart rate
- From a Bluetooth heart-rate sensor you pair, or read from Health Connect (see below). Optional.

### Profile
- Weight, height, year of birth, sex and heart-rate settings you choose to enter, used only to estimate calories and
  heart-rate zones.

---

## What leaves your device, and when

SpeedTrack itself never sends your recorded data anywhere. Some features use third-party services as part of their
normal operation:

| Feature | Service | What is sent |
|---|---|---|
| Online maps | Google Maps SDK for Android (Google) | Map tile requests for the area you view, with your IP address and device information, as for any Google Maps app. Governed by [Google's Privacy Policy](https://policies.google.com/privacy). |
| Offline maps & maps without Google | OpenFreeMap (tiles.openfreemap.org) | Standard web requests for map tiles of the area you view or download, with your IP address and an app identifier. No account or identifier of yours. |
| Location accuracy | Google Play services (fused location provider) | When Google Location Accuracy is on in your phone's settings, Google uses Wi-Fi, Bluetooth and mobile-network signals to improve location — this is a phone setting governed by Google. |
| Naming offline map areas | Android's Geocoder | The centre point of an area you are about to download, to suggest a place name. |
| Naming journeys (on by default, can be turned off in Settings) | Android's Geocoder (Google Play services) | The start and end points of a finished journey you haven't named, to name it after places ("Bengaluru → Delhi flight"). Only the two points, no route or other data. |
| Google Drive sync (optional, off until you connect) | Google Drive, in your own Google account | Each finished journey — route, statistics, laps, sensor and heart-rate readings — is stored in Drive's hidden app-data folder of the account you connect, so it survives a lost phone and can be restored on a new one. Only SpeedTrack can open that folder; SpeedTrack can't see your other Drive files. Nobody else, including the developer, has access. |
| Health Connect (optional, off by default) | Health Connect, on your device | Finished workouts (exercise session with route, distance, steps, calories, elevation, speed, heart rate) are written to Health Connect; heart rate recorded by other apps during your sessions is read from it. Health Connect is an Android component on your phone — data you share there is then available to other apps *you* allow. |
| Backups | Android Backup (your Google account), or a file you save | Android may back up the app's data to your Google account (encrypted, managed by Google). In-app backups are files saved wherever you choose. |
| Export / share | Apps you choose | Files (GPX, TCX, KML, CSV, JSON) you explicitly export or share. |

---

## Google Drive sync

Google Drive sync is off until you connect an account in Settings → Backup & sync. SpeedTrack then asks Google only
for the `drive.appdata` permission: access to a hidden folder in your Drive that belongs to SpeedTrack, and nothing
else. Journeys you record, edit or delete are uploaded, updated or deleted there; journeys found there on a new phone
are restored. The developer runs no server and never receives this data. To stop: Settings → Backup & sync →
Disconnect (journeys already uploaded stay in your Drive). To delete them, remove SpeedTrack's hidden app data in Google
Drive on the web (Settings → Manage apps → SpeedTrack → Delete hidden app data), or disconnect SpeedTrack under your
Google Account → Security → Third-party apps.

---

## Health Connect

If you turn on Health Connect, SpeedTrack uses the Health Connect permissions you grant only to:
- **Write** the workouts you record in SpeedTrack, and
- **Read** heart-rate data for the time of your SpeedTrack sessions, to show it with those sessions.

Health data is never used for advertising, never sold, never shared with third parties, and never used for anything
other than these features. You can revoke access at any time in Health Connect settings. SpeedTrack's use of
information received from Health Connect adheres to the Health Connect Permissions policy, including the Limited
Use requirements.

---

## Permissions

| Permission | Why |
|---|---|
| Precise / approximate location | Speed and route recording (while in use only) |
| Foreground service (location, connected device) | Keep recording, and keep a heart-rate sensor connected, with the screen off |
| Notifications | Show the recording notification with pause / lap / stop |
| Physical activity (optional) | Steps, cadence and activity detection |
| Nearby devices / Bluetooth (optional) | Find and connect a Bluetooth heart-rate sensor — never used to infer location |
| Health Connect (optional) | Write workouts, read heart rate |
| Network / Wi-Fi state | Download maps, sync to Google Drive; the GPS doctor checks whether Wi-Fi scanning is on |
| Google account, Drive app-data access (optional) | Google Drive sync — granted on Google's consent screen when you connect |

---

## Storage, retention and deletion

- Sessions and settings are stored in the app's private storage on your device, and — with Google Drive sync on — in
  your Drive's SpeedTrack app folder.
- Data stays until you delete it: delete a session (with undo), delete all sessions in Settings, or uninstall the app.
  Deleting a session also deletes its Drive copy while sync is connected. Data you exported, backed up or wrote to
  Health Connect is managed where you put it.

---

## Children

The app is not directed at children under 13 and does not knowingly collect information from them.

---

## Changes

We may update this policy; changes are shown by the "Last Updated" date above.

---

## Contact

**Email**: contact@shivarya.dev
**Website**: https://shivarya.dev
**Source code**: https://github.com/shivarya/location-tracker
