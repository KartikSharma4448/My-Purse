# My Purse

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/purse_logo.png" alt="My Purse wallet" width="160" />
</p>

My Purse is a native Android card and document organizer built with Kotlin and Jetpack Compose. It keeps vault data on the device, with no account, cloud sync, advertising, analytics, or Internet permission.

The app opens with a bundled startup video and a minimal Cards / Documents dashboard: a lavender ribbon, vertically swiping cards, side controls, and two bottom tabs. The first launch starts with an empty vault. This project is under active development.

## Included

- Bundled `loading-animation.mp4` intro on a fresh launch; skip, completion, error and timeout handling.
- Cards and Documents canvases with floating add / remove controls and two-tab navigation.
- Bank cards with RuPay/Visa/Mastercard type, bank, holder, number, valid-upto date and optional masked CVV on the back; copy individual fields and flip the card.
- ID cards with encrypted front/back image uploads, animated flip and image copy. Existing version-one cards migrate without resetting the vault.
- Vertical snapping card/document carousel with scale, tilt and shared connected split-ribbon paths.
- Android system picker for PDF, JPEG, PNG and WebP, verified by file signature; imports copy the document into the vault.
- Document categories during import and editing, favorites, rename, encrypted file deduplication and storage usage.
- PDF page navigation, image/PDF pinch zoom, and explicitly requested sharing through a scoped FileProvider.
- Explicit clipboard copying of text or ID images; sensitive clips are marked for Android clipboard privacy handling. Image pasting depends on the receiving app.
- Biometric / device-authentication components (see current limitations below).
- Room + SQLCipher database; Tink streaming authenticated encryption for documents; keys protected using Android Keystore.
- App-private transient decrypted previews, deleted on close; old preview/share caches and incomplete imports cleaned on cold start.
- Backups/device transfer excluded. Uninstall or Clear Data deletes the vault. Original imports are not modified.
- Adaptive bounded layouts for phones, tablets, foldables and large font sizes; Android 8.0+.

## Build

Clone the repository and open its root folder in Android Studio. Install Android SDK 36 and use JDK 17+ (Android Studio's bundled JDK is suitable). Let Gradle sync, select a device or emulator, and run the `app` configuration. No signing credentials are needed for development.

The included wrapper uses Gradle 8.14.3. A clean build requires Internet access to download dependencies; the installed app works offline.

On macOS / Linux:

```sh
chmod +x gradlew
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

On Windows PowerShell, with your JDK and Android SDK configured:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
Release bundle: `app/build/outputs/bundle/release/app-release.aab`.

## Release

Use your own upload keystore. Copy `keystore.properties.example` to `keystore.properties`, fill in your local signing configuration, and run `./gradlew bundleRelease` (`.\gradlew.bat bundleRelease` on Windows). Without signing configuration, the bundle is unsigned and cannot be uploaded as-is. Keystores, passwords, build outputs, and local configuration are excluded by `.gitignore`. Keep a secure backup of your signing credentials and never commit them.

See [Google Play preparation](docs/PLAY_STORE.md) and the [privacy-policy template](docs/PRIVACY_POLICY.md). Building a bundle does not publish an app to Google Play. A fork needs its own application ID, upload key, listing assets, reviewed privacy policy, and applicable testing/review steps.

This app stores card reference details; it does not process payments or provision NFC/Google Wallet cards. Screenshot protection is enabled in release builds. Debug builds permit screenshots for development verification.

## Project Layout

```text
app/src/main/java/com/mypurse/vault/
  data/                 Encrypted storage, models, and validation
  ui/                   Compose screens and viewers
  MainActivity.kt       Android entry point
  VaultViewModel.kt      Vault state and operations
app/src/main/res/       App resources and launcher artwork
app/src/test/           Local validation tests
app/src/androidTest/    Device and emulator tests
app/schemas/            Room schema history
startup-assets/         Packaged startup video
docs/                   Release, privacy, and verification notes
```

## Current Limitations

App-lock and settings components exist, but their entry point is currently disconnected from the minimal dashboard. Root / malware detection is not implemented. Portable encrypted backup / restore is not part of this version. Uninstalling the app or clearing its data deletes the vault; keep originals of irreplaceable documents.

Encryption at rest does not guarantee protection on a compromised or unlocked device. Copying and sharing intentionally make selected data available outside the vault. Emulator coverage does not guarantee behavior across all devices; test changes on real hardware as well.

## Contributing and Security

See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md). Use synthetic card details and documents in tests, screenshots, and issues.

## License

Source and bundled project assets are available under the [MIT License](LICENSE). Third-party dependencies remain under their respective licenses.
