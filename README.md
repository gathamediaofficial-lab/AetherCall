# Aether Call 📞✨

**Aether Call** is a production-grade, highly reliable, and aesthetically premium native Android dialer application built from the ground up using **Kotlin** and **Jetpack Compose**. Designed for high performance and smooth user experiences, it integrates directly with Android's official Telecom framework (`InCallService` and `CallScreeningService`) to deliver a robust alternative to stock dialers.

---

## 🌟 Key Features

* **Advanced Telephony & InCall Service:** Built on Android’s official `InCallService` role, ensuring seamless handling of active, holding, and incoming calls.
* **Carrier HD Video Calling (ViLTE / IMS):** 
  * Immersive full-screen video stream with a top-left frosted status pill (caller name and duration timer).
  * Draggable Picture-in-Picture (PiP) self-camera preview with 20.dp rounded corners and tap-to-flip camera controls.
  * Bottom frosted-glass control dock (`Flip`, `Mute`, `Voice`, `End`).
* **Dual Incoming Call Layouts (Customizable via Settings):**
  * **iOS Style ("Slide-to-Answer"):** Minimalist incoming screen with top caller header, Quick Reply, Voicemail options, and an interactive horizontal slide track with animated shimmer text.
  * **One UI Style ("Dual Buttons"):** Center avatar radar with circular red decline and green answer action pods.
* **Persistent Call Recording:** Records active calls and exports them directly into the device's public **MediaStore (`Music/Aether Call Recordings`)**, making recorded `.m4a` files instantly visible in system File Managers under the Audio/Music category.
* **Smart Call Backgrounds:** Supports dynamic auto-extracted color gradients from contact avatars, custom gallery photo backgrounds, and solid AMOLED dark themes.
* **T9 Smart Dialer & Contacts:** Fast incremental T9 keypad search with multi-account contact lookup and favorite starring.
* **Privacy & Security First:** Zero data collection, local-first storage, and full compliance with Android permission sandboxes.

---

## 🛡️ Privacy, Security & Data Integrity

Aether Call is engineered with strict security boundaries:
* **Zero Data Collection:** The developer does not collect, track, upload, or share any personal data, call logs, or contacts. All data remains 100% private and secure on your local device storage.
* **Telecom Sandbox Isolation:** Operates strictly within official Android OS security boundaries utilizing SIM-based authentication (IMS AKA) and secure transport protocols.
* **MediaStore Safety:** Call recordings are finalized atomically with `IS_PENDING = 0` and indexed via `MediaScannerConnection`, preventing ghost files or partial recording corruption.

---

## 🏗️ Technical Architecture

* **UI Framework:** Jetpack Compose with Material 3 design tokens, custom smooth springs, and isolated recompositions.
* **Concurrency:** Kotlin Coroutines & `StateFlow` for thread-safe reactive state management.
* **Media & Storage:** `MediaRecorder` (AAC 128kbps, 44.1kHz), `TextureView` for hardware-accelerated video surfaces, and `MediaStore.Audio.Media` integration.
* **Updates:** Built-in **GitHub Release Auto-Updater** that queries GitHub's REST API (`/repos/{owner}/{repo}/releases/latest`) to notify users of newer app versions.

---

## ⚙️ Getting Started & Installation

### Prerequisites
* Android Studio (Koala / Jellyfish or newer)
* Android SDK 26 (Android 8.0) up to SDK 35+ (Android 15+)
* Kotlin 1.9+ / Jetpack Compose

### Building from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/gathamediaofficial-lab/AetherCall.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle dependencies and run the `:app:assembleDebug` task.
4. Deploy the APK to an Android device running API 26 or higher.
