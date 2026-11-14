# DocuBuds — Testing

## Unit tests

Run:

```bash
./gradlew testDebugUnitTest
```

Coverage (all in `app/src/test/java/app/docubuds/core/`):

| Area | What is asserted |
|---|---|
| `CryptoTest` | AES/GCM round-trip (incl. unicode + leading zeros), wrong-key failure, tampered-ciphertext failure, fresh IV per encryption, key serialisation round-trip, unique generated keys |
| `LogicTest` | Inactivity lock: open at 59s, locked at exactly 60s, locked after time outside the app; reminder dates due at exactly 30 / 7 / 1 days before expiry and not on other days; notification-key uniqueness; trash kept at 29 days, purged at 31 days, selective purge; donate UPI link exact + no amount parameter (a link with `am=` is rejected); credit line, Instagram URL, chooser title and copy toast exact; expiry-date parsing (dd-mm-yyyy, yyyy-mm-dd, month-name) and keyword-based suggestion |
| `ModelsTest` | Vault JSON round-trip preserves leading zeros and IFSC; 7 suggested spaces seeded; record ids unique; card records keep PIN and CVV fields |

### Results — 2026-11-14 (standalone verification, see blocker below)

**Unit tests: 30 / 30 PASS.** `./gradlew testDebugUnitTest` could not run in this VM (blocker below), so the same tests were compiled and run with the standalone Kotlin compiler 2.0.20 (the same Kotlin version the project uses) and JUnit 4.13.2:

```bash
kotlinc -cp junit-4.13.2.jar:hamcrest-core-1.3.jar:gson-2.11.0.jar -d /tmp/docubuds-test/classes \
  app/src/main/java/app/docubuds/core/*.kt app/src/test/java/app/docubuds/core/*.kt
java -cp <classes>:junit-4.13.2.jar:hamcrest-core-1.3.jar:gson-2.11.0.jar:kotlin-stdlib.jar \
  org.junit.runner.JUnitCore app.docubuds.core.CryptoTest app.docubuds.core.LogicTest app.docubuds.core.ModelsTest
# JUnit version 4.13.2 — OK (30 tests) — Time: 0.551
```

| Class | Tests | Result |
|---|---|---|
| `CryptoTest` | 7 | OK |
| `LogicTest` | 19 | OK |
| `ModelsTest` | 4 | OK |

**Android sources parse-clean:** the `android/` + `ui/` sources were also compiled standalone against `android.jar` (without the AndroidX/Compose/ML Kit/CameraX libraries, which only Gradle can resolve). Result: **0 syntax errors**; all 1,344 errors were `unresolved reference` to third-party libraries only (Compose `Text`/`Modifier`/`dp`, ML Kit `com.google…`, `kotlinx.coroutines`) — no unresolved reference to any of the app's own code. The full compile, with all dependencies resolved, is therefore left to the CI build below.

### Local Gradle blocker — root cause found (2026-11-14)

`./gradlew testDebugUnitTest` and `./gradlew assembleDebug` **do not run in this VM**, and it is not a code problem. Every attempt fails within ~5 s with:

```
FAILURE: Build failed with an exception.
* What went wrong:
Could not receive a message from the daemon.
Caused by: java.lang.IllegalArgumentException: Unexpected type tag 109 found.
```

Root cause, proven with a minimal Java TCP test (a Java server on 127.0.0.1 + a Java client connecting to it): the Gradle daemon starts fine, binds its loopback port, and never receives a connection — because **the sandbox intercepts TCP connections made by Java processes** and answers them itself with this plaintext message (byte 109 = `m`, the first byte of `muse: …`):

> `muse: Other TCP connections is turned off for this assistant. To allow it, ask the user to open Muse settings -> Permissions -> Direct network protocols and switch other_tcp from Deny to Ask.`

(Python-to-Python loopback works; Java-to-anything does not. Even a trivial `gradle hello` task on an empty project fails identically, so the project is not the cause.) The Gradle daemon protocol is TCP-on-loopback only, so there is no Gradle flag that avoids it — `--no-daemon` still forks a single-use daemon over the same socket.

