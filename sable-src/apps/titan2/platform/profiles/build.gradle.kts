plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Sound Profiles: BlackBerry sound profiles (Normal, Loud, Vibrate only, Phone calls only, All alerts
// off), switched from the app or the Quick Settings tile. Its own app, not a Sable Tools page: Tools
// may not write system settings or run a service (tools/core/PrivilegePolicy).
android {
    // The Sable theme palette shared by the platform apps (one copy, see sable-theme/).
    sourceSets["main"].java.srcDir("../sable-theme/src/main/java")
    namespace = "org.sableos.titan2.profiles"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.sableos.titan2.profiles"
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
        targetCompatibility =
            JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
