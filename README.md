# Ember V12 Android

Native Android host for the Ember V12 interface supplied in the project brief.

## Build

Push to `android-rebuild` or run **Build Ember V11 Android APK** from GitHub Actions. The workflow builds a debug APK and uploads it as an Actions artifact.

## Architecture

- Kotlin Android activity hosts the original Ember interface in a full-screen WebView.
- The original interface is stored at `app/src/main/assets/index.html`.
- A JavaScript bridge provides Gemini requests, Android text-to-speech, microphone permission, device metrics, HTTPS link opening, clock timer/alarm intents, downloads and encrypted local key storage.
- The Android activity starts in landscape orientation and supports portrait sensor rotation.

## Current limitations

This is a native host foundation, not complete parity with every action exposed by the HTML interface. Cross-app typing, floating-window controls, always-on wake-word detection, Vosk model integration, USB serial ESP32 operations, scheduled reminders and app automation are not implemented yet. The UI must not report these as completed native capabilities.

The Gemini key is stored using AndroidX EncryptedSharedPreferences backed by Android Keystore. Use a private device and protect the device lock screen.
