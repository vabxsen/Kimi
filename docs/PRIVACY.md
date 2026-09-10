# Kimi privacy policy

**Last updated: 10 September 2026**

Kimi is a habit tracker and journal for Android. This policy describes exactly what the app does
with your information. It is written to match the app's actual behaviour, which you can verify in
this repository.

> **Before publishing:** replace `[CONTACT EMAIL]` below with an address you are willing to make
> public, and confirm the "Last updated" date. Play requires a publicly reachable URL for this
> policy — GitHub Pages or the raw file URL both work.

## The short version

Your habits, check-ins and journal entries never leave your device. Kimi has no server of its own,
no analytics, no advertising and no tracking. The network is used for two things only: signing in,
if you choose to create an account, and reporting crashes so the app can be fixed.

## What stays on your device

These are stored in Kimi's private app storage and are never uploaded anywhere:

- Your display name
- Your habits, their schedules, icons, colours and reminder times
- Every check-in
- Your journal reflections and moods, including unfinished drafts
- Your light/dark appearance preference

If you use an account, each account gets its own separate local space on the device, and guest use
gets another. Signing out leaves that account's data on the device for next time.

**Uninstalling Kimi, or clearing its storage in Android Settings, permanently deletes all of it.**
There is no cloud copy. Export a backup first if you want to keep it.

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

### Nothing else

Beyond sign-in, app integrity and crash reporting, Kimi contains **no analytics, no advertising, no
tracking of any kind and no other third-party SDKs.** Your habit and journal content is never
transmitted, sold, shared or used for advertising or model training.

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | To reach Firebase Authentication when you sign in, and to send crash reports |
| `POST_NOTIFICATIONS` | To show habit reminders. These are generated on your device; nothing is sent anywhere |
| `RECEIVE_BOOT_COMPLETED` | To restore your reminder schedule after a restart |

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
  Firebase identity and that account's habits, check-ins, journal and drafts **on this device**.

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
