<div align="center">

# ⚡ JARVIS-X AUTONOMOUS AI AGENT ⚡
### *Next-Generation Autonomous AI Digital Operator for Android*

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg?style=for-the-badge&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin%201.9.22-blue.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Gemini AI](https://img.shields.io/badge/AI Engine-Gemini%201.5%20%2F%20Flash-8E44AD.svg?style=for-the-badge&logo=google)](https://ai.google.dev)
[![Build APK](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions%20APK%20Build-20B2AA.svg?style=for-the-badge&logo=githubactions)](/.github/workflows/android.yml)

---

### 🎨 **AMOLED Dark Canvas with Dynamic Animated Moving Rainbow Frame**
JARVIS-X combines a **Cyberpunk Iron Man Arc Reactor Voice Interface** with local speech recognition, dynamic package discovery for opening ANY installed application, continuous background trigger automation, and full Accessibility screen control.

</div>

---

## 🌟 Key Highlights & Capabilities

### 🎙️ **Arc Reactor Voice & Command Core**
- **Glowing Animated Visualizer**: Sweeping rainbow outer ring that reacts dynamically when JARVIS is listening, processing, speaking, or idle.
- **Voice & Text Command Input**: Continuous local speech-to-text integration with Android `SpeechRecognizer` + real-time `TextToSpeech` verbal feedback.
- **Natural Language Intent Parsing**: Powered by Google's **Gemini AI API** with intelligent local fallback engine.

### 📱 **Dynamic "Open ANY Installed App" Engine**
- **Universal Package Discovery**: Unlike hardcoded assistants, JARVIS queries all launchable applications installed on your device (`PackageManager.QUERY_ALL_PACKAGES`).
- **Flexible Matching**: Opens any app by exact label, partial name, or package substring (e.g., *"Open TikTok"*, *"Launch Spotify"*, *"Open Calculator"*, *"Start Settings"*).

### ⚙️ **Custom Gemini API Key Settings**
- **Runtime API Key Management**: Easily add, update, or clear your personal Gemini API key inside the dedicated **Settings** tab.
- **Secure Local Storage**: Safely encrypted in local `SharedPreferences` without touching `local.properties`.
- **Automatic Fallback**: Smoothly transitions between user-defined key, `BuildConfig` injected key, or local command parser.

### 🤖 **Autonomous System Automation Engine**
- **Notification Auto-Reply**: Monitors incoming notifications (WhatsApp, Telegram, Instagram, SMS) via `NotificationListenerService` and auto-replies based on active trigger rules.
- **Accessibility Service Interaction**: Can read screen elements, locate buttons by text, and perform tap/click operations (`JarvisAccessibilityService`).
- **Background Foreground Service**: Continuous background trigger monitoring for charger connections, voice keywords, or notification rules.

---

## 📸 Interface Tabs & Screens

```
┌─────────────────────────────────────────────────────────┐
│ 🔮 JARVIS-X SYSTEM NAVIGATION                         │
├───────────────┬─────────────────┬──────────────┬────────┤
│  🎙️ Command   │  ⚡ Automations │  📜 Execution│⚙️ System│
│    Engine     │     Triggers    │     Logs     │ Settings│
└───────────────┴─────────────────┴──────────────┴────────┘
```

1. **🎙️ Command Engine**: Central HUD featuring the Arc Reactor, speech input, message history, and quick-action chips.
2. **⚡ Automations**: Manage background trigger rules (e.g., auto-reply on power plug, notification filters).
3. **📜 Agent Logs**: Real-time audit trail recording every app launch, web search, and automation execution.
4. **⚙️ System Settings**: Add/Save your custom Gemini API key and view system permissions status.

---

## ⚙️ How to Add Your Gemini API Key

1. Launch **JARVIS-X** on your Android device.
2. Tap the **Settings** icon (bottom navigation bar).
3. Under **Gemini API Key Configuration**:
   - Paste your key from [Google AI Studio](https://aistudio.google.com).
   - Tap **Save Key**.
4. JARVIS-X will immediately switch to using your custom API Key for all natural language commands!

---

## 🚀 Building & Exporting APK on GitHub

This repository includes a pre-configured **GitHub Actions Workflow** that automatically builds and exports the installable Debug APK whenever code is pushed.

### 📦 Download APK from GitHub Actions:
1. Go to the **Actions** tab in this GitHub repository.
2. Click on the latest run under **Android CI & APK Build**.
3. Scroll down to the **Artifacts** section and download `JARVIS-X-debug-apk.zip`.
4. Extract the `.apk` file and install it on your Android device!

### 💻 Local Compilation Command:
```bash
# Build Debug APK locally
./gradlew assembleDebug

# Output APK path:
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🛡️ Required Android Permissions

| Permission | Purpose |
| :--- | :--- |
| `QUERY_ALL_PACKAGES` | Allows JARVIS to find and launch any app installed on the device. |
| `BIND_ACCESSIBILITY_SERVICE` | Enables screen reading and automated button clicking. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Reads incoming notifications to perform auto-replies. |
| `RECORD_AUDIO` | Local voice speech recognition via microphone. |
| `FOREGROUND_SERVICE` | Keeps automation triggers active in the background. |

---

## 🏗️ Tech Stack & Architecture

- **UI Framework**: Jetpack Compose (Material 3 with custom dark theme)
- **Architecture**: MVVM + Clean Architecture + Coroutines / StateFlow
- **Local Database**: Room DB (for automation rules and execution audit logs)
- **AI Core**: Gemini REST API + Local System Action Planner
- **Services**: `AccessibilityService`, `NotificationListenerService`, `ForegroundService`

---

<div align="center">

*Designed & Engineered for Autonomous Mobile Automation* 🚀

</div>
