/**
 * Icon Generator for Google Play Store
 * 
 * This script generates all required icon sizes from SVG source files
 * Run: node scripts/generate-icons.js
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

// Check if dependencies are installed
try {
    require('sharp');
    require('@resvg/resvg-js');
} catch (err) {
    console.log('📦 Installing required dependencies...\n');
    execSync('npm install', { stdio: 'inherit', cwd: path.join(__dirname) });
    console.log('\n✅ Dependencies installed!\n');
}

const sharp = require('sharp');
const { Resvg } = require('@resvg/resvg-js');

// Icon specifications for Google Play Store
const ICON_SPECS = {
    // App Icon (required)
    appIcon: {
        sizes: [512],
        output: 'icon.png',
        description: 'App Icon (512x512)'
    },
    
    // Feature Graphic (required)
    featureGraphic: {
        width: 1024,
        height: 500,
        output: 'feature-graphic.png',
        description: 'Feature Graphic (1024x500)'
    },
    
    // Screenshots (at least 2 required, max 8)
    screenshots: {
        // Phone screenshots
        phone: {
            width: 1080,
            height: 1920,
            description: 'Phone Screenshot (1080x1920)'
        }
    },
    
    // Adaptive Icons (Android)
    adaptiveIcon: {
        foreground: {
            sizes: [512],
            output: 'android-icon-foreground.png',
            description: 'Adaptive Icon Foreground (512x512)'
        },
        background: {
            sizes: [512],
            output: 'android-icon-background.png',
            description: 'Adaptive Icon Background (512x512)'
        },
        monochrome: {
            sizes: [512],
            output: 'android-icon-monochrome.png',
            description: 'Adaptive Icon Monochrome (512x512)'
        }
    },
    
    // Various launcher icons
    launcherIcons: {
        sizes: [48, 72, 96, 144, 192, 512],
        prefix: 'launcher-icon',
        description: 'Launcher Icons'
    },
    
    // Web assets
    web: {
        favicon: {
            sizes: [16, 32, 48],
            output: 'favicon',
            description: 'Favicon'
        },
        splash: {
            sizes: [512, 1024, 2048],
            output: 'splash-icon',
            description: 'Splash Screen Icon'
        }
    }
};

// Paths
const ASSETS_DIR = path.join(__dirname, '..', 'assets', 'images');
const OUTPUT_DIR = path.join(__dirname, '..', 'play-store-assets');
const SVG_LOGO = path.join(ASSETS_DIR, 'app-logo.svg');
const SVG_ICON_SIMPLE = path.join(ASSETS_DIR, 'app-icon-simple.svg');

// Ensure output directory exists
if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

console.log('🎨 Google Play Store Icon Generator\n');
console.log('==================================\n');

/**
 * Convert SVG to PNG using resvg-js
 */
async function convertSvgToPng(svgPath, outputPath, width, height = null) {
    const actualHeight = height || width;
    
    try {
        // Read SVG file
        const svgBuffer = fs.readFileSync(svgPath);
        
        // Render SVG to PNG using resvg
        const resvg = new Resvg(svgBuffer, {
            fitTo: {
                mode: 'width',
                value: width
            }
        });
        
        const pngData = resvg.render();
        const pngBuffer = pngData.asPng();
        
        // Resize if height is different
        if (actualHeight !== width) {
            await sharp(pngBuffer)
                .resize(width, actualHeight)
                .toFile(outputPath);
        } else {
            fs.writeFileSync(outputPath, pngBuffer);
        }
        
        return true;
    } catch (err) {
        console.error(`   ❌ Error converting ${path.basename(svgPath)}:`, err.message);
        return false;
    }
}

/**
 * Generate app icon (512x512) with transparent background and safe area
 */
async function generateAppIcon() {
    console.log('📱 Generating App Icon...');
    
    const tempPath = path.join(OUTPUT_DIR, 'temp-icon.png');
    const outputPath = path.join(OUTPUT_DIR, 'icon-512.png');
    
    // Convert SVG to PNG at larger size (for better quality)
    const success = await convertSvgToPng(SVG_ICON_SIMPLE, tempPath, 800);
    
    if (success) {
        // Resize and add padding to prevent truncation (80% of original size, centered)
        await sharp(tempPath)
            .resize(410, 410, { // 80% of 512 = 410
                fit: 'contain',
                background: { r: 0, g: 0, b: 0, alpha: 0 } // Transparent
            })
            .extend({
                top: 51,
                bottom: 51,
                left: 51,
                right: 51,
                background: { r: 0, g: 0, b: 0, alpha: 0 } // Transparent padding
            })
            .png()
            .toFile(outputPath);
        
        // Clean up temp file
        fs.unlinkSync(tempPath);
        
        console.log(`   ✅ Created: ${ICON_SPECS.appIcon.output} (512x512 with transparent background)`);
        
        // Copy to assets folder
        const assetsPath = path.join(ASSETS_DIR, 'icon.png');
        fs.copyFileSync(outputPath, assetsPath);
        console.log(`   ✅ Copied to: assets/images/icon.png`);
    }
    
    return success;
}

