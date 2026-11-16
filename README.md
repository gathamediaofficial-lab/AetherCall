# 📞 Aether Call (com.aether.call)

**Aether Call** is a production-grade, local-first native Android dialer and telephony application engineered with **Kotlin**, **Jetpack Compose**, and Android's official Telecom Framework (`InCallService` & `CallScreeningService`).

It combines the clean, edge-to-edge Apple iOS 18 Contact Poster in-call experience with the ergonomic one-handed speed of Samsung One UI contact and keypad management—while guaranteeing zero data collection and complete on-device privacy.

---

## ✨ What's New in v1.1.4

* 🖼️ **Ergonomic Glassmorphic Contact Photo Container**: Replaced full-screen stretched cropping with a centered high-resolution photo box taking ~50% of screen height, styled with a frosted glass structure (`Color.White.copy(0.08f)`) and continuous vertical gradient transparent border (`Color.White.copy(0.40f)` → `Color(0xFF10B981).copy(0.35f)`). Protects header text (`top = 160.dp`) and dynamically readjusts height on active answered calls (`bottom = 330.dp`) to avoid button collision while maintaining 100% full HD aspect ratio (`ContentScale.Fit`).
* ✂️ **Interactive Photo Customization & Cropping Suite (`PhotoCropperEditorDialog`)**: Real-time 2D pan/drag, 1.0x–4.0x pinch-to-zoom & smooth zoom slider, 90° step rotation, 1-tap reset, and high-res 92% JPEG export to app internal storage.
* 🛡️ **End-to-End Spam Protection, Spam Reporting & Number Blocking**: Built a unified multi-layered spam protection architecture. Added 1-tap **"Block & Report Spam"** in call overflow menu (`BlockAndReportSpamDialog`), block action buttons in Recents call log rows, contact profile detail sheet block tile, and settings protection controls with categorization (`Spam / Telemarketer`, `Robocall`, `Scam / Fraud`, `Personal / Unwanted`).
* 🎙️ **Unified High-Performance Call Recording Engine**: Replaced PFD streams with direct POSIX file output in `Environment.DIRECTORY_MUSIC/Aether Call Recordings`, eliminating 0-byte/empty saved files. Auto-publishes non-zero byte AAC/M4A recordings to system MediaStore with `IS_PENDING = 0` and `MediaScannerConnection` sync.
* ⚡ **2-Stage Staged Call History Loading**: Stage 1 queries past 7 days history instantly (< 5ms); Stage 2 loads older historical records asynchronously in the background.
* 🔢 **Vertical T9 Predictive Dialpad Search**: Converted predictive T9 contact matches from `LazyRow` to a vertical `LazyColumn` above the keypad with full-width cards, high-res avatars, contact labels, and 1-tap green Call buttons.
* ⚙️ **Compact & Productive Call Settings Redesign**: Redesigned settings into 5 clean, compact, category-header sections (`PROTECTION & SECURITY`, `APPEARANCE & DISPLAY`, `AUDIO & CALL CONTROLS`, `NETWORK & SIM SERVICES`, `SYSTEM & ABOUT`) with 18.dp rounded cards and inline productivity switches.
* 👥 **Multi-Party Call & Conference Engine (`Call.conference()`)**: Native multi-party call management supporting 2+ active lines, Call Waiting, auto-holding active calls on answer, and automatic line unhold/resume when a call disconnects. Features interactive **"Merge calls"** (`Icons.Default.CallMerge`) and **"Swap"** (`Icons.Default.SwapCalls`) grid controls, invoking `primaryCall.conference(secondaryCall)` and `mergeConference()` to merge active lines into a carrier conference call (Jio, Airtel, Vodafone, T-Mobile, AT&T, Verizon).
* 📹 **Carrier ViLTE Answer Stabilization**: Enhanced `AetherInCallService.answer(asVideo = true)` to unconditionally dispatch `VideoProfile.STATE_BIDIRECTIONAL` to Telecom and carrier IMS stack.

---

## 🌟 Core Features

### 1. Advanced In-Call Experience & Security
* **Ergonomic Glassmorphic Contact Photo**: 50% screen height centered HD contact photo card with continuous gradient glass border and zero text or button overlap.
* **In-Call Screen Lock & Touch Protection**: Lock active audio and video call screens with 1 tap to eliminate accidental in-call touches.
* **App-Wide Theme Control**: Live selection between **Light Mode**, **Dark Mode**, and **System Default** directly from Settings.
* **Dual Incoming Call Layouts**: Switch anytime in Settings between iOS Style (*"Slide to Answer"*) with shimmer track and One UI Style (*"Dual Action Buttons"*).
* **Built-In Call Recording**: Record calls locally in high-clarity `.m4a` format directly into public device storage (`Music/Aether Call Recordings` via Android MediaStore) with instant playback and sharing.
* **Smart Sensors & Gestures**:
  * **Flip to Silence**: Flip the phone face-down on a flat surface to silence incoming ringtones.
  * **Proximity Wake-Lock**: Automatically turns off the display when held to your ear during earpiece calls.
  * **Caller Name Announcer (TTS)**: Optionally reads the incoming caller's name aloud.
* **Picture-in-Picture (PiP) Mode**: Minimizes active calls into a floating window with live audio equalizer bars when navigating to the home screen.

