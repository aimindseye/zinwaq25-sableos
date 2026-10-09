plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Sable Tools (DESIGN-KF-C): the one launcher-visible tools product. Utilities / Diagnostics / Reports.
// Common source for every Sable keyboard-first device; device differences come only from the
// capability profile selected by ro.sable.profile.id (core/ToolsDeviceProfile.kt).
android {
    namespace = "org.sableos.tools"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.sableos.tools"
        // Platform apps ship only on Android 16+ images.
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
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
