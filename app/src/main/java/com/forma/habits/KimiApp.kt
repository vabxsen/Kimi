package com.forma.habits

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Installs Firebase App Check before anything touches Authentication.
 *
 * `google-services.json` is public client configuration and this repository is public, so the
 * project's API key is not a secret. App Check is what stops that key from being useful to anyone
 * else: Play Integrity attests that a request really came from a genuine install of Kimi, which
 * keeps strangers from farming accounts against the project's sign-up quota.
 *
 * Which provider is installed is decided per build variant by [appCheckProviderFactory], defined
 * separately in `src/debug` and `src/release`. It has to be split that way because the debug
 * provider is a `debugImplementation` dependency and does not exist in a release build at all.
 *
 * Enforcement is a server-side switch in the Firebase console — until it is turned on for
 * Authentication, unattested requests still succeed and this is purely additive. Turn it on only
 * after a release build with a registered Play App Signing certificate has been seen reporting
 * valid tokens, or existing installs will start failing to sign in.
 */
class KimiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeSetting.load(this)
        // App Check must never be the reason Kimi fails to start; a missing provider only costs
        // attestation, and Authentication keeps working while enforcement is off.
        runCatching {
            FirebaseApp.initializeApp(this)
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
        }
    }
}
