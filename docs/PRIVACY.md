# Kimi privacy policy

**Last updated: 10 September 2026**

Kimi is a habit tracker and journal for Android. This policy describes exactly what the app does
with your information. It is written to match the app's actual behaviour, which you can verify in
this repository.

> **Before publishing:** replace `[CONTACT EMAIL]` below with an address you are willing to make
> public, and confirm the "Last updated" date. Play requires a publicly reachable URL for this
> policy — GitHub Pages or the raw file URL both work.

## The short version

**As a guest, nothing you write leaves your device.** If you sign in, your habits, check-ins and
journal sync privately to your own account so they follow you between devices — stored where only
you can read them. Kimi has no analytics, no advertising and no tracking.

## What stays on your device

These are stored in Kimi's private app storage. **As a guest they are never uploaded anywhere.**
If you sign in, everything except the last item is also synced to your account — see
*Your synced space* below.

- Your display name
- Your habits, their schedules, icons, colours and reminder times
- Every check-in
- Your journal reflections and moods, including unfinished drafts
- Your light/dark appearance preference

If you use an account, each account gets its own separate local space on the device, and guest use
gets another. Signing out leaves that account's data on the device for next time.

**As a guest there is no cloud copy.** Uninstalling Kimi, or clearing its storage in Android
Settings, permanently deletes everything. Export a backup first if you want to keep it. If you are
signed in, your synced space is restored when you sign in again.

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

If you would rather nothing was uploaded, use Kimi as a guest and move data yourself with
*Export my space*.

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
analytics, no advertising, no tracking of any kind and no other third-party SDKs.** Your habit and
journal content is never sold, shared with anyone else, or used for advertising or model training.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | To sign in, to sync your space while signed in, and to send crash reports |
| `POST_NOTIFICATIONS` | To show habit reminders. These are generated on your device; nothing is sent anywhere |
| `RECEIVE_BOOT_COMPLETED` | To restore your reminder schedule after a restart |
| `REQUEST_INSTALL_PACKAGES` | To hand an update you downloaded to Android's installer. Android still asks you to confirm, and to allow Kimi as a source the first time |

Kimi requests no access to contacts, location, camera, microphone, storage or any other sensitive
permission.

## Backups you create

**Export my space** writes a JSON file to a location you choose using Android's document picker. The
file contains your name, habits, schedules, check-ins and saved reflections in readable form.

Once written, that file is outside Kimi's control. If you place it in a synced folder it will be
copied wherever that folder syncs. Choose a location you trust.

**Restore a backup** reads a file you select. Kimi validates it fully before replacing anything, and
an invalid file never overwrites your current data.

## Deleting your data

- **Local data:** Settings → *Start with a clean slate*, or uninstall the app.
- **Your account:** Settings → your account → *Delete my account*. This permanently deletes your
  Firebase identity, your synced space in Firestore, and that account's habits, check-ins, journal
  and drafts on this device.

Deleting an account cannot reach backups you exported earlier, or data on other devices where you
were signed in. Delete those yourself.

## Children

Kimi is not directed at children under 13, and does not knowingly collect their information. Account
creation goes through Firebase Authentication, which has its own age requirements.

## Changes

If this policy changes materially, the "Last updated" date above will change and the revision will
be visible in this repository's history.

## Contact

Questions about this policy or your data: **[CONTACT EMAIL]**