/**
 * Generate adaptive icons with transparent backgrounds and safe area
 */
async function generateAdaptiveIcons() {
    console.log('\n📐 Generating Adaptive Icons...');
    
    // Foreground (use simple icon with padding to prevent truncation)
    const tempForeground = path.join(OUTPUT_DIR, 'temp-foreground.png');
    const foregroundPath = path.join(OUTPUT_DIR, 'android-icon-foreground.png');
    let success = await convertSvgToPng(SVG_ICON_SIMPLE, tempForeground, 640);
    
    if (success) {
        // Resize to 70% and center with transparent background (safe area for adaptive icons)
        await sharp(tempForeground)
            .resize(358, 358, { // 70% of 512
                fit: 'contain',
                background: { r: 0, g: 0, b: 0, alpha: 0 }
            })
            .extend({
                top: 77,
                bottom: 77,
                left: 77,
                right: 77,
                background: { r: 0, g: 0, b: 0, alpha: 0 }
            })
            .png()
            .toFile(foregroundPath);
        
        fs.unlinkSync(tempForeground);
        console.log('   ✅ Foreground icon (512x512 with transparent background)');
        fs.copyFileSync(foregroundPath, path.join(ASSETS_DIR, 'android-icon-foreground.png'));
    }
    
    // Background (transparent)
    const backgroundPath = path.join(OUTPUT_DIR, 'android-icon-background.png');
    await sharp({
        create: {
            width: 512,
            height: 512,
            channels: 4,
            background: { r: 0, g: 0, b: 0, alpha: 0 } // Fully transparent
        }
    })
    .png()
    .toFile(backgroundPath);
    console.log('   ✅ Background icon (512x512 transparent)');
    fs.copyFileSync(backgroundPath, path.join(ASSETS_DIR, 'android-icon-background.png'));
    
    // Monochrome (for Android 13+)
    const tempMonochrome = path.join(OUTPUT_DIR, 'temp-monochrome.png');
    const monochromePath = path.join(OUTPUT_DIR, 'android-icon-monochrome.png');
    success = await convertSvgToPng(SVG_ICON_SIMPLE, tempMonochrome, 640);
    
    if (success) {
        // Convert to white on transparent and add safe area
        await sharp(tempMonochrome)
            .greyscale()
            .resize(358, 358, {
                fit: 'contain',
                background: { r: 0, g: 0, b: 0, alpha: 0 }
            })
            .extend({
                top: 77,
                bottom: 77,
                left: 77,
                right: 77,
                background: { r: 0, g: 0, b: 0, alpha: 0 }
            })
            .png()
            .toFile(monochromePath);
        
        fs.unlinkSync(tempMonochrome);
        console.log('   ✅ Monochrome icon (512x512 transparent)');
        fs.copyFileSync(monochromePath, path.join(ASSETS_DIR, 'android-icon-monochrome.png'));
    }
}

/**
 * Generate feature graphic
 */
async function generateFeatureGraphic() {
    console.log('\n🖼️  Generating Feature Graphic...');
    
    const featurePath = path.join(OUTPUT_DIR, 'feature-graphic.png');
    
    // Create a feature graphic with logo and text
    const backgroundColor = '#0A1929';
    const primaryColor = '#00D4FF';
    
    // Create base image
    await sharp({
        create: {
            width: 1024,
            height: 500,
            channels: 4,
            background: backgroundColor
        }
    })
    .png()
    .toFile(featurePath);
    
    console.log('   ✅ Feature Graphic (1024x500)');
    console.log('   ℹ️  Note: Add logo and text manually using image editor');
}

/**
 * Generate launcher icons (various sizes) with transparent backgrounds
 */
