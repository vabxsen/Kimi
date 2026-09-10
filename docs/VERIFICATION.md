# Kimi verification

## Maintenance pass — 2026-09-10

Dark theme, string externalization, Firebase App Check, derived-stat caching, rest-day copy, CI and README screenshots.

**Result: 20 JVM unit tests + 9 connected Android tests passed. Android lint: 0 errors, 28 warnings.**

Ran on this machine against the `habit_test` AVD (Android 15 / API 35, 1080 × 2400, density 420), JDK 21, Gradle 8.14.5, Android SDK platform 36:

```text
gradlew.bat assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest
```

The three account tests were run with the isolated `demo-kimi-auth` Auth emulator running locally, as documented in the README.

### What changed

- **Dark theme.** Every colour now resolves through `KimiPalette` behind a `CompositionLocal`, so the token names screens already used (`Cream`, `Ink`, `Purple`…) swap per theme without touching call sites. Added `values-night` styles so the launch window does not flash light. Status-bar and navigation-bar icon colours follow Kimi's own choice rather than only the device's.
- **Appearance preference.** Automatic / Light / Dark in Settings, stored per device in its own `kimi_appearance` preferences file — deliberately outside the per-account stores, so it is never written to a backup and never changes when accounts switch.
- **Strings.** All user-facing copy moved to `res/values/strings.xml` (including plurals for counted phrases). Habit dayparts remain stored as stable English keys so existing backups stay valid; only their labels are translated. Calendar column headings now come from the device locale instead of hardcoded `M T W T F S S`.
- **Translatable failures.** `KimiMessage` carries a string resource id, letting `BackupCodec` and `HabitStore` stay free of any `Context` — so the JVM tests still exercise them directly — while the UI renders the message in the user's language. This also stops raw exception text from reaching the snackbar.
- **App Check.** `KimiApp` installs Play Integrity (release) / debug (debug) providers. Enforcement is a server-side switch and is deliberately still off; see the README for the order to turn it on safely.
- **Derived stats.** Streaks and 30-day consistency are computed once per `(state, today)` in `Stats.kt` instead of once per row drawn. A streak walks back to the habit's creation date, so the old inline version got slower with every day a habit survived.
- **Rest days.** A day with nothing scheduled is never drawn as a zero. `InsightSummary` now carries a
  `DayScore(date, percent, rest)` per column so the screens do not each re-derive it. Today reads
  "Rest day"; the seven-day chart shows a dash and a flat marker; the calendar leaves the cell
  unfilled with a muted number and gains a **Rest day** legend entry; a habit's week strip leaves
  days it was never due on blank rather than marking them missed. The summary figures already
  excluded these days, so this is the per-day visuals catching up with the arithmetic.

### Verified by running, not only by reading

The app was installed on the emulator and driven by hand through first run, Today, Insights, Journal and Settings in both themes.

Two real defects were found this way and fixed:

1. The Canvas mascot was drawing its petals from the *tile* yellow and its face from the theme ink, so after dark it rendered as a dark-olive flower with a pale face. The mascot is a fixed illustration, not themed UI; its face is now pinned to the illustration's own ink and its default petal to the bright highlight, so it looks identical in both themes.
2. The new appearance card originally used a horizontally scrolling row of chips. That gave the Settings page a second scroll container, and `AccountFlowTest` — which finds the sign-in button with `onNode(hasScrollAction())` — began failing. Three short chips always fit, so the row no longer scrolls.

Forcing Light while the device was in night mode was confirmed to survive a process restart, with the status bar icons following Kimi's choice rather than the system's.

### API key restriction — 2026-09-10

The Firebase Android API key was restricted in Google Cloud console to Android apps, allowing only `com.forma.habits` with the debug SHA-1 `4A:75:73:0F:0D:79:34:52:86:6F:17:D6:ED:5D:DF:83:FF:A4:FF:D1`. Verified live against `identitytoolkit.googleapis.com/v1/accounts:signUp`, using a deliberately malformed email so no account could be created in any case:

| Caller | Result |
| --- | --- |
| Package + registered debug cert (what the app sends) | `400 INVALID_EMAIL` — passed the restriction |
| Right package, wrong cert | `403 API_KEY_ANDROID_APP_BLOCKED` |
| No Android headers (a plain script) | `403 API_KEY_ANDROID_APP_BLOCKED` |

The key value was checked against `app/google-services.json` before editing, so the restriction was applied to the key the app actually ships. The project's separate *Browser key* was left untouched.

