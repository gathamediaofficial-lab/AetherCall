# 📞 Aether Call (com.aether.call)

**Aether Call** is a production-grade, local-first native Android dialer and telephony application engineered with **Kotlin**, **Jetpack Compose**, and Android's official Telecom Framework (`InCallService` & `CallScreeningService`).

It combines the clean, edge-to-edge Apple iOS 18 Contact Poster in-call experience with the ergonomic one-handed speed of Samsung One UI contact and keypad management—while guaranteeing zero data collection and complete on-device privacy.

---

## ✨ What's New in v1.1.2

* 🎨 **Active Light, Dark, and System Theme Controls**: Added full theme controls under **Settings → App theme**. Users can toggle anytime between **System default** (follows Android device theme), **Light mode** (clean One UI light theme), and **Dark mode** (AMOLED dark background), with instant UI re-theming and dynamic edge-to-edge system bar contrast adaptation.
* 🔔 **OEM Ringtone & Vibration Engine (`CallRingtonePlayer`)**: Dedicated incoming call audio and haptic engine supporting default/custom ringtones, repeating vibration patterns, flip-to-silence gestures, and hardware volume key silencing—without mutating device-wide system ringer settings.
* ⚡ **Thread-Safe UI & Async Protection (`ToastUtils`)**: Safe Toast dispatcher ensuring all status alerts across background coroutines and telephony workers post seamlessly on the Main UI thread.
* 🍎 **Pure Apple iOS 18 Full-Screen Call Interface**: Full-bleed edge-to-edge Contact Posters with multi-stop vertical readability scrims or sleek dark ambient gradients with bold top-centered typography.
* 📹 **Complete 3-Stage Carrier HD Video Calling (ViLTE / IMS)**:
  * **Incoming Video Call Screen (`STATE_RINGING`)**: Live local camera preview with options to Decline, Answer as Voice Only, or Accept Video.
  * **Active Video Call Screen (`STATE_ACTIVE`)**: Full-screen remote peer video stream, draggable Picture-in-Picture (PiP) self-view with 1-tap camera flip, and frosted controls.
  * **4-Second Ended Call Summary (`STATE_DISCONNECTED`)**: Post-call summary screen showing call duration with instant Call Again or Done actions.
* 🛡️ **Hardened Samsung One UI & OEM Call Ownership**: Unified foreground service notification binding (`ID = 1001`) with dual-channel quiet/high-importance routing (`aether_telecom_channel_v3_silent` + `aether_call_quiet_v2`) to eliminate double ringtones and prevent native OEM dialers from hijacking active calls.
* 📶 **Precision Dual-SIM Carrier Detection**: Powered by `SimAccountResolver`, Aether Call cross-matches `TelecomManager` `PhoneAccountHandle` labels, ICCIDs, subscription IDs, and physical slot indices (`SIM 1 • Jio`, `SIM 2 • Airtel`) with full "Ask every time" (`STATE_SELECT_PHONE_ACCOUNT`) support.

---

## 🌟 Core Features

### 1. Advanced In-Call Experience & Themes
* **App-Wide Theme Control**: Live selection between **Light Mode**, **Dark Mode**, and **System Default** directly from Settings.
* **Dual Incoming Call Layouts**: Switch anytime in Settings between iOS Style (*"Slide to Answer"*) with shimmer track and One UI Style (*"Dual Action Buttons"*).
* **Built-In Call Recording**: Record calls locally in high-clarity `.m4a` format directly into public device storage (`Music/Aether Call Recordings` via Android MediaStore) with instant playback and sharing.
* **Smart Sensors & Gestures**:
  * **Flip to Silence**: Flip the phone face-down on a flat surface to silence incoming ringtones.
  * **Proximity Wake-Lock**: Automatically turns off the display when held to your ear during earpiece calls.
  * **Caller Name Announcer (TTS)**: Optionally reads the incoming caller's name aloud.
* **Picture-in-Picture (PiP) Mode**: Minimizes active calls into a floating window with live audio equalizer bars when navigating to the home screen.

### 2. Smart Dialer, Recents & Multi-Account Contacts
* **T9 Smart Keypad Search**: Instant incremental matching across contact names and phone numbers with highlighted matches.
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
   Download the latest signed release APK (`AetherCall-v1.1.2.apk`) from the GitHub Releases page.

2. **Unlock Restricted Settings (Android 13 / 14 / 15+ Users)**:
   Because the APK is installed directly from GitHub outside the Google Play Store, Android 13+ applies Enhanced Confirmation Mode:
   - Open **Settings** → **Apps** → **Aether Call**.
   - Tap the **⋮** (three dots) menu in the top-right corner and select *"Allow restricted settings"*.

3. **Complete In-App Setup Banner**:
   Open Aether Call and use the guided setup card at the top of the screen to:
   - Set Aether Call as your **Default Phone App** (`ROLE_DIALER`).
   - Grant runtime permissions (Phone, Call Logs, Contacts, Microphone, Camera for ViLTE Video Calls, SMS for Quick Replies).
   - Enable **Full-Screen Intent alerts** (Android 14+) and **Appear on Top (Overlay)** so incoming calls always wake the lock screen cleanly.

---

## 🛠️ Tech Stack & Project Structure

* **UI Framework**: 100% Jetpack Compose (Material 3, `AetherTheme` Light/Dark/System engine, Custom Canvas Equalizers, Edge-to-Edge Window Insets)
* **Telephony Core**: `android.telecom.InCallService`, `CallScreeningService`, `TelecomManager`, `SubscriptionManager`, `VideoProfile` (ViLTE/IMS)
* **Local Persistence**: Room SQLite Database (Contact Posters & Quick Reply Templates) + Jetpack DataStore Preferences
* **Media & Imaging**: Android MediaStore (`Audio.Media`), `CallRingtonePlayer`, Coil (`AsyncImage`), ZXing (Offline vCard QR Code generation)

```
com.aether.call
├── data/               # Room Database, CallLogRepository, ContactsRepository, AppSettingsManager
├── telecom/            # AetherInCallService, CallManager, CallBridge, CallNotificationManager,
│                       # ActiveCallNotificationBuilder, CallRingtonePlayer, CallRecordingManager, CallerIdResolver
├── ui/                 # InCallActivity, InCallScreen, CarrierVideoCallScreen, DialerMainScreen,
│                       # OneUiContactsScreen, OneUiContactSheets, AboutSettingsScreen, OneUiSettingsScreen
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