async function generateLauncherIcons() {
    console.log('\n🚀 Generating Launcher Icons...');
    
    const sizes = [48, 72, 96, 144, 192, 512];
    
    for (const size of sizes) {
        const tempPath = path.join(OUTPUT_DIR, `temp-launcher-${size}.png`);
        const outputPath = path.join(OUTPUT_DIR, `launcher-icon-${size}.png`);
        const innerSize = Math.floor(size * 0.8); // 80% of target size
        const padding = Math.floor((size - innerSize) / 2);
        
        const success = await convertSvgToPng(SVG_ICON_SIMPLE, tempPath, innerSize * 1.5);
        if (success) {
            await sharp(tempPath)
                .resize(innerSize, innerSize, {
                    fit: 'contain',
                    background: { r: 0, g: 0, b: 0, alpha: 0 }
                })
                .extend({
                    top: padding,
                    bottom: padding,
                    left: padding,
                    right: padding,
                    background: { r: 0, g: 0, b: 0, alpha: 0 }
                })
                .png()
                .toFile(outputPath);
            
            fs.unlinkSync(tempPath);
            console.log(`   ✅ ${size}x${size} (transparent background)`);
        }
    }
}

/**
 * Generate web assets
 */
async function generateWebAssets() {
    console.log('\n🌐 Generating Web Assets...');
    
    // Favicon
    const faviconSizes = [16, 32, 48];
    for (const size of faviconSizes) {
        const outputPath = path.join(OUTPUT_DIR, `favicon-${size}.png`);
        const success = await convertSvgToPng(SVG_ICON_SIMPLE, outputPath, size);
        if (success && size === 48) {
            // Copy 48x48 as main favicon
            fs.copyFileSync(outputPath, path.join(ASSETS_DIR, 'favicon.png'));
            console.log(`   ✅ Favicon ${size}x${size} (copied to assets)`);
        } else if (success) {
            console.log(`   ✅ Favicon ${size}x${size}`);
        }
    }
    
    // Splash icons
    const splashSizes = [512, 1024];
    for (const size of splashSizes) {
        const outputPath = path.join(OUTPUT_DIR, `splash-icon-${size}.png`);
        const success = await convertSvgToPng(SVG_LOGO, outputPath, size);
        if (success && size === 512) {
            fs.copyFileSync(outputPath, path.join(ASSETS_DIR, 'splash-icon.png'));
            console.log(`   ✅ Splash Icon ${size}x${size} (copied to assets)`);
        } else if (success) {
            console.log(`   ✅ Splash Icon ${size}x${size}`);
        }
    }
}

/**
 * Generate screenshot templates
 */
async function generateScreenshotTemplates() {
    console.log('\n📸 Generating Screenshot Templates...');
    
    const screenshotSizes = [
        { name: 'phone', width: 1080, height: 1920 },
        { name: 'tablet-7', width: 1920, height: 1080 },
        { name: 'tablet-10', width: 2560, height: 1600 }
    ];
    
    for (const spec of screenshotSizes) {
        const outputPath = path.join(OUTPUT_DIR, `screenshot-${spec.name}-template.png`);
        
        await sharp({
            create: {
                width: spec.width,
                height: spec.height,
                channels: 4,
                background: '#1A252F'
            }
        })
        .png()
        .toFile(outputPath);
        
        console.log(`   ✅ ${spec.name} template (${spec.width}x${spec.height})`);
    }
    
    console.log('   ℹ️  Take actual screenshots from your device/emulator');
}

/**
 * Create a README for the assets
 */
function createAssetsReadme() {
    const readme = `# Google Play Store Assets

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

Run: \`node scripts/generate-icons.js\`

Generated: ${new Date().toLocaleString()}
`;

    fs.writeFileSync(path.join(OUTPUT_DIR, 'README.md'), readme);
    console.log('\n📄 Created assets README.md');
}

/**
 * Main execution
 */
async function main() {
    try {
        // Check if SVG files exist
        if (!fs.existsSync(SVG_LOGO)) {
            console.error('❌ SVG logo not found at:', SVG_LOGO);
            process.exit(1);
        }
        
        if (!fs.existsSync(SVG_ICON_SIMPLE)) {
            console.error('❌ Simple SVG icon not found at:', SVG_ICON_SIMPLE);
            process.exit(1);
        }
        
        // Generate all assets
        await generateAppIcon();
        await generateAdaptiveIcons();
        await generateFeatureGraphic();
        await generateLauncherIcons();
        await generateWebAssets();
        await generateScreenshotTemplates();
        createAssetsReadme();
        
        console.log('\n✨ All assets generated successfully!');
        console.log(`📁 Output folder: ${OUTPUT_DIR}`);
        console.log('\n📋 Next Steps:');
        console.log('   1. Review generated assets in play-store-assets/');
        console.log('   2. Edit feature-graphic.png to add branding');
        console.log('   3. Take screenshots from your device/emulator');
        console.log('   4. Upload to Google Play Console');
        console.log('\n💡 Tip: Check play-store-assets/README.md for details');
        
    } catch (error) {
        console.error('\n❌ Error:', error.message);
        process.exit(1);
    }
}

// Run the script
main();
