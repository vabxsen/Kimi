# Kimi for Android

A colorful native habit tracker, built in Kotlin and Jetpack Compose. The approved six-screen design supplies the colors, Nunito typography, rounded cards, icons, spacing, and Canvas flower mascot. There is no WebView.

## Using Kimi

On first launch, enter your name and choose five suggested rituals or an empty collection. Suggestions start with zero check-ins; progress is earned from the day you start.

- **Today:** check and undo habits, choose a day in the current week, filter by time of day, see daily completion and current streaks.
- **Habits:** search, create, edit, and delete habits. Choose a name, goal, icon, color, daypart, daily/weekday schedule, and daily goals. Reminders are managed in Settings.
- **Calendar:** browse months and check or undo past scheduled days. Future check-ins are disabled.
- **Insights:** seven-day completion chart, weekly wins, current streaks, and consistency over the last 30 days. Days before creation and unscheduled days are excluded.
- **Journal:** five moods, one saved reflection per date, editing and deletion. Drafts persist across navigation and process restarts. Unfinished older drafts can be reopened.
- **Settings:** change your local display name, manage your Firebase account, export a JSON file, preview and restore a validated backup, manage notification access, or reset the current local space.

The avatar opens Settings. The five bottom tabs retain their scrolling state. Android Back closes dialogs/sheets and returns other pages to Today.

Habit goals are descriptive targets with one completion check per scheduled day. Schedule edits apply from the day of the edit; earlier schedules and statistics are retained. Deleting a habit also deletes its check-ins. Reset removes habits, history, reflections, drafts, and internal recovery copies.

## Reminders

In Settings, scroll to **A friendly little nudge**, select a habit, enable **A gentle nudge**, allow Android notifications, and choose a local time. Kimi reminds you only on scheduled days that remain incomplete. A notification opens Kimi and includes an idempotent **Mark complete** action. Schedules are refreshed after reboot, app updates, clock/timezone changes, and relevant data changes.

Reminders use Android's inexact alarm API and can be delayed by battery management. Kimi does not request special exact-alarm access. Force-stopping an Android app stops its alarms until you open it again. See [Android alarm behavior](https://developer.android.com/develop/background-work/services/alarms).

## Data and backups

Firebase Authentication manages optional accounts; Internet access is used for authentication. Habits and journal data remain on the device, with a separate store and drafts for each Firebase UID and for guests. There is no cloud sync, analytics, or advertising. Stores serialize writes off the main thread and confirm the device save before reporting success. Each keeps one previous valid snapshot for recovery. A damaged save is preserved; invalid backup files never replace live data.

## Accounts

Open the avatar, then **Sign in to Kimi** in Settings. First-run setup also has an account link. Google sign-in uses Android Credential Manager. Email accounts support creation, sign-in, verification emails, password recovery, account-name updates, sign-out and account deletion with fresh password/Google reauthentication. Passwords are transient form values; Firebase manages its session tokens, and neither is included in backups.

Guest progress remains intact when signing in. Each account starts with its own local space. **Copy guest progress** explicitly copies saved guest progress into an empty account space, leaving the guest original and unfinished drafts intact. Signing out returns to guest mode and preserves account data for the next sign-in. Deleting an account removes its Firebase identity and local habits, history, journal, drafts and recovery copies on this device. Previously exported files and data on other devices are not remotely erased. Reminders only run for the active space, and old notification actions cannot update another account.

The connected project is **kimi-track**, Android app **1:952471890795:android:c6a437cf29cae1b7a13a2e**, package **com.forma.habits**. The existing `com.kimi.app` registration was preserved. `app/google-services.json` contains public Firebase client configuration, not administrative credentials. Google and email/password providers are enabled. This build’s debug SHA-1/SHA-256 are registered; register a production signing certificate (including the Play App Signing certificate, when applicable) before shipping a release.

Auth provider configuration is in `firebase.json`. To intentionally update those providers, use `npx -y firebase-tools@latest deploy --only auth --project kimi-track`. Do not deploy the `demo-kimi-auth` test project or unrelated services.

**Export my space** uses Android's document picker to save your name, habits, schedules, check-ins, and saved reflections. **Restore a backup** validates the file and displays its contents' counts before asking to replace the current space. Unsaved journal drafts are local and are not included in exports. The versioned JSON format accepts the earlier Kimi/Forma preview backups. Limit: 8 MB, 500 habits, 10,000 characters per reflection.

Exports contain readable personal data. Choose a storage location you trust. Uninstalling the app or clearing Android app data removes the local workspace, so export first. See [Android document access](https://developer.android.com/training/data-storage/shared/documents-files).

## Build and install

Requires Android SDK 36, JDK 17 or newer, and Android 8.0+ on the device. Open this folder in Android Studio, or create `local.properties` with your SDK path and run:

```text
gradlew.bat assembleDebug testDebugUnitTest lintDebug
npx -y firebase-tools@latest emulators:start --only auth --project demo-kimi-auth
# In another terminal, with an Android emulator running:
gradlew.bat connectedDebugAndroidTest
```

The connected tests use a dedicated emulator and replace its Kimi test data. The delivered `kimi-android-debug.apk` is signed with an Android debug certificate, suitable for installing and testing. It is not a Play Store release. A production distribution should use your own protected release signing key. The application ID remains `com.forma.habits` to allow updates over the earlier Kimi preview without losing its data; the launcher name is Kimi.

## Source map

- `MainActivity.kt`: native app scaffold, navigation, lifecycle/date refresh, document pickers.
- `AccountUI.kt`: matching account screens and isolated session navigation.
- `AccountViewModel.kt`: Firebase authentication, Credential Manager, account lifecycle and guest import.
- `Design.kt`: approved visual tokens and shared native components.
- `Screens.kt`: the six screens and habit editor.
- `SetupAndRemindersUI.kt`: first-run setup and notification controls.
- `Data.kt`: models, due dates, streaks, schedule history, next reminder calculation.
- `BackupCodec.kt`: versioned serialization, migration, and validation.
- `HabitStore.kt`: serialized durable local storage and recovery.
- `FormaViewModel.kt`: user operations, draft persistence, backup read/write.
- `Reminders.kt`: alarm scheduling, boot/time receivers, notifications and actions.
- `src/test`: statistics, schedule changes, backup validation, and reminder calculations.
- `src/androidTest`: real Compose habit/journal flows, Android storage round-trip, notification action.

Nunito is bundled under its SIL Open Font License in `Nunito-OFL.txt`.
