# SpeedTrack branding

The native app's logo: a speedometer arc whose needle is a GPS navigation arrow, white on brand blue `#1F5EFF`.

| File | What it is |
|------|------------|
| `speedtrack-icon.svg` | Master icon on the 108×108 adaptive-icon canvas (full-bleed blue square) |
| `speedtrack-mark.svg` | The mark alone, cropped to its bounds |
| `feature-graphic.svg` | Google Play feature graphic (1024×500) |
| `play-icon-512.png`, `feature-graphic.png` | Rendered Play Store uploads — regenerate with `render.js` |

Render the PNGs (uses the resvg and sharp copies installed for the legacy Expo app):

```bash
node branding/render.js mobile/node_modules
```

The Android app draws the logo from vectors, not these files — change them together:

- `android/app/src/main/res/drawable/ic_launcher_foreground.xml` — the mark on the adaptive canvas; also the themed
  (monochrome) icon and the splash icon
- `android/app/src/main/res/drawable/ic_mark.xml` — 24dp glyph for the notification and Quick Settings tile
- `android/app/src/main/res/values/colors.xml` — `icon_background`, `splash_background`, `notification_accent`

The deprecated Expo app (`mobile/`) keeps its old icon and its own generator (`mobile/scripts/generate-icons.js`).
