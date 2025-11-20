# Google Play Store Publishing Guide
## Location & Speed Tracker

---

## 📋 Prerequisites

Before you begin, ensure you have:

1. **Google Play Developer Account** ($25 one-time fee)
   - Sign up at: https://play.google.com/console
   - Complete account verification
   
2. **EAS CLI installed**
   ```bash
   npm install -g eas-cli
   ```

3. **Expo Account**
   - Sign up at: https://expo.dev
   - Required for EAS Build service

---

## 🚀 Step-by-Step Publishing Process

### Step 1: Install EAS CLI and Login

```bash
# Install EAS CLI globally
npm install -g eas-cli

# Login to your Expo account
eas login
```

If you don't have an Expo account, create one at https://expo.dev/signup

---

### Step 2: Configure EAS Build

```bash
# Initialize EAS in your project
eas build:configure
```

This creates an `eas.json` file with build configurations.

---

### Step 3: App Store Listing Assets

✅ **Icons Already Generated!** All required icons have been created using the automated icon generation tool.

#### Available Assets (in `play-store-assets/` folder):
1. ✅ **App Icon** - `icon-512.png` (512x512 PNG)
2. ✅ **Adaptive Icons** - Foreground, background, and monochrome (512x512 PNG each)
3. ✅ **Feature Graphic** - `feature-graphic.png` (1024x500 PNG) - Ready to use or edit
4. ✅ **Launcher Icons** - Multiple sizes (48, 72, 96, 144, 192, 512)
5. ✅ **Screenshot Templates** - Phone and tablet templates generated

#### Still Needed:
1. 📸 **Screenshots** - Take at least 2 actual app screenshots (1080x1920 or similar)
2. 🔗 **Privacy Policy URL** - Host the privacy policy (required for location permissions)

**To regenerate icons anytime:**
```bash
npm run generate-icons
```

See `ICON_GENERATION_COMPLETE.md` for full details on generated assets.

#### App Information:
- **App Name**: Location & Speed Tracker
- **Short Description**: Real-time GPS tracking with speed monitoring
- **Full Description**: (See below)
- **Category**: Maps & Navigation
- **Content Rating**: Everyone
- **Privacy Policy**: Required (app uses location data)

---

### Step 4: Build Production APK/AAB

Google Play requires AAB (Android App Bundle) format:

```bash
# Build for Google Play Store (AAB format)
eas build --platform android --profile production
```

**What happens:**
- EAS uploads your code to Expo servers
- Builds the app in the cloud
- Generates a signed AAB file
- You'll receive a download link when complete (takes ~10-20 minutes)

**Alternative: Local Build (if you have Android SDK)**
```bash
# Generate native Android project
npx expo prebuild

# Build locally
cd android
./gradlew bundleRelease
```

---

### Step 5: Generate App Signing Key

**Option A: Let Google Play manage signing (Recommended)**
- Google Play App Signing handles key management
- Choose this when creating the app in Play Console

**Option B: Self-manage signing**
```bash
# Generate keystore
keytool -genkeypair -v -storetype PKCS12 -keystore my-app.keystore -alias my-app-alias -keyalg RSA -keysize 2048 -validity 10000

# You'll be prompted for:
# - Keystore password
# - Key password
# - Your name/organization details
```

⚠️ **CRITICAL**: Backup your keystore file! If lost, you can't update your app.

---

### Step 6: Create App in Google Play Console

1. **Go to Play Console**: https://play.google.com/console
2. Click **"Create app"**
3. Fill in details:
   - **App name**: Location & Speed Tracker
   - **Default language**: English (United States)
   - **App or game**: App
   - **Free or paid**: Free
   - Accept declarations

4. Complete **App Dashboard** sections:

---

### Step 7: Set Up Store Listing

Navigate to **Store presence → Main store listing**

#### Fill in:

**App name**: Location & Speed Tracker

**Short description** (80 characters max):
```
Real-time GPS tracking with live speed monitoring and route recording
```

