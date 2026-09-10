package com.forma.habits

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release builds attest with Play Integrity, which checks that the app is a genuine, unmodified
 * install of this package signed by the registered certificate. This requires the release app to
 * be registered in the Firebase console with its Play App Signing certificate.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
