/*
 *
 *  *
 *  *  *  Copyright (c) 2026 Piyush Daiya
 *  *  *  *
 *  *  *  * Permission is hereby granted, free of charge, to any person obtaining a copy
 *  *  *  * of this software and associated documentation files (the "Software"), to deal
 *  *  *  * in the Software without restriction, including without limitation the rights
 *  *  *  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  *  *  * copies of the Software, and to permit persons to whom the Software is
 *  *  *  * furnished to do so, subject to the following conditions:
 *  *  *  *
 *  *  *  * The above copyright notice and this permission notice shall be included in all
 *  *  *  * copies or substantial portions of the Software.
 *  *  *  *
 *  *  *  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  *  *  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  *  *  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  *  *  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  *  *  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  *  *  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  *  *  * SOFTWARE.
 *  *
 *
 */


import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") // <-- Remove 'version "2.0.21"' from here

    // IMPORTANT: Because you are using Kotlin 2.0+, you must add the new Compose compiler plugin here:
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseKeystoreFile = file("vaachak-key.jks")
val releaseStorePassword =
    System.getenv("ORG_GRADLE_PROJECT_VAACHAK_KEYSTORE_PASSWORD")
        ?: providers.gradleProperty("VAACHAK_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias =
    System.getenv("ORG_GRADLE_PROJECT_VAACHAK_KEY_ALIAS")
        ?: providers.gradleProperty("VAACHAK_KEY_ALIAS").orNull
val releaseKeyPassword =
    System.getenv("ORG_GRADLE_PROJECT_VAACHAK_KEY_PASSWORD")
        ?: providers.gradleProperty("VAACHAK_KEY_PASSWORD").orNull
android {
    namespace = "org.vaachak.textreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.vaachak.textreader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    applicationVariants.all {
        val variantName = this.buildType.name // "debug" or "release"

        outputs.all {
            val outputImpl = this as? BaseVariantOutputImpl

            // This will name it exactly Vaachak-TextReader.apk
            //outputImpl?.outputFileName = "Vaachak-TextReader.apk"

            // PRO TIP: If you want to tell your local builds apart,
            // you can uncomment the line below instead to get
            // Vaachak-TextReader-debug.apk and Vaachak-TextReader-release.apk
            outputImpl?.outputFileName = "Vaachak-TextReader-${variantName}.apk"
        }
    }
    signingConfigs.create("release") {


        storeFile = releaseKeystoreFile
        storePassword =  releaseStorePassword
        keyAlias =  releaseKeyAlias
        keyPassword =  releaseKeyPassword
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
dependencies {
    // Core Android & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose UI & Material 3, and the extended Material icons (Clear, Translate, Settings)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // CameraX
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Google ML Kit: translation, and text recognition (Latin for English, Devanagari for Hindi, Marathi, Sanskrit)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.devanagari)

    // Accompanist Permissions (to handle Camera permission easily in Compose)
    implementation(libs.accompanist.permissions)

    // Unit Testing (Pure JVM)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)

    // Android UI Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom.androidtest))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockito.android)

    // Debugging tools for Compose tests; ui-test-manifest is required for createComposeRule() to launch its empty
    // testing Activity
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
