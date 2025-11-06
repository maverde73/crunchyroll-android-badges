# Crunchyroll Language Badges - Android App

<div align="center">

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Fire TV](https://img.shields.io/badge/Platform-Fire%20TV-orange.svg)
![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Version](https://img.shields.io/badge/version-1.0-blue.svg)

**Android app companion to the Crunchyroll Language Badges browser extension**

Browse Crunchyroll with language badges, then open anime in the official app!

</div>

---

## 🎯 What is this?

This Android app brings the **Crunchyroll Language Badges** experience to Android devices and **Fire TV Stick**.

It displays Crunchyroll in a WebView with visual language badges overlaid on anime cards. When you select an anime, it automatically opens in the official Crunchyroll app.

### 🔗 Related Project

- **Browser Extension:** [crunchyroll-language-badges](https://github.com/maverde73/crunchyroll-language-badges)

---

## ✨ Features

- 🎯 **Browse Crunchyroll** with language badge overlays
- 🌐 **9 languages supported** (Italian, English, Spanish, German, French, Portuguese, Japanese, Russian)
- 🎨 **Modern badge design** with glassmorphism effect
- 📱 **D-pad navigation** support for Fire TV
- 🔄 **Seamless handoff** to Crunchyroll app when selecting anime
- ⚡ **WebView-based** - uses same code as browser extension
- 💾 **Saves preferences** locally on device

---

## 📱 Compatibility

### Supported Devices:
- ✅ **Android phones & tablets** (Android 5.0+)
- ✅ **Fire TV Stick** (all generations)
- ✅ **Android TV** boxes
- ✅ **Fire TV Cube**

### Requirements:
- **Android 5.0** (API 21) or higher
- **Crunchyroll app** installed (for launching anime)
- **Internet connection**

---

## 📥 Installation

### Method 1: APK Download (Recommended)

1. **Download APK** from [Releases](https://github.com/maverde73/crunchyroll-android-badges/releases)
2. **Enable Unknown Sources:**
   - Settings → Security → Unknown Sources → Enable
   - (Fire TV: Settings → My Fire TV → Developer Options → Apps from Unknown Sources → ON)
3. **Install APK** using file manager or ADB
4. **Launch app** from home screen

### Method 2: Build from Source

```bash
# Clone repository
git clone https://github.com/maverde73/crunchyroll-android-badges.git
cd crunchyroll-android-badges

# Build with Gradle
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Or open in Android Studio
```

### Method 3: ADB Install (Fire TV)

```bash
# Enable ADB on Fire TV:
# Settings → My Fire TV → Developer Options → ADB Debugging → ON

# Connect to Fire TV
adb connect <FIRE_TV_IP_ADDRESS>

# Install APK
adb install -r app-debug.apk

# Launch app
adb shell am start -n com.maverde.crunchybadges/.MainActivity
```

---

## 🚀 Usage

1. **Launch App** on your Android device/Fire TV
2. **Browse Crunchyroll** - Badges appear automatically on anime cards
3. **Navigate** with touch or D-pad (Fire TV)
4. **Select an anime** - Tap/click on any anime card
5. **App launches** - Crunchyroll official app opens with selected anime

### 🎮 Fire TV Navigation

- **D-Pad:** Navigate between anime cards
- **Center/OK:** Select anime (launches Crunchyroll app)
- **Back:** Go back in browsing history
- **Home:** Exit to Fire TV home

---

## 🏗️ Architecture

### Technology Stack:
- **Language:** Kotlin
- **UI:** Android XML layouts
- **WebView:** AndroidX WebKit
- **Min SDK:** 21 (Android 5.0)
- **Target SDK:** 34

### Components:

```
MainActivity.kt         → Main activity hosting WebView
WebViewManager.kt       → WebView setup + JS injection
JavaScriptBridge.kt     → JS ↔ Android communication
IntentLauncher.kt       → Launches Crunchyroll app
```

### How it Works:

1. **WebView loads Crunchyroll** website
2. **JavaScript is injected** from browser extension (content.js, injected.js)
3. **CSS is injected** for badge styling
4. **Badges appear** on anime cards automatically
5. **Click intercepted** via JavaScript Bridge
6. **Series ID extracted** from clicked URL
7. **Intent launched** to open Crunchyroll app with deep link

---

## 🔧 Development

### Prerequisites:
- Android Studio Flamingo or newer
- JDK 8 or higher
- Android SDK 34

### Project Structure:

```
crunchyroll-android-badges/
├── app/
│   ├── src/main/
│   │   ├── java/com/maverde/crunchybadges/
│   │   │   ├── MainActivity.kt
│   │   │   ├── WebViewManager.kt
│   │   │   ├── JavaScriptBridge.kt
│   │   │   └── IntentLauncher.kt
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── values/strings.xml
│   │   │   └── drawable/
│   │   ├── assets/                # Extension code
│   │   │   ├── content.js
│   │   │   ├── injected.js
│   │   │   ├── style.css
│   │   │   └── _locales/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

### Building:

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing)
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

---

## 🤝 Contributing

Contributions welcome! Please:

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

---

## 📝 License

MIT License - See [LICENSE](LICENSE) file for details.

---

## 🐛 Known Issues

- **Deep linking:** Crunchyroll app may not support all deep link schemes. Fallback to web browser is provided.
- **Performance:** WebView can be slower than native app on older Fire TV devices.
- **UI updates:** If Crunchyroll website changes, badges may not appear correctly.

---

## 📧 Support

- **Issues:** [GitHub Issues](https://github.com/maverde73/crunchyroll-android-badges/issues)
- **Browser Extension:** [crunchyroll-language-badges](https://github.com/maverde73/crunchyroll-language-badges)

---

<div align="center">

**Made with ❤️ for anime fans on Android & Fire TV**

[Browser Extension](https://github.com/maverde73/crunchyroll-language-badges) • [Report Bug](https://github.com/maverde73/crunchyroll-android-badges/issues) • [Request Feature](https://github.com/maverde73/crunchyroll-android-badges/issues)

</div>
