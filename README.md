# DocuBuds

A complete, free, fully offline Android vault for documents, IDs, bank accounts and cards, logins and secure notes. Android only, version 1.0.0.

**Package:** `app.docubuds` · **minSdk 26, targetSdk 34** · **Kotlin + Jetpack Compose (Material 3)**

## What it does

- **Unlock — only the phone's lock.** First launch asks for a display name, then the vault opens with Android's own unlock prompt (BiometricPrompt, `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`): the phone's PIN / pattern / password, face or fingerprint. The app never reads or stores the phone password. No separate DocuBuds password, no recovery key, no backup at all. If the phone lock is removed, DocuBuds refuses to open until a lock is set again. If the phone is lost, the app is uninstalled, or data is cleared, the records are gone for good — the setup screen says this plainly.
- **Locks after 1 minute of inactivity**, including time spent outside the app. Screenshots are allowed; the recent-apps thumbnail is blanked by switching to a blank screen on backgrounding (no `FLAG_SECURE`, so screenshots keep working).
- **Holds everything:** IDs (Aadhaar, PAN, passport, licence, voter ID), bank accounts (numbers/IFSC as text, leading zeros preserved), payment cards **including PIN and CVV**, logins (website as inert text only — never fetched, validated or opened), secure notes, and documents of any file type.
- **Organisation:** named spaces with nested folders and breadcrumbs, seeded with Property 1, Home documents, Shop documents, Farmhouse documents, Vehicle documents, Work and Resumes. Property/vehicle spaces and per-company Work spaces with Payslips > Year > Month. A space is organisation only, not a security boundary.
- **Adding documents:** camera scan (CameraX), gallery pick, file pick — all in v1. On-device OCR (ML Kit, Latin + Devanagari) reads the text; an expiry date found in a scan is shown for confirmation before saving. Search works inside documents too, in English and Hindi. UI is English only; theme follows the phone.
- **Passwords and bank numbers are masked until tapped to reveal** — revealing is just a tap, no second unlock.
- **Expiry reminders** at 30 / 7 / 1 days before, local only (WorkManager), with generic notification wording — no document names or numbers in the notification.
- **Trash:** deleted items stay 30 days, then are gone for good; the Trash can be emptied manually at any time.
- **Export and share while unlocked:** export locally (SAF picker) or share any file through Android's share menu (WhatsApp, email, any app) in original quality — shared as a file/document, never compressed by DocuBuds.
- **Donate to the developer:** exactly as in Remibuds 2.0.4 — header heart button, donate row at the bottom of every screen except More, full donate section (tappable QR 240×240, "Click to pay", UPI ID + Copy) in More/Settings, and `Made with love ♥ by Vijay Mamta Sahani (@momentswithvms)` as the last line on every screen. UPI chooser titled "Choose an app to pay with", no amount pre-filled, fallback sheet if no UPI app can open. Free, no ads, no paid restrictions.
- **Video/audio:** files of any type can be stored; in-app video/audio playback is deferred (spec §5).

## Encryption

- Fresh random AES-256 data key per vault (secure randomness), wrapped by a master key in the AndroidKeyStore. The raw data key is never written to disk.
- Everything is encrypted with AES-256-GCM (random IV per item): the whole vault index (titles, space/folder names, field values, OCR text) and every attachment, stored under a random id in internal storage — real filenames live only inside the encrypted index.
- The APK contains code only — no documents, keys or vault data. No universal password, no developer bypass.
- `allowBackup=false` and backup rules exclude all vault content from OS cloud backup.

**Honest note on the unlock and the keys:** the master key is not set to require user authentication, because Android does not allow a CryptoObject with device-credential authentication — and the spec requires PIN/pattern unlock. So the unlock is an access gate enforced by the app (BiometricPrompt on every unlock + the 1-minute lock); the keys themselves are KeyStore-bound and never leave the device. A stolen vault file without the device's KeyStore cannot be decrypted.

## Build

```bash
./gradlew assembleDebug        # debug APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
```

Requires JDK 17 and the Android SDK (compileSdk 34). CI (`.github/workflows/build.yml`) runs the same two commands on a free GitHub-hosted Ubuntu runner, uploads the APK as a workflow artifact, and on a `v*` tag creates a GitHub Release with the APK attached.

## Architecture (one page)

