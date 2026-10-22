plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.sableos.titan2.vault"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.sableos.titan2.vault"
        // Platform apps ship only on Android 16+ images; Private Space needs Android 15+.
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        release {
            // R8 shrinks and obfuscates the shipped (privileged) release APK.
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
    // FileProvider for "Open" (a one-file, read-only, temporary grant the user confirms).
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
