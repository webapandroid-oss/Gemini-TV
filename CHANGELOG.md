# Changelog

All notable changes to **Gemini TV** are documented in this file following [Semantic Versioning](https://semver.org/).

## [3.0.0] - 2026-11-12

### Added
- **Dedicated Android TV Manifest & Leanback Support:** Configured `android.software.leanback` (`required="true"`), `android.hardware.type.television` (`required="true"`), `android.hardware.touchscreen` (`required="false"`), and `LEANBACK_LAUNCHER`.
- **Full D-Pad Remote Navigation & 10-ft TV UI:** Rebuilt UI with high-contrast focus halos, spring scale animations, 2-column TV suggestion grid, D-pad Up/Down/Left/Right escape from text inputs, OK/Center submission, and Back navigation.
- **64-bit & 32-bit Android TV Release Variants:** Updated GitHub Actions release workflow to compile, align, and v1+v2+v3 sign separate `arm64-v8a` (64-bit) and `armeabi-v7a` (32-bit) Android TV APKs.
- **Direct Gemini 3.8 Integration (`gemini-3.8-flash`):** Upgraded the primary AI engine directly to Google's latest Gemini 3.8 Flash model for high-speed conversational AI, coding assistance, and multimodal queries.
- **Direct Gemini 3.8 Pro Integration (`gemini-3.8-pro`):** Added direct support for Gemini 3.8 Pro with expanded `maxOutputTokens` (`8192`) for complex reasoning, software architecture, and advanced code synthesis.
- **Live Model Switcher in Top Bar & Settings:** Added one-tap/D-pad model selector pills (`3.8` and `3.8 Pro`) directly on the main screen as well as in the Settings dialog.
- **In-App Release Notes Viewer (`v3.0.0`):** Added an interactive `v3.0.0` release badge and changelog dialog inside the app.
- **Edge-to-Edge Window Insets & TV Overscan Padding:** Enabled `enableEdgeToEdge()` with `WindowInsets.safeDrawing` and TV overscan-safe margins.

### Changed
- **Semantic Version Bump:** Updated `versionName` to `3.0.0` and `versionCode` to `6` across `app/build.gradle.kts`, `AndroidManifest.xml`, and `.github/workflows/apk-packager.yaml`.
- **Automatic Legacy Model Migration:** Existing preferences referencing deprecated `gemini-1.5-*`, `gemini-2.0-*`, or `gemini-2.5-*` models automatically upgrade to `gemini-3.8-flash` or `gemini-3.8-pro`.
- **Expanded Multi-Turn Context:** Increased conversational history window from 6 to 12 recent turns in `GeminiService`.
- **Offline Knowledge Engine Refresh:** Updated `GeminiService` on-device responses and metadata strings to reflect Gemini 3.8 & Gemini 3.8 Pro.

---

## [2.1.0]

### Added
- Initial Gemini 2.0 Flash support, Nano Banana image generation, voice dictation, TTS playback, and Android TV hardware actions.
