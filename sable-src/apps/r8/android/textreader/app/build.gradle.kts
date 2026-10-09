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

    // Compose UI & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Google ML Kit Translation
    implementation("com.google.mlkit:translate:17.0.3")

    // Extended Material Icons (for the Clear, Translate, and Settings icons)
    implementation("androidx.compose.material:material-icons-extended")

// Unit Testing (Pure JVM)
    testImplementation("junit:junit:4.13.2")

    // Android UI Testing (Explicit strings to guarantee they resolve)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("org.mockito:mockito-android:5.12.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")

    // Debugging tools for Compose tests
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Required for createComposeRule() to launch its empty testing Activity
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.0") // (Use whatever your Compose version is)

    // CameraX core library
    val camerax_version = "1.3.1"
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // ML Kit Text Recognition (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Accompanist Permissions (to handle Camera permission easily in Compose)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    implementation("com.google.mlkit:text-recognition:16.0.1") // Latin (English)
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1") // Hindi, Marathi, Sanskrit
}
