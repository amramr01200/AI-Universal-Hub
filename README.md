# AI Universal Hub

Android-first AI workspace starter. The initial release provides a bilingual Arabic/English shell, OpenAI-compatible provider configuration, model discovery, encrypted API-key storage, and a chat screen.

## Build
Open this project in Android Studio (JDK 17, Android SDK 35), or push it to GitHub and run **Build Android APK** from Actions.

The workflow builds a debug APK and uploads it as an artifact. A signed release APK requires repository secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Security
- API keys are stored using AndroidX Security Crypto encrypted preferences.
- Keys are never written to source files, logs, or Git.
- The first version supports OpenAI-compatible chat-completions endpoints. Provider-specific auth, OAuth, Chrome integration, Termux bridge, GitHub project agent, MCP, and automatic free-quota discovery are staged for later milestones.
- Do not import credentials from browser password stores without explicit Android/Chrome user consent and supported APIs.

## Current scope
This is a working foundation, not a finished AI platform. Free quota and reset dates are only reliable when the provider exposes them through an API; otherwise the user can set a manual reset date.
