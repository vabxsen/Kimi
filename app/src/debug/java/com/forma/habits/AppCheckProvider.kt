package com.forma.habits

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds cannot pass Play Integrity - they are not installed from Play and are signed with
 * the debug key - so they attest with a token that has to be registered in the Firebase console
 * (App Check -> Apps -> Manage debug tokens). The token is printed to Logcat on first run.
 *
 * This lives in the debug source set because `firebase-appcheck-debug` is a `debugImplementation`
 * dependency: referencing it from `src/main` would not compile the release variant, and would risk
 * shipping debug attestation in a production build.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