### 2. Smart Dialer, Recents & Multi-Account Contacts
* **Vertical T9 Smart Keypad Search**: Instant incremental matching across contact names and phone numbers in a vertical scrollable card list.
* **Photo Customization Suite**: Crop, scale (1x–4x zoom slider), drag/pan, rotate 90°, and export contact profile photos in high quality.
* **Multi-Layered Spam Shield & Blocking**: Block calls from unknown numbers, report spam with custom reasons (`Spam`, `Robocall`, `Scam`, `Unwanted`), and block directly from active calls, call logs, or contact details.
* **Swipe-to-Call / Swipe-to-Message**: Swipe right on any contact or call log row to place a call, or swipe left to jump straight into an SMS conversation.
* **Rich Contact Cards & Editor**:
  * Support for multiple phone numbers with 1-tap Primary / Default Number selection.
  * Multiple email addresses (with email app chooser), physical addresses (with 1-tap Google Maps navigation), and Birthdays/Anniversaries (with 1-tap recurring Calendar reminder sync).
  * Emergency Contact (ICE / SOS) toggle and QR Code vCard generator for instant offline contact sharing.
  * Multi-location contact saving across Device storage, SIM cards, and synced Google accounts.

---

## 🔒 Privacy & Security Architecture

Aether Call is built on a strict **Zero Data Collection** policy:
* **No Cloud Servers or Trackers**: Zero analytics SDKs, zero crash-reporting telemetry, and zero ads.
* **100% On-Device Storage**: All contacts (`ContactsContract`), call logs (`CallLog.Calls`), custom contact posters (Room SQLite), preferences (Jetpack DataStore), and call recordings (`MediaStore`) never leave your phone.
* **Network Transparency**: The `INTERNET` permission is used exclusively when you manually tap *"Check for updates"* in Settings → About Aether Call to query the official GitHub Releases API (`api.github.com/repos/gathamediaofficial-lab/AetherCall/releases/latest`).

📄 Read our full Privacy Policy: [Privacy Policy](privacy-policy.html)

---

## 📥 Installation & Setup Guide (Android 8.0 – Android 15+)

1. **Download the APK**:
   Download the latest signed release APK (`AetherCall-v1.1.4.apk`) from the GitHub Releases page.

2. **Unlock Restricted Settings (Android 13 / 14 / 15+ Users)**:
   Because the APK is installed directly from GitHub outside the Google Play Store, Android 13+ applies Enhanced Confirmation Mode:
   - Open **Settings** → **Apps** → **Aether Call**.
   - Tap the **⋮** (three dots) menu in the top-right corner and select *"Allow restricted settings"*.

3. **Complete In-App Setup Banner**:
   Open Aether Call and use the guided setup card at the top of the screen to:
   - Set Aether Call as your **Default Phone App** (`ROLE_DIALER`).
   - Grant runtime permissions (Phone, Call Logs, Contacts, Microphone, Camera for ViLTE Video Calls, SMS for Quick Replies).
   - Enable **Full-Screen Intent alerts** (Android 14+), **Appear on Top (Overlay)**, and **Unthrottled Performance (Battery Optimization Exemption)** so background sync and incoming call alerts operate with 100% responsiveness.

---

## 🛠️ Tech Stack & Project Structure

* **UI Framework**: 100% Jetpack Compose (Material 3, `AetherTheme` Light/Dark/System engine, Custom Canvas Equalizers, Edge-to-Edge Window Insets)
* **Telephony Core**: `android.telecom.InCallService`, `CallScreeningService`, `TelecomManager`, `SubscriptionManager`, `VideoProfile` (ViLTE/IMS)
* **Local Persistence**: Room SQLite Database (Contact Posters, Blocked Numbers, Quick Reply Templates) + Jetpack DataStore Preferences
* **Media & Imaging**: Android MediaStore (`Audio.Media`), `CallRingtonePlayer`, `CallRecordingManager`, `PhotoCropperEditorDialog`, Coil (`AsyncImage`), ZXing (vCard QR Code generation)

```
com.aether.call
├── data/               # Room Database, CallLogRepository, ContactsRepository, AppSettingsManager, SpamBlockEntity
├── telecom/            # AetherInCallService, CallManager, CallBridge, CallNotificationManager,
│                       # ActiveCallNotificationBuilder, CallRingtonePlayer, CallRecordingManager, CallerIdResolver
├── ui/                 # InCallActivity, InCallScreen, CarrierVideoCallScreen, DialerMainScreen,
│                       # OneUiContactsScreen, OneUiContactSheets, PhotoCropperEditorDialog, AboutSettingsScreen, OneUiSettingsScreen
│   └── theme/          # AetherTheme (Light / Dark / System Default Theme Engine)
├── util/               # PhoneNormalizer, SimAccountResolver, ToastUtils, GitHubUpdateChecker
└── widget/             # FavoritesWidgetReceiver (Glance Speed-Dial Widget)
```

---

## 🏗️ Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/gathamediaofficial-lab/AetherCall.git
   cd AetherCall
   ```

2. Build the Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

3. To build a signed Release APK, create a `key.properties` file in the root directory pointing to your `.jks` keystore and run:
   ```bash
   ./gradlew assembleRelease
   ```

---

## 📬 Feedback & Contact

Found a bug or have a feature request? Please open an issue on our GitHub Issues page.

* **Developer**: Gatha Media
* **Contact**: gathamediaofficial@gmail.com
* **Repository**: [github.com/gathamediaofficial-lab/AetherCall](https://github.com/gathamediaofficial-lab/AetherCall)
