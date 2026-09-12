import java.util.Properties

plugins {
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
/**
 * Release signing material never enters the repository.
 *
 * Supply it either in `keystore.properties` at the repo root (gitignored), or through the
 * KIMI_* environment variables, which is how a CI job would inject it from secrets. When
 * nothing is supplied the release build simply stays unsigned, so `assembleDebug`, the unit
 * tests and lint keep working on a machine that has no keystore at all.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun signingValue(property: String, environment: String): String? =
    (keystoreProperties.getProperty(property) ?: System.getenv(environment))?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("storeFile", "KIMI_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KIMI_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KIMI_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KIMI_KEY_PASSWORD")
val releaseKeystore = releaseStorePath?.let(rootProject::file)
val canSignRelease = releaseKeystore?.exists() == true &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

android {
    namespace = "com.forma.habits"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.forma.habits"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    signingConfigs {
        if (canSignRelease) create("release") {
            storeFile = releaseKeystore
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        getByName("debug") {
            // Crashes from a machine running the app under a debugger are noise, not signal.
            // This has to be a manifest value, not a runtime call: Crashlytics initialises from a
            // ContentProvider before Application.onCreate, so a setter in KimiApp would run too late.
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        getByName("release") {
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // R8 renames everything, so without the mapping file every crash report would be
            // unreadable. proguard-rules.pro keeps SourceFile/LineNumberTable for the same reason.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
        }
    }
}

// An unsigned release APK is easy to produce by accident and confusing to debug later.
tasks.matching { it.name.startsWith("assemble") && it.name.contains("Release") }.configureEach {
    doFirst {
        if (!canSignRelease) logger.warn(
            "Kimi: no release signing material found - producing an UNSIGNED release build. " +
            "Create keystore.properties (see README, 'Release signing') or set the KIMI_* environment variables."
        )
    }
}
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