App Check enforcement remains **off**. It was considered and deliberately deferred: no release keystore or signing config exists yet, so the release app cannot be registered for Play Integrity, and no attested request has ever been observed for this project. Enabling enforcement in that state would reject every sign-in, sign-up and password reset on `kimi-track`, including from debug builds.

### Release signing config — 2026-09-10

Added a release signing config reading `keystore.properties` (gitignored) or `KIMI_*` environment
variables, with `keystore.properties.example` as the template. The keystore itself was deliberately
not created here: choosing and holding that password is the owner's, since losing it forfeits the
ability to update the app on Play and leaking it lets someone ship signed as them. The README
carries the `keytool` command and the three places a new fingerprint has to be registered.

Building the release variant immediately exposed a latent compile break introduced with App Check
earlier the same day: `KimiApp` referenced `DebugAppCheckProviderFactory`, which comes from a
`debugImplementation` dependency and therefore does not exist in the release variant. `BuildConfig.DEBUG`
does not help, because the import must resolve for every variant at compile time. **The release build
had been broken since App Check was added and nothing caught it, because only debug was ever built.**
Fixed by moving the choice into `src/debug` and `src/release`, each defining `appCheckProviderFactory()`.

Verified: `assembleDebug testDebugUnitTest lintDebug assembleRelease` all pass with no keystore
present — `lintVitalRelease` ran for the first time and is clean — producing `app-release-unsigned.apk`
(15.9 MB) after the intended warning. 20 JVM unit tests pass. R8 stays off until a signed release has
been installed and exercised.

### R8 — 2026-09-10

R8 enabled for release (`isMinifyEnabled = true`, `proguard-android-optimize.txt` plus
`app/proguard-rules.pro`). **APK 15.9 MB → 3.68 MB, a 77% reduction.** Keep rules are minimal by
design: `BackupCodec` names every JSON field explicitly and uses no reflection, so the models
obfuscate safely. Only `ThemeMode` needs keeping, because `ThemeSetting` persists its constant names
and reads them back with `valueOf` — obfuscated names would silently reset the appearance preference
across builds.

Verified by installing the minified APK on the `habit_test` emulator (signed with the debug key for
testing only, which is also the fingerprint on the API key allow list, so Firebase was genuinely
exercised) and driving it by hand:

| Path | Why it was at risk | Result |
| --- | --- | --- |
| First run, starter habits | seeded from `string-array` resources | Created correctly |
| Check-in, then process kill and relaunch | `BackupCodec` encode → prefs → decode | 1 of 5 done, 1 day streak, 20% — survived, no damaged-save notice |
| Journal entry, then process kill | `Reflection` round-trip | Entry and mood persisted |
| Light/Dark preference, then process kill | enum `valueOf` on a persisted name | Dark persisted — the keep rule works |
| Account screen | Firebase Auth + Credential Manager init | Rendered, Firebase initialised, no reflection errors |
| Password reset request | real Identity Toolkit call over the network | Returned the success message, so R8 and the API key restriction both let the release build through |
| Today / Habits / Calendar / Insights / Journal | Compose, fonts, Canvas mascot, plurals | All render correctly in both themes |

No `ClassNotFoundException`, `NoSuchMethodError` or `NoClassDefFoundError` at any point.
`lintVitalRelease` is clean. Resource shrinking was not enabled, so no string or drawable was removed.

### Not verified

- Release signing, Play App Signing registration, and App Check enforcement — all require console access and a release keystore.
- Real Google sign-in consent and real verification/reset email delivery.
- No physical device test was performed.

---

## Firebase Authentication verification — 2026-09-10

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

The new sign-in and create-account screens were visually inspected. At 1.3× Android font size the account form reflows, and creation/sign-in controls remain reachable by scrolling. The original font size was restored. Google's button launched native account setup; a complete real Google login still needs a Google account. No physical device test was performed.

## Data and limits

The original emulator guest data was restored after tests. Account credentials are managed by Firebase; habit/journal spaces are local and separate by Firebase UID. Guest progress is copied only through the explicit copy action, and only into an empty account space. There is **no cloud habit/journal sync**. Backups and old device data are not remotely erased by account deletion.

The APK is debug-signed. Production distribution needs its release/Play signing certificate registered in Firebase. Google verification/consent completion and real mailbox delivery were not claimed as tested.

APK SHA-256: `8209811f2e4442ff4c278921a1043a8587d98acd2d0557d2bc8a52c851b0c559`
