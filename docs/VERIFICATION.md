# Kimi Firebase Authentication verification — 2026-09-10

Final build: **22 tests passed** (13 JVM + 9 connected Android tests). Android lint: **0 errors, 28 warnings**; remaining warnings are dependency-update and platform/Compose recommendations. APK signature verified (v2), debug SHA matches the Firebase Android OAuth registration.

## Firebase project

- Project: `kimi-track` (952471890795).
- Added Android registration `1:952471890795:android:c6a437cf29cae1b7a13a2e` for the installed package `com.forma.habits`; preserved the existing `com.kimi.app` registration.
- Google and email/password provider deployment succeeded. Downloaded SDK configuration contains the matching Android SHA-1 client and web OAuth client.
- Live project smoke check passed: disposable email/password registration, sign-in, and account lookup. The temporary account was deleted and a subsequent sign-in was rejected. No real verification/reset emails were sent during testing.

## Android runtime checks

Visible `habit_test` emulator, Android 15 / API 35, 1080 × 2400, density 420.

The three new connected tests use a named Firebase SDK app and the isolated **demo-kimi-auth** Auth emulator:

1. Real Compose create-account form, guest import, sign-out, sign-in, activity recreation/session persistence, reauthenticated account deletion; guest data retained.
2. SDK verification email/action-code completion, password-reset action-code completion, profile-name update, rejected invalid password, successful new-password login and account deletion.
3. Separate accounts' habits and drafts, sign-out to guest, old notification action rejected after account switch, returning to the original account, and draft cleanup after deletion.

All six existing connected habit/journal/backup/notification tests also passed, including actual AlarmManager delivery and its completion action. Unit tests cover due dates, streaks, schedule history, backup validation, reminder timing, DST and idempotent check-ins.

## Visual checks

The original six screens and habit editor were recaptured using the approved fixture. Default layouts remain intact on Today, Habits, Calendar, Insights, Journal and the editor. Raw screenshot equality is 98.74–99.07% for these screens (mean per-channel differences below 0.012/255), with small rasterization differences; this is not a zero-difference pixel claim. Settings intentionally adds the account card and accurate privacy copy. Raw measurements are in `visual-comparison.json`.

The new sign-in and create-account screens were visually inspected. At 1.3× Android font size the account form reflows, and creation/sign-in controls remain reachable by scrolling. The original font size was restored. Google’s button launched native account setup; a complete real Google login still needs a Google account. No physical device test was performed.

## Data and limits

The original emulator guest data was restored after tests. Account credentials are managed by Firebase; habit/journal spaces are local and separate by Firebase UID. Guest progress is copied only through the explicit copy action, and only into an empty account space. There is **no cloud habit/journal sync**. Backups and old device data are not remotely erased by account deletion.

The APK is debug-signed. Production distribution needs its release/Play signing certificate registered in Firebase. Google verification/consent completion and real mailbox delivery were not claimed as tested.

APK SHA-256: `8209811f2e4442ff4c278921a1043a8587d98acd2d0557d2bc8a52c851b0c559`