**Two ways to get a verified build:**
1. **CI (already set up):** push the source and let `.github/workflows/build.yml` run `./gradlew testDebugUnitTest assembleDebug` on a free GitHub-hosted Ubuntu runner, where loopback TCP is unrestricted. This is the path the release APK will come from.
2. **Flip the permission (user action):** in Muse settings → Permissions → Direct network protocols, switch `other_tcp` from Deny to Ask — then `./gradlew` works locally too.

`assembleDebug` has therefore **not** been verified locally; the APK is unverified until CI runs.

## Spec §12 scenario checklist

Note: every "LogicTest … pass" below refers to the standalone run in Results above (30/30 via kotlinc + JUnit), not to `./gradlew`, which is blocked in this VM.

| # | Scenario (spec §12) | Result | Evidence |
|---|---|---|---|
| 1 | First launch asks for display name, then phone unlock opens the vault; with no phone lock set, DocuBuds refuses to open | UNTESTED — no real phone in this environment | BiometricPrompt + `KeyguardManager.isDeviceSecure` gate implemented in `MainActivity` / `LockScreen`; not exercised on hardware |
| 2 | Lock after 1 minute of inactivity, including after leaving the app to share; returning after that minute requires unlock again | PARTIAL — lock timing logic unit-tested (59s open / 60s locked); the on-resume relock flow itself UNTESTED on a device | `LogicTest` lock tests pass |
| 3 | Screenshots work inside the app; the recent-apps thumbnail is blank | UNTESTED on a device | No `FLAG_SECURE` anywhere in the source (verify with `grep -r FLAG_SECURE app/src`); blanking is done by switching to a blank screen in `onPause` |
| 4 | Camera scan, gallery pick and file pick all add a document; images and PDFs open in the internal viewer | UNTESTED on a device | CameraX, GetContent launchers and PdfRenderer viewer implemented |
| 5 | OCR reads English and Hindi; search finds text inside a scanned document; an expiry date read from a scan is shown for confirmation before saving | PARTIAL — expiry parsing unit-tested; OCR on real photos UNTESTED | `LogicTest` expiry-parser tests pass; ML Kit OCR not exercised on device photos |
| 6 | Expiry reminders set for 30, 7 and 1 day before, with generic notification wording | PARTIAL — reminder-date logic unit-tested; the daily worker firing on a real phone UNTESTED | `LogicTest` reminder tests pass |
| 7 | Passwords stay hidden until tapped, then reveal on tap with no second unlock | UNTESTED on a device | Masked-field UI implemented in `MaskedFieldRow` |
| 8 | Export saves locally; Share opens Android's share menu and the shared file keeps its original quality | UNTESTED on a device | Share via FileProvider with the decrypted original bytes; export via SAF |
| 9 | Delete moves an item to Trash; Trash empties manually and auto-deletes after 30 days | PARTIAL — purge logic unit-tested; the UI flow UNTESTED on a device | `LogicTest` trash tests pass |
| 10 | Donate: chooser titled "Choose an app to pay with", payee pre-filled, no amount; QR scans to the same payee; Copy copies the UPI ID and shows "UPI ID copied."; @momentswithvms opens Instagram; fallback sheet when no UPI app exists | PARTIAL — UPI link exactness unit-tested; the chooser/QR/fallback on a real phone UNTESTED | `LogicTest` donate tests pass; `DonateActions` implements the chooser + fallback |
| 11 | Bottom of every screen: donate block present, credit line directly below it as the last line, neither covered by the navigation bar, both fully tappable | UNTESTED on a device | `DonateFooter` is the last item of every screen's scrollable content, above the bottom navigation bar |
| 12 | With connectivity enabled, network observation proves the vault itself makes zero network activity | UNTESTED — no device/emulator run in this environment | The vault code makes no network calls (no HTTP client, no analytics SDK in `app/build.gradle.kts`); ML Kit model download on first use is the one documented exception (see README) |

## Emulator

An emulator was not run on this VM: the machine has ~7.7 GiB RAM with ~0.5 GiB free, and the emulator plus Gradle would not fit. The release APK is built by GitHub Actions (see `.github/workflows/build.yml`), and the phone checks in §12 must be done on a real phone before v1 is called done.

**Local Gradle is blocked in this VM** (see Results above): the sandbox intercepts Java-process loopback TCP (`other_tcp` = Deny), so `./gradlew` cannot talk to its daemon here. CI on a GitHub runner is the verification path for `assembleDebug` until the permission is flipped.
