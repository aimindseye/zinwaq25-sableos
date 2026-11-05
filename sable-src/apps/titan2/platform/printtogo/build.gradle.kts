plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Sable Print To Go, after BlackBerry 10's Print To Go: while the user has it on, the phone is a driverless
// network printer on the Wi-Fi (IPP Everywhere and AirPrint) and saves every print as a PDF in
// Documents/Print To Go. Nothing is printed on paper and nothing leaves the phone.
android {
    // The Sable theme palette shared by the platform apps (one copy, see sable-theme/).
    sourceSets["main"].java.srcDir("../sable-theme/src/main/java")
    namespace = "org.sableos.titan2.printtogo"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.sableos.titan2.printtogo"
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