**Full description** (4000 characters max):
```
📍 Track Your Journey with Precision

Location & Speed Tracker is a powerful GPS tracking app that provides real-time speed monitoring, accurate location tracking, and comprehensive session analytics. Perfect for runners, cyclists, drivers, and outdoor enthusiasts.

🎯 KEY FEATURES

✓ Real-Time Speed Display
  • Live speed tracking in km/h or mph
  • Large, easy-to-read display
  • Instant unit switching

✓ Comprehensive Location Data
  • GPS coordinates (latitude/longitude)
  • Altitude tracking
  • Location accuracy indicator
  • Heading/direction display
  • Timestamp for each data point

✓ Session Management
  • Record unlimited tracking sessions
  • Automatic session saving
  • View complete session history
  • Track duration with precision timer

✓ Advanced Statistics
  • Total distance traveled
  • Average speed
  • Maximum speed
  • Minimum speed
  • Number of data points collected
  • Session duration (HH:MM:SS format)

✓ Data Export
  • Export sessions as JSON
  • Export sessions as CSV
  • Perfect for analysis and record keeping

✓ Flexible Units
  • Metric system (km/h, meters, kilometers)
  • Imperial system (mph, feet, miles)
  • Instant switching between systems
  • Preference saved automatically

✓ Modern Dark Interface
  • Professional dark theme
  • Cyan accent colors
  • Clean, minimalist design
  • Easy-to-read displays

🔒 PRIVACY & PERMISSIONS

• Location permission required for GPS tracking
• All data stored locally on your device
• No data shared with third parties
• Complete control over your data
• Delete sessions anytime

💡 PERFECT FOR

• Running and jogging
• Cycling and biking
• Driving and road trips
• Hiking and outdoor activities
• Walking and fitness
• Speed monitoring
• Distance tracking
• Route recording

📊 TECHNICAL FEATURES

• High-accuracy GPS tracking
• 1-second update intervals
• Haversine formula for distance calculation
• Automatic session persistence
• Pull-to-refresh data loading
• Offline functionality
• Lightweight and fast

🎨 USER EXPERIENCE

• Intuitive tab navigation
• One-tap to start/stop tracking
• Tap speed display to toggle units
• Swipe to delete sessions
• Confirmation dialogs for data safety
• Helpful tips and guidance

Whether you're tracking your morning run, monitoring your road trip, or analyzing your cycling performance, Location & Speed Tracker provides all the tools you need in a clean, modern interface.

Download now and start tracking your journey with precision!

---

📧 Support: contact@shivarya.dev
🌐 Website: https://shivarya.dev
🔄 Regular updates with new features
⭐ Rate us and share your experience
```

**App category**: Maps & Navigation

**Tags**: gps, tracking, speed, location, navigation, fitness

**Email**: contact@shivarya.dev

**Website**: https://shivarya.dev

---

### Step 8: Upload Graphics Assets

All graphics have been pre-generated! Find them in the `play-store-assets/` folder.

**App icon** (512x512):
- ✅ Use `play-store-assets/icon-512.png`
- Already converted from SVG and optimized

