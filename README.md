# 🌼 Kimi

<div align="center">

**Small steps. A space of your own.**

A joyful, privacy-conscious habit tracker for Android, built entirely with Kotlin and Jetpack Compose.

[![Android CI](https://github.com/vabxsen/Kimi/actions/workflows/android.yml/badge.svg)](https://github.com/vabxsen/Kimi/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/vabxsen/Kimi?display_name=tag&sort=semver)](https://github.com/vabxsen/Kimi/releases/latest)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?logo=kotlin&logoColor=white)](https://developer.android.com/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[Download the latest APK](https://github.com/vabxsen/Kimi/releases/latest) · [Privacy](docs/PRIVACY.md) · [Verification](docs/VERIFICATION.md)

</div>

![A preview of the Kimi Android app](docs/screenshots/overview.png)

## 🌱 About Kimi

Kimi makes habit tracking feel encouraging instead of demanding. Create small rituals, check in each day, reflect in a private journal, and understand your progress without being punished for rest days.

The app is fully native—there is no WebView—and its interface includes light and dark themes, friendly visuals, Nunito typography, rounded components, and Kimi's flower mascot.

> **Rest days are not failures.** Days with nothing scheduled are excluded from streaks and consistency calculations, and appear as rest days throughout the app.

## ✨ Features

| Area | What you can do |
| --- | --- |
| ☀️ **Today** | Check off today's habits, undo check-ins, filter by time of day, and review daily progress and streaks. |
| 🌿 **Habits** | Create, search, edit, and delete rituals with custom icons, colors, schedules, dayparts, and goals. |
| 📅 **Calendar** | Browse previous and upcoming months, then review the habits completed on any date. |
| 📊 **Insights** | See seven-day completion, weekly wins, current streaks, and 30-day consistency. |
| 📖 **Journal** | Record a mood and one private reflection per day, with resilient draft recovery. |
| ⚙️ **Settings** | Choose a theme, manage reminders and accounts, export or restore data, and check for updates. |

Additional details:

- Five suggested starter rituals or a completely blank space.
- Automatic, light, and dark appearance modes.
- Optional habit reminders on scheduled, incomplete days.
- Local JSON export with validation and preview before restore.
- Optional Firebase account sync across devices.
- Email/password and Google sign-in.
- Manual, certificate-verified updates from GitHub Releases.
- Localized date and calendar labels, with all app copy stored in Android resources.

## 📱 Download and install

Kimi supports **Android 8.0 (API 26) and newer**.

1. Open the [latest GitHub release](https://github.com/vabxsen/Kimi/releases/latest).
2. Download the attached `.apk` file.
3. Allow your browser or file manager to install unknown apps if Android asks.
4. Open the APK and confirm the installation.

After installation, use **Settings → Keep Kimi fresh → Check for updates** to check manually for newer releases. Kimi does not contact GitHub in the background.

> **Upgrading from v1.0.0?** That release used a debug signing key and cannot be updated in place. Export your data, uninstall v1.0.0, and install the latest release manually.

## 🔔 Reminders

Open **Settings → A friendly little nudge**, choose a habit, enable **A gentle nudge**, grant notification permission, and select a local time.

Kimi only reminds you when a habit is scheduled and still incomplete. Notifications include a safe **Mark complete** action, while reminders are refreshed after reboots, app updates, timezone changes, and relevant habit changes.

Android may delay inexact alarms because of battery management, and force-stopping Kimi disables reminders until the app is opened again. Kimi does not request exact-alarm access. Learn more in the [Android alarm documentation](https://developer.android.com/develop/background-work/services/alarms).

## 🔒 Privacy and data

Kimi is designed to work without an account:

- **Guest mode stays on your device.** Habits, check-ins, reflections, and drafts remain local.
- **Accounts are optional.** Signing in enables Firebase Authentication and Firestore sync.
- **Spaces stay separate.** Guest data and each signed-in account have independent data stores.
- **No ads or analytics.** Kimi does not include advertising or behavioral analytics.
- **Crash reports are limited.** Release builds use Firebase Crashlytics, but never attach habit or journal content; debug builds do not report crashes.
- **Backups are readable.** Exports are JSON files containing personal data, so save them somewhere you trust.

See the complete [Privacy Policy](docs/PRIVACY.md) for data handling details.

## 🎨 Thoughtful behavior

- Check-ins can only be changed on the current day; past and future dates are read-only.
- Schedule edits apply from the edit date onward, preserving historical statistics.
- Deleting a habit also deletes its check-ins.
- Resetting a space removes its habits, history, reflections, drafts, and recovery copies.
- Appearance is stored per device and is not included in backups or switched with accounts.
- Bottom navigation retains screen state, and Android Back closes transient UI before returning to Today.

## 🛠️ Build from source

### Requirements

- Android Studio with Android SDK 36
- JDK 17 or newer
- An Android 8.0+ device or emulator

Clone the repository and run:

```powershell
git clone https://github.com/vabxsen/Kimi.git
cd Kimi
.\gradlew.bat assembleDebug
```

The debug APK is generated under:

```text
app/build/outputs/apk/debug/
```

You can also open the repository directly in Android Studio and run the `app` configuration.

> Firebase restricts Android clients by package name and signing certificate. A debug build created on a new computer needs that machine's debug SHA-1 registered before account, sync, and password-reset features will work.

## 🧪 Test and verify

Run the same checks used by CI:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

For connected tests, start the Firebase emulators, then run the instrumentation suite on a dedicated Android emulator:

```powershell
npx -y firebase-tools@latest emulators:start --only auth,firestore --project demo-kimi-auth

# In another terminal
.\gradlew.bat connectedDebugAndroidTest
```

Connected tests replace the Kimi test data on the selected emulator. See [docs/VERIFICATION.md](docs/VERIFICATION.md) for the broader verification record.

## 🧱 Technology

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose and Material 3 |
| Architecture | ViewModels, coroutines, immutable UI state, and local stores |
| Authentication | Firebase Authentication and Android Credential Manager |
| Sync | Cloud Firestore |
| Reliability | Validated JSON backups and previous-snapshot recovery |
| Background work | Android alarms, receivers, and notifications |
| Quality | JUnit, Compose UI tests, Android Lint, and GitHub Actions |
| Minimum Android version | Android 8.0 / API 26 |

## 🗂️ Project structure

| Path | Responsibility |
| --- | --- |
| `MainActivity.kt` | App scaffold, navigation, lifecycle handling, and document pickers |
| `Design.kt` | Themes, palettes, typography, and reusable UI components |
| `Screens.kt` | Main product screens and the habit editor |
| `AccountUI.kt` | Account and authentication screens |
| `AccountViewModel.kt` | Authentication, Credential Manager, and account lifecycle |
| `FormaViewModel.kt` | User operations, drafts, backups, and sync scheduling |
| `Data.kt` / `Stats.kt` | Habit rules, schedules, streaks, and consistency |
| `HabitStore.kt` | Durable local persistence and recovery |
| `BackupCodec.kt` | Versioned JSON serialization, migration, and validation |
| `Sync.kt` / `SpaceSync.kt` | Per-entry merge logic and Firestore synchronization |
| `Reminders.kt` | Alarm scheduling, receivers, and notification actions |
| `Updates.kt` / `UpdateUI.kt` | Secure GitHub release checks and update UI |
| `src/test` | Unit tests for core domain behavior |
| `src/androidTest` | End-to-end Compose, storage, notification, and account flows |

<details>
<summary><strong>☁️ Firebase and App Check notes</strong></summary>

Kimi uses the Firebase project `kimi-track` and the Android package `com.forma.habits`.

- Google and email/password authentication providers are enabled.
- The Android API key is restricted by package and certificate.
- Release builds install the Play Integrity App Check provider.
- Debug builds install the App Check debug provider.
- App Check enforcement must only be enabled after all production certificates and required debug tokens are registered and verified in Firebase metrics.
- A Play Store release would also require the Play App Signing certificate to be registered.

The public `app/google-services.json` contains client configuration, not administrative credentials. API key restrictions and App Check provide complementary safeguards; neither makes client configuration secret.

Provider configuration lives in `firebase.json`. Intentional changes can be deployed with:

```powershell
npx -y firebase-tools@latest deploy --only auth --project kimi-track
```

Do not deploy unrelated services or the `demo-kimi-auth` test project.

</details>

<details>
<summary><strong>🔐 Release signing</strong></summary>

Release signing values are loaded from a gitignored `keystore.properties` file or from `KIMI_*` environment variables. Without signing material, `assembleRelease` produces an unsigned APK and prints a warning.

1. Create and securely back up a release keystore:

   ```powershell
   keytool -genkeypair -v -keystore kimi-release.jks -alias kimi -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
   ```

2. Copy `keystore.properties.example` to `keystore.properties` and provide:

   ```properties
   storeFile=kimi-release.jks
   storePassword=<your store password>
   keyAlias=kimi
   keyPassword=<your key password>
   ```

3. Register the signing certificate's SHA-1 in:

   - Google Cloud API key restrictions
   - Firebase Android app certificate fingerprints
   - Firebase App Check / Play Integrity

4. Increment `versionCode` and `versionName` together in `app/build.gradle.kts`.

5. Build the release:

   ```powershell
   .\gradlew.bat assembleRelease
   ```

Keep the keystore and passwords out of version control. Losing the release key prevents future updates; exposing it allows someone else to sign builds as Kimi.

</details>

<details>
<summary><strong>📦 Update security</strong></summary>

Kimi's updater:

- Checks the latest `vabxsen/Kimi` GitHub release only when requested.
- Compares semantic numeric versions.
- Streams downloads to an internal temporary file.
- Exposes an APK to Android only after the download completes.
- Verifies that the APK signing certificate matches the installed app.
- Uses Android's package installer for final user confirmation.

Because this flow needs `REQUEST_INSTALL_PACKAGES`, it is appropriate for GitHub distribution. A Google Play release would need to remove the self-update feature or permission to comply with Play policy.

</details>

## 📚 Documentation

| Document | Description |
| --- | --- |
| [Privacy Policy](docs/PRIVACY.md) | User-facing data collection, storage, and sharing details |
| [Verification](docs/VERIFICATION.md) | Test coverage, manual checks, and release verification |
| [License](LICENSE) | MIT license terms |
| [Nunito license](Nunito-OFL.txt) | SIL Open Font License for the bundled Nunito typeface |

## 🤝 Contributing

Issues and pull requests are welcome.

1. Fork the repository and create a focused branch.
2. Keep changes consistent with the existing Compose architecture and visual language.
3. Add or update tests for behavioral changes.
4. Run the build, unit tests, and lint before opening a pull request.
5. Explain the user-facing impact and include screenshots for UI changes.

Please avoid committing Firebase administrative credentials, keystores, passwords, generated APKs, or local configuration files.

## 📄 License

Kimi is available under the [MIT License](LICENSE).

---

<div align="center">

Made with care for small steps and happier routines. 🌼

</div>
