plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Sable Phone: the default Phone app, BlackBerry 10 style and keyboard first. Call list, dial pad, contacts
// search, speed dial on letter keys, and its own incoming and in-call screens (an InCallService). Replaces the
// LineageOS Dialer from Q4 (product/q25/apps.tsv). Nothing leaves the phone.
android {
    // The Sable theme palette shared by the platform apps (one copy, see sable-theme/).
    sourceSets["main"].java.srcDir("../sable-theme/src/main/java")
    namespace = "org.sableos.phone"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.sableos.phone"
        // Platform apps ship only on Android 16+ images.
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            // R8 shrinks and obfuscates the shipped release APK.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
