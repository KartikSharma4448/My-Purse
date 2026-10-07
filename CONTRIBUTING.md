# Contributing to My Purse

Keep changes focused and follow the existing Kotlin / Compose patterns. Explain the problem, resulting behavior, and verification in your pull request.

Install JDK 17+ and Android SDK 36, then open the root folder in Android Studio. Use the Gradle wrapper rather than a system Gradle installation.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Run `connectedDebugAndroidTest` on a device or emulator when changing persistence, encryption, migrations, clipboard behavior, or UI workflows. Add focused tests for new behavior; preserve existing Room schemas and add a migration for database changes.

Use synthetic data for tests and screenshots. Never commit vault databases, real card or ID images, signing keys, passwords, or local configuration. Preserve the offline architecture unless a proposed change explicitly explains the need for networking and updates the privacy documentation.

Include only artwork and media that you have the right to redistribute. Keep packaged assets reasonably sized and third-party license notices intact.

Bug reports should include the Android version, device type, app version, reproduction steps, expected behavior, and actual behavior. Remove private information from screenshots and logs. Report security vulnerabilities using [SECURITY.md](SECURITY.md).
