# ORION FILES
**ALL YOUR FILES. ONE PLACE.**

Offline-first Android file manager. Package: `com.orion.files`

## Visual direction
- AMOLED-first true black surfaces
- Electric blue + violet accent palette
- Translucent glass-style panels with restrained blur
- Smooth Compose transitions, animated content size, animated selection and navigation
- Designed to respect Android reduced-motion / performance constraints as the app grows

## Planned feature set
1. Home dashboard and storage overview
2. File browser: folders, search, sorting, copy, move, rename, delete
3. Favorites and recent files
4. Storage analyzer and large-file finder
5. Exact duplicate finder
6. ZIP create/extract and password-protected ZIP
7. PDF preview, image-to-PDF, merge and split
8. Media browser for images/videos
9. Secure Vault: PIN, biometric unlock, Android Keystore-backed encryption, auto-lock
10. QR scanner, generator and local history
11. Bengali + English, dark/light-independent AMOLED visual theme, settings

## Current starter build
This is the first project foundation: Android app shell, animated aesthetic dashboard, navigation placeholders, and GitHub Actions debug APK workflow. Feature tiles are UI entry points/placeholders until their modules are implemented and tested. Do not treat the Vault or file operations as implemented in this starter.

## Privacy
Core app architecture is offline-first. No login, cloud sync, analytics, or file upload is included in this starter. Storage access should be requested contextually through Android's Storage Access Framework / media permissions.

## Build
Push the project to GitHub. Open **Actions → Android Debug APK → Run workflow** (or push a commit). Download the APK from the successful run's Artifacts.

- Minimum Android: API 26
- Compile/Target SDK: 35
- Kotlin + Jetpack Compose + Material 3
