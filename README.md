# JARVIS-X Autonomous AI Agent

JARVIS-X is an autonomous Android AI Agent equipped with local voice processing, Gemini AI integration, device automation triggers, Notification Listener auto-reply, and screen reading capabilities via Accessibility Services.

## Features
- **Clean Dark Theme & Moving Rainbow Frame UI**: Pitch-black AMOLED canvas with dynamic moving rainbow border accents.
- **Arc Reactor Voice & Command Core**: Visualizes voice state (Listening, Processing, Speaking, Idle) with glowing animated rings.
- **Background Automation Engine**: Monitored continuous triggers (e.g. power connection, notification auto-replies).
- **Accessibility Service & Notification Engine**: Enables UI element clicking, text input, and automated reply to messages.
- **Gemini AI Integration**: Uses Gemini model API for intent parsing and autonomous multi-step execution.

## GitHub APK Build & Deployment
This repository is configured with GitHub Actions to automatically compile and export the Android APK upon every push.

### How to build on GitHub:
1. Push this project to your GitHub repository.
2. Go to the **Actions** tab on your GitHub repository.
3. The **Android CI & APK Build** workflow will automatically run and compile the app.
4. Once completed, download the `JARVIS-X-debug-apk` artifact directly from the workflow run summary to install on your Android device.

### Local Build:
```bash
./gradlew assembleDebug
```
The compiled APK will be output to: `app/build/outputs/apk/debug/app-debug.apk`
