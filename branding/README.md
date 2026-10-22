# SpeedTrack branding

The native app's logo (2.0.2+): an S-shaped road with centre-line dashes — a route, and the S of SpeedTrack — in
white on violet tonal shading, `#A78BFA` (top-left) → `#4C1D95` (bottom-right). One hue in shades, not a multi-colour
gradient. It replaced a speedometer-arc mark (2.0.0–2.0.1) that looked too much like Speedtest's gauge.

| File | What it is |
|------|------------|
| `speedtrack-icon.svg` | Master icon on the 108×108 adaptive-icon canvas (full-bleed gradient square) |
| `speedtrack-mark.svg` | The road alone, single colour, no dashes (themed icon / small glyphs) |
| `feature-graphic.svg` | Google Play feature graphic (1024×500) |
| `play-icon-512.png`, `feature-graphic.png` | Rendered Play Store uploads — regenerate with `render.js` |

Render the PNGs (uses the resvg and sharp copies installed for the legacy Expo app):

```bash
node branding/render.js mobile/node_modules
```

Geometry: road path `M77,34 H50 C37,34 37,54 50,54 H58 C71,54 71,74 58,74 H31`, stroke 15, butt ends; dashes are the
same path at stroke 2.5, dash 5/5, painted with the background gradient so they read as gaps. On the icon the glyph
is scaled to 0.9 about the centre (the road ends then stay inside the 66-unit safe circle), and the dash gradient is
the background's mapped into that scaled space — recompute it if the scale changes.

The Android app draws the logo from vectors, not these files — change them together:

- `android/app/src/main/res/drawable/ic_launcher_background.xml` — the gradient layer
- `android/app/src/main/res/drawable/ic_launcher_foreground.xml` — white road + gradient dashes
- `android/app/src/main/res/drawable/ic_launcher_monochrome.xml` — themed (Android 13+) icon: the road only
- `android/app/src/main/res/drawable/ic_splash.xml` — splash: gradient disc + road
- `android/app/src/main/res/drawable/ic_mark.xml` — 24dp glyph for the notification and Quick Settings tile
- `android/app/src/main/res/values/colors.xml` — `splash_background`, `notification_accent`

The share image (`core/export/ShareCard.kt`) draws the same road in its footer.

The deprecated Expo app (`mobile/`) keeps its old icon and its own generator (`mobile/scripts/generate-icons.js`).
