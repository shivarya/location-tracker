# Google Play Store Assets

This folder contains all generated assets for Google Play Store submission.

## Required Assets

### App Icon
- ✅ icon-512.png (512x512) - Main app icon

### Adaptive Icons (Android)
- ✅ android-icon-foreground.png (512x512)
- ✅ android-icon-background.png (512x512)
- ✅ android-icon-monochrome.png (512x512)

### Feature Graphic
- ✅ feature-graphic.png (1024x500) - Required for store listing
  - **Note**: Edit this file to add your logo and app name

### Screenshots
- 📸 screenshot-phone-template.png (1080x1920)
- 📸 screenshot-tablet-7-template.png (1920x1080)
- 📸 screenshot-tablet-10-template.png (2560x1600)
  - **Note**: Take actual screenshots from your device/emulator

### Launcher Icons
- Various sizes: 48, 72, 96, 144, 192, 512

### Web Assets
- Favicons: 16, 32, 48
- Splash icons: 512, 1024

## Google Play Requirements

### Minimum Required:
1. App Icon (512x512)
2. Feature Graphic (1024x500)
3. At least 2 phone screenshots (1080x1920 or similar)
4. Privacy Policy URL
5. App description

### Screenshot Guidelines:
- Minimum 2, maximum 8 screenshots
- Supported formats: PNG, JPEG
- Max file size: 8MB each
- Recommended: 1080x1920 (portrait) or 1920x1080 (landscape)

### Feature Graphic Guidelines:
- Size: 1024x500 pixels
- Format: PNG or JPEG
- Max file size: 1MB
- Should showcase app branding

## How to Use

1. Upload icon-512.png as your app icon
2. Edit feature-graphic.png to add branding
3. Take screenshots from your device
4. Upload to Google Play Console

## Regenerate Assets

Run: `node scripts/generate-icons.js`

Generated: 20/11/2025, 6:28:30 pm