- `app.docubuds.core` — pure JVM logic, no Android classes: `Models` (VaultData/Space/VaultRecord/TrashEntry), `VaultCrypto` (AES/GCM), `LockLogic` (1-minute lock), `ReminderDates` (30/7/1), `TrashLogic` (30-day purge), `Donate` (exact UPI values), `ExpiryParser` (date extraction from OCR text).
- `app.docubuds.android` — platform layer: `KeystoreKeyManager` (KeyStore master key, wrapped data key), `VaultStore` (encrypted index + attachments in internal storage), `MlKitOcr` (on-device text recognition), `DonateActions` (UPI chooser / clipboard / Instagram), `ReminderWorker` (daily local reminder check).
- `app.docubuds.ui` — Compose UI: `MainActivity` (biometric gate, lifecycle blanking), `VaultViewModel` (state, vault operations, share/export), `Screens` (setup, lock, spaces, search, trash, more, record detail), `AddRecord` (forms + document flow with OCR confirmation), `DonateUi` (donate row/section/credit), `CameraScreen` (CameraX scan), `Viewers` (internal image/PDF viewers via PdfRenderer).

## Documented deviations from the v3 spec

These are deliberate, owner-approved, or honestly recorded where the platform forced a choice:

1. **Unlock is the phone's lock only** (spec §3 override of v3): no vault password, no recovery key. See the note above about KeyStore auth.
2. **No backup at all** (spec §7 override of v3): v3 had manual encrypted backups; the owner removed them entirely. Permanent loss is accepted and stated on setup.
3. **Share/export via Android's generic share menu is allowed** (spec §6 override of v3): WhatsApp, email, any app — while unlocked, in original quality.
4. **OCR engine:** the spec prefers a bundled Tesseract engine with no first-use download. Tesseract4Android's published artifacts are unmaintained and would not build reliably on a stock GitHub runner, so this build uses ML Kit Text Recognition instead. ML Kit processes images fully on-device (nothing leaves the phone) and covers Latin and Devanagari; its models are managed by Google Play services and may be downloaded on first use if not already present. If the model is unavailable, OCR returns empty text and the user types the expiry date manually — the app never blocks on OCR.
5. **Recent-apps blanking is best-effort:** it works by switching to a blank screen in `onPause`, before the system captures the thumbnail. It was not verified on a real phone (see below).

## What was tested / what was NOT tested

Tested (see `TESTING.md` for the full checklist with evidence):

- **Unit tests: 30 / 30 pass** — crypto round-trip, wrong-key and tamper failure, fresh IVs, vault JSON round-trip (leading zeros preserved), lock timing (59s open / 60s locked), reminder dates (30/7/1), trash purge (29 days kept / 31 days purged), donate UPI link exactness (no amount), expiry-date parsing. Run via the standalone Kotlin compiler + JUnit (see TESTING.md for the exact commands and why `./gradlew` is blocked in this VM).
- **Android sources parse-clean** with 0 syntax errors (standalone kotlinc check against `android.jar`; only missing-library references remain, which Gradle resolves on CI).
- `assembleDebug` has **not** been verified locally: this VM's sandbox blocks the Gradle daemon's loopback TCP (`other_tcp` permission = Deny — see TESTING.md). The CI workflow (`.github/workflows/build.yml`) runs `./gradlew testDebugUnitTest assembleDebug` on a free GitHub-hosted Ubuntu runner, which is the verification path for the APK.

NOT tested — labelled honestly, do not claim as verified:

- **Biometric unlock on a real phone** (no phone available in this environment). The BiometricPrompt code follows the documented API, but the real unlock flow, the no-phone-lock blocking screen, and the 1-minute relock have not been exercised on hardware.
- **OCR on real device photos**, and ML Kit model availability on a phone without Play services.
- **UPI chooser on a real phone** (no UPI app in an emulator), QR scanning from a second phone, and the fallback sheet when no UPI app exists.
- **Camera scan, gallery/file pick, share to WhatsApp, export to local storage** on a real phone.
- **The recent-apps blank thumbnail** on a real phone (emulator would not run on this small VM — see TESTING.md).
- **Expiry reminders firing on a real phone** (WorkManager scheduling is unit-logic only; the daily worker has not been observed firing).
- **Independent security review** — not done. Do not use this app with real secrets until one is done, and never claim it is "unhackable".