**Feature graphic** (1024x500):
- ✅ Use `play-store-assets/feature-graphic.png`
- Already generated with app theme colors (#0A1929 background)
- **Optional**: Edit to add logo text/branding using an image editor (Canva, Photoshop, GIMP)

**Screenshots** (minimum 2, maximum 8):
- **Action Required**: Take screenshots from your Android device/emulator
- Show key screens: tracking screen, history, settings
- Recommended size: 1080x1920 (portrait) or 1920x1080 (landscape)
- Format: PNG or JPEG, max 8MB each

**How to take screenshots:**
1. Run the app: `npm start` then press `a` for Android
2. Navigate to main screens
3. Take screenshots (device/emulator screenshot button)
4. Save to `play-store-assets/screenshots/` folder

**Templates available:**
- `screenshot-phone-template.png` (1080x1920)
- `screenshot-tablet-7-template.png` (1920x1080)
- `screenshot-tablet-10-template.png` (2560x1600)

---

### Step 9: Content Rating

1. Go to **Policy → App content → Content rating**
2. Fill out questionnaire:
   - Does app contain violence? **No**
   - Does app contain sexual content? **No**
   - Does app contain language? **No**
   - Does app contain controlled substances? **No**
   - Does app contain gambling? **No**
   - **User interaction**: None
3. Submit for rating (automatic, instant result)

---

### Step 10: Privacy Policy

**Required** because app uses location data.

**Privacy Policy URL**: https://shivarya.dev/location-tracker/privacy.html

**Privacy Policy Content** (already created in `PRIVACY_POLICY.md` and `website/privacy.html`):

The privacy policy has been created and is ready to host. You need to:

1. **Upload the website** to https://shivarya.dev/location-tracker/
   - Files are in the `website/` folder
   - Upload `privacy.html` to make it accessible at the URL above
   - Or use the markdown version from `PRIVACY_POLICY.md`

2. **Hosting Options**:
   - GitHub Pages (free)
   - Netlify (free)
   - Vercel (free)
   - Your own domain/server

3. **Quick Setup with GitHub Pages**:
   ```bash
   # Create gh-pages branch
   git checkout -b gh-pages
   
   # Copy website files to root
   cp -r website/* .
   
   # Commit and push
   git add .
   git commit -m "Add website for privacy policy"
   git push origin gh-pages
   
   # Enable GitHub Pages in repository settings
   # Then access at: https://shivarya.github.io/location-tracker/privacy.html
   ```

Once hosted, add the URL to the Play Console privacy policy field.

---

### Step 11: Target Audience & News Apps

1. Go to **Policy → Target audience and content**
2. **Target age**: 13+
3. **Appeal to children**: No
4. Save

---

### Step 12: Data Safety

1. Go to **Policy → Data safety**
2. Fill in:
   - **Does app collect data**: Yes
   - **Location data**: Collected, Not shared, Optional, Ephemeral
   - **Purpose**: App functionality
   - Click **Next** and save

---

### Step 13: Upload App Bundle (AAB)

1. Go to **Release → Production**
2. Click **Create new release**
3. Upload the AAB file from Step 4
4. **Release name**: 1.0.0 (Initial Release)
5. **Release notes** (must include language tags):
```
<en-US>
Initial Release - Version 1.0.0

Welcome to Location & Speed Tracker!

Features:
- Real-time GPS tracking
- Live speed monitoring (km/h & mph)
- Session recording and history
- Comprehensive statistics
- Data export (JSON/CSV)
- Dark theme interface
- Metric/Imperial units

Start tracking your journey with precision today!
</en-US>
```

6. Click **Save** → **Review release**

---

### Step 14: Countries & Regions

1. Go to **Release → Production → Countries/regions**
2. **Select**: Add all countries (or specific ones)
3. Save

---

### Step 15: Pricing & Distribution

1. Go to **Monetization → Pricing & distribution**
2. **Free/Paid**: Free
3. **Countries**: Available in all countries
4. **Contains ads**: No
5. **In-app purchases**: No
6. Accept content guidelines
7. Save

---

### Step 16: Submit for Review

1. **Dashboard**: Ensure all sections have green checkmarks
2. Click **"Send X items for review"** at the top
3. Review summary
4. Click **"Send for review"**

**Review time**: 1-7 days (usually 1-3 days)

---

## 📱 Testing Before Publishing

### Internal Testing Track

Test with up to 100 testers before going live:

```bash
# Build for internal testing
eas build --platform android --profile preview
```

1. Go to **Release → Testing → Internal testing**
2. Create new release
3. Upload AAB
4. Add testers (email addresses)
5. Share test link with testers

---

## 🔄 App Updates

To release updates:

1. Update version in `app.json`:
```json
{
  "expo": {
    "version": "1.1.0",
    "android": {
      "versionCode": 2
    }
  }
}
```

2. Build new AAB:
```bash
eas build --platform android --profile production
```

3. Go to **Production** → **Create new release**
4. Upload new AAB
5. Add release notes
6. Submit for review

---

## ⚙️ Important Commands Reference

```bash
# Install EAS CLI
npm install -g eas-cli

# Login to Expo
eas login

# Configure EAS
eas build:configure

# Build for production
eas build --platform android --profile production

# Build for testing
eas build --platform android --profile preview

# Check build status
eas build:list

# Submit to Play Store (alternative method)
eas submit --platform android
```

---

## 📋 Checklist Before Submission

### Assets (Generated ✅)
- [x] App icon (512x512 PNG) - `play-store-assets/icon-512.png`
- [x] Feature graphic (1024x500 PNG) - `play-store-assets/feature-graphic.png`
- [x] Adaptive icons (foreground, background, monochrome)
- [x] Launcher icons (multiple sizes)
- [ ] At least 2 screenshots (take from device/emulator)

### Documentation (Created ✅)
- [x] Privacy policy content - `PRIVACY_POLICY.md` and `website/privacy.html`
- [ ] Privacy policy URL (needs hosting)

### Store Listing (To Do)
- [ ] Store listing description completed
- [ ] Content rating questionnaire completed
- [ ] Data safety form completed
- [ ] Target audience selected
- [ ] Countries/regions selected

### Build & Test (To Do)
- [ ] AAB file built (`eas build --platform android --profile production`)
- [ ] AAB file uploaded to Play Console
- [ ] Release notes written
- [ ] Tested app on Android device

### Final Check (To Do)
- [ ] All dashboard items have green checkmarks
- [ ] App reviewed and submitted

### Quick Reference Commands
```bash
# Generate/regenerate icons
npm run generate-icons

# Build production AAB
eas build --platform android --profile production

# Start app for screenshots
npm start
```

---

## 🚨 Common Issues & Solutions

### Issue: Build fails
**Solution**: Check `eas build:list` for error logs, ensure all dependencies are installed

### Issue: Location permissions not working
**Solution**: Ensure permissions are declared in `app.json` android.permissions array

### Issue: App rejected for privacy policy
**Solution**: Ensure privacy policy URL is accessible and covers location data usage

### Issue: Icon not displaying correctly
**Solution**: Use exact dimensions (512x512) and PNG format, not SVG

---

## 💰 Costs

1. **Google Play Developer Account**: $25 (one-time)
2. **EAS Build**: Free tier (limited builds) or $29/month for unlimited
3. **Hosting privacy policy**: Free (GitHub Pages, Netlify, etc.)

---

## 📞 Support Resources

- **Expo Docs**: https://docs.expo.dev
- **EAS Build**: https://docs.expo.dev/build/introduction/
- **Play Console Help**: https://support.google.com/googleplay/android-developer
- **Expo Forums**: https://forums.expo.dev

---

## ✅ Next Steps

### Immediate Actions (Assets Ready!)
1. ✅ **Icons Generated** - All required icons created in `play-store-assets/`
2. 📸 **Take Screenshots** - Capture 2-8 screenshots from your device
3. 🌐 **Host Privacy Policy** - Upload `website/` folder to make privacy policy accessible
4. 🔧 **Build App** - Run `eas build --platform android --profile production`
5. 📤 **Submit to Play Store** - Follow steps 6-16 above

### Commands to Run
```bash
# 1. Take screenshots (run app first)
npm start
# Press 'a' for Android, navigate screens, take screenshots

# 2. Install EAS CLI (if not installed)
npm install -g eas-cli

# 3. Login to Expo
eas login

# 4. Configure EAS (if not done)
eas build:configure

# 5. Build for production
eas build --platform android --profile production
```

### Resources Created
- 📁 `play-store-assets/` - All icons and graphics
- 📄 `ICON_GENERATION_COMPLETE.md` - Icon generation documentation
- 📄 `PRIVACY_POLICY.md` - Privacy policy content
- 🌐 `website/` - Website with privacy policy HTML

Good luck with your app launch! 🚀
