# Kimi privacy policy

**Last updated: 16 October 2026**

Kimi is a habit tracker and journal for Android. This policy describes exactly what the app does
with your information. It is written to match the app's actual behaviour, which you can verify in
this repository.

## The short version

**As a guest, nothing you write leaves your device.** If you sign in, your habits, check-ins and
journal sync privately to your own account so they follow you between devices — stored where only
you can read them. Kimi has no analytics, no advertising and no tracking.

## What stays on your device

These are stored in Kimi's private app storage. **As a guest they are never uploaded anywhere.**
If you sign in, everything except the last three items is also synced to your account — see
*Your synced space* below.

- Your display name
- Your habits, their schedules, icons, colours and reminder times
- Every check-in
- Your saved journal reflections and moods
- Unfinished journal drafts, which always remain on this device
- Voice notes you record in the journal, which always remain on this device. Kimi records only
  from when you tap the microphone until you stop; notes are never synced or uploaded, and are
  deleted with their reflection, or with the account they belong to
- Your preferences, which always remain on this device: light or dark mode, colour theme, whether the
  garden game is on, your flower's petal colour, which garden levels, badges and keepsakes have
  already been celebrated, the day you last played your week's story, your sound settings,
  each reminder's sound, and each habit's last focus-session length and sound. Garden progress
  itself is worked out from your check-ins, never stored

If you use an account, each account gets its own separate local space on the device, and guest use
gets another. Signing out leaves that account's data on the device for next time.

**As a guest there is no cloud copy.** Uninstalling Kimi, or clearing its storage in Android
Settings, permanently deletes everything. If you are signed in, your synced space is restored when
you sign in again.

## What leaves your device

### Accounts (optional)

Kimi has no account system of its own. If you choose to sign in, authentication is handled entirely
by **Google Firebase Authentication**, which receives and stores:

- Your email address
- Your display name, if you set one
- Your password, which Firebase stores hashed — Kimi never stores or transmits it itself
- A user ID, and sign-in metadata such as timestamps and IP address, which Google uses to operate
  the service and prevent abuse

If you sign in with Google, Google additionally confirms your identity to Firebase. Kimi receives
only the resulting account identifier, email and display name.

You can use Kimi entirely as a guest. Nothing above applies if you never sign in.

Google's handling of this data is governed by the
[Google Privacy Policy](https://policies.google.com/privacy) and the
[Firebase terms](https://firebase.google.com/terms).

### Your synced space

When you are signed in, Kimi keeps a copy of your space — your name, habits, schedules, check-ins
and saved reflections — in **Google Cloud Firestore**, in a single document belonging to your
account. This is what lets your habits appear on a new phone.

- **Only you can read it.** Security rules allow access to a document solely when the signed-in
  account matches the document's owner. Every other read and write, authenticated or not, is
  refused. The rules are in `firestore.rules` in this repository.
- Unfinished journal drafts are **not** synced; they stay on the device where you typed them.
- Deleting your account deletes this document.
- Guest use never touches Firestore at all.

If you would rather nothing was uploaded, use Kimi as a guest.

### App integrity

Release builds use **Firebase App Check with Play Integrity**, which asks Google to confirm that the
app is a genuine, unmodified copy of Kimi. This sends device and app integrity signals to Google. It
exists to stop other people abusing the project's sign-up endpoint. It does not identify you and
carries none of your habit or journal content.

### Crash reports

When Kimi crashes, **Firebase Crashlytics** sends a report to Google so the fault can be found and
fixed. A report contains:

- The stack trace of the crash
- Your device model, operating system version and app version
- Whether the device was rooted, and how much memory and storage were free
- A random Crashlytics installation identifier, which is not linked to your Kimi account

**No habit, check-in or journal content is ever attached to a crash report.** Kimi does not add any
custom keys, logs or user identifiers to reports, so nothing you have written can appear in one.

Crash reporting is active in released builds only. Development builds never send anything.

### Update checks (only when you ask)

Kimi is distributed through GitHub Releases rather than an app store, so **Check for updates** in
Settings asks GitHub for the latest release and can download it for you.

This happens **only when you press that button**. Kimi never checks for updates on its own, not at
startup and not in the background. When you do press it, GitHub receives the request as any website
would: your IP address and the standard request headers. No habit, journal or account information
is attached, and Kimi sends nothing that identifies you.

Installing a downloaded update is handed to Android's own installer, which asks for your
confirmation. Kimi never installs anything silently.

### Nothing else

Beyond sign-in, syncing your own space, app integrity, crash reporting and update checks you
ask for, Kimi contains **no
analytics, no advertising, no tracking of any kind and no other data-collection SDKs.** Your habit and
journal content is never sold, shared with anyone else, or used for advertising or model training.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | To sign in, to sync your space while signed in, and to send crash reports |
| `POST_NOTIFICATIONS` | To show habit reminders and, if you turn it on, the evening streak nudge. These are generated on your device; nothing is sent anywhere |
| `RECORD_AUDIO` | Only to record a journal voice note, asked for the first time you tap **Add a voice note**. Recordings stay on your device |
| `RECEIVE_BOOT_COMPLETED` | To restore your reminder schedule after a restart |
| `REQUEST_INSTALL_PACKAGES` | To hand an update you downloaded to Android's installer. Android still asks you to confirm, and to allow Kimi as a source the first time |
| `ACCESS_NETWORK_STATE` | Lets Kimi retry a failed sync when connectivity returns; Firebase and Google Play services also use it to understand whether a network connection is available |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Added by Android Credential Manager so your device can protect saved sign-in credentials with its normal authentication UI |
| `READ_GSERVICES` | Added by Google Play services to read Google service configuration on the device |

Apart from the microphone for voice notes, which you can decline and still use everything else,
Kimi requests no access to contacts, location, camera, storage or any other sensitive permission.
Every other sound in Kimi is made by the app itself; it never listens.

## Backups

Kimi no longer creates backup files. Backups made by earlier versions are JSON files containing
your name, habits, schedules, check-ins and saved reflections in readable form, and they stay
wherever you saved them, outside Kimi's control.

When you first set Kimi up, **Already have a Kimi backup?** reads a file you select. Kimi validates
it fully before using it, and an invalid file never overwrites your current data.

## Deleting your data

- **Local data:** clear Kimi's storage in Android Settings, or uninstall the app.
- **Your account:** Settings → your account → *Delete my account*. This permanently deletes your
  Firebase identity, your synced space in Firestore, and that account's habits, check-ins, journal
  and drafts on this device.

Deleting an account cannot reach backup files you saved with an earlier version, or data on other
devices where you were signed in. Delete those yourself.

## Children

Kimi is not directed at children under 13, and does not knowingly collect their information. Account
creation goes through Firebase Authentication, which has its own age requirements.

## Changes

If this policy changes materially, the "Last updated" date above will change and the revision will
be visible in this repository's history.

## Contact

Questions about this policy or your data can be submitted through the
[Kimi GitHub issue tracker](https://github.com/vabxsen/Kimi/issues).
