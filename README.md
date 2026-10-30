# Ember V11 Android

Native Android companion for Ember V11 by Cyber Pulse.

## Build
Push to `android-rebuild` or run the **Build Ember V11 Android APK** workflow manually. GitHub Actions builds a debug APK and publishes it as a release asset.

## Install
Download `app-debug.apk` from the latest GitHub release and install it on Android. Android may ask you to allow installs from the browser you used.

## Current scope
- Native Compose chat interface with landscape-first layout and portrait support
- OpenAI-compatible chat endpoint configuration
- Speech recognition input and Android text-to-speech output
- Custom Ember launcher icon

This is an Android foundation build, not complete feature parity with the Windows edition. Local model runtime, Gemini Live, plugins, avatar lip-sync, advanced file ingestion and long-term memory remain future work.

API credentials are stored in app-private preferences in this foundation build. Do not use a shared device and do not treat this as hardware-backed encrypted secret storage.
