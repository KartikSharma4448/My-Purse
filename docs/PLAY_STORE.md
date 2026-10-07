# Google Play release preparation

This project builds a native Android application. Building it does not publish it to Google Play. The debug APK is for local evaluation. A release AAB must be signed using your own upload key before submission.

## Before your first upload

1. Confirm you own the final application ID. The current ID is `com.mypurse.vault`; change it in `app/build.gradle.kts` before publishing if needed. It cannot be changed for an existing Play app.
2. Create/retain your upload keystore and keep it outside source control. Copy `keystore.properties.example` to `keystore.properties`, supply your key details, and build `bundleRelease`. Enable Play App Signing in your Console. Do not distribute keystore passwords.
3. Replace the contact placeholders in `PRIVACY_POLICY.md`, review the policy and host it on a public HTTPS URL. Provide the URL in Play Console and add it to your website/store support details. The app contains an offline privacy summary.
4. Upload the signed AAB to an internal testing track. Complete Data safety, content rating, target audience, app access (no login required), ads declaration (no ads), support contact, country availability and store listing.
5. If your personal developer account falls under Google's new-account testing rules, complete the required closed test and apply for production access. As checked on 2026-10-06, this requires at least 12 continuously opted-in testers for 14 days for applicable accounts.
6. Test an actual older Android phone, a modern ARM64 device, tablet/foldable layouts, large fonts, low storage, background lock, process death and device biometrics. Test startup playback on real hardware. Do not infer universal device support from a single emulator.
7. Confirm native libraries and APK/AAB packaging meet 16 KB requirements. SQLCipher includes native binaries, so dependency updates must be rechecked. Review Play Console's bundle/device compatibility checks.
8. Increment versionCode for every new uploaded build. Release screenshots are protected; use development screenshots containing only dummy data for the listing.

## Data safety draft

The current app has no networking permission, accounts, advertising or analytics SDKs. Personal data is processed and stored on-device. Users may explicitly share files through Android's chooser; review how the current Play form treats user-initiated sharing before submitting. Do not blindly declare this app compliant from the architecture alone. Revisit the declaration if any SDK or networking is added.

## Listing draft

Name: My Purse

Short description: Keep cards and documents organized in a private, offline vault.

Full description:

My Purse keeps your card reference details and important documents together on your Android device.

Add bank-card reference details or ID-card front/back images. Flip cards, copy chosen fields, and import PDF documents and images. Organize documents into categories, mark favorites, browse the card carousel, and open documents with page navigation and zoom.

No signup. No cloud sync. No advertising. Card details and imported documents are encrypted locally.

Review this draft against the version you publish. The minimal dashboard currently has no entry to the settings / app-lock components.

My Purse is an organizer, not a payment app. It does not make payments or add payment cards to NFC wallets. Uninstalling the app or clearing its data deletes your vault. Keep original copies of important documents.

## Official references

- https://support.google.com/googleplay/android-developer/answer/14151465
- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://developer.android.com/guide/practices/page-sizes
