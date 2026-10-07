# My Purse build handover

Local verification notes from development on 2026-10-06. These are historical results, not a certification for every device or future commit.

## Delivered

- Native Kotlin / Jetpack Compose Android app, minimum Android 8, target Android 16 (API 36).
- Figma-matched Cards and Documents dashboard: edge-aligned split lavender ribbon, stacked landscape cards, floating side controls and the two-tab bottom bar. No dashboard header, search, counts, category filters or save banners.
- Bundled original startup video, empty first-run canvas and animated vertical Cards/Documents pager.
- Bank cards with network, title, masked/revealed number, CVV on the reverse, field copy, flip, favorites and deletion.
- ID cards with encrypted front/back image import, image copy and flip.
- PDF/JPEG/PNG/WebP import through Android's file picker, categories, duplicate detection, 100 MB limit, thumbnails, PDF/image viewer, zoom and explicit file sharing.
- SQLCipher encrypted database, streaming encrypted document files, Android Keystore key protection and optional biometric/device authentication.
- No login, cloud sync, ads or analytics. Final APK has no Internet or broad storage permission. Android backup is disabled; release screenshots are protected.

## Verification results

- Debug APK, minified release APK and release AAB built successfully.
- Three local validation tests passed; debug lint passed.
- Five emulator instrumented tests passed: version-one database migration, encrypted storage/tamper/duplicate handling, front/back ID image cleanup, clipboard copy/flip and document carousel swipe.
- Workflow tests passed on API 36 phone and tablet layouts, including tablet font scale 1.6. The latest file-picker lifecycle patch was rebuilt and retested on the phone.
- Actual PDF thumbnail and viewer screenshots inspected. Screenshot data is synthetic test content, not seeded into first-run app data.
- Minified release APK locally signed with the development key solely for smoke testing: installed, startup reached dashboard, no AndroidRuntime crash observed. This is not the owner upload signing key.
- Release native ARM64/x86_64 ELF load alignment and APK 16 KB zip alignment checks passed. No actual 16 KB-page runtime device was tested.
- Original and bundled startup video SHA-256 hashes match.
- After the dashboard, launcher-logo, and navigation changes, debug assembly and local tests passed. Release bundle assembly and signing also passed locally. Instrumented tests were not rerun after those final visual changes.

## Release boundaries

No Play Store publication has happened. A local owner upload key was created during release preparation and is excluded from this source repository. Forks must configure their own signing key and application ID, finish and host a privacy policy, and complete Play Console declarations and applicable testing requirements. See `PLAY_STORE.md`.

Physical-device testing remains necessary: old Android versions, biometric enrollment/unlock, external picker with lock enabled, low storage, process death, real video playback, foldables and 16 KB-page hardware. Emulator results do not guarantee every device works.

There is no portable encrypted backup/restore in this version. Uninstalling or clearing app data deletes the vault; retain original documents. Explicitly shared decrypted cache copies are cleaned at the next cold start. My Purse stores card references only, not payments or NFC credentials.
