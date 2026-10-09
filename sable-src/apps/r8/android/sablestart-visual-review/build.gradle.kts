plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.sableos.start.visualreview"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.sableos.start.visualreview"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-r9l9"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(
                org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17,
            )
        }
    }

    lint {
        ignoreTestSources = true
    }
}

dependencies {
    constraints {
        implementation("androidx.core:core-ktx:1.16.0")
        implementation("androidx.collection:collection:1.5.0")
        implementation("androidx.collection:collection-jvm:1.5.0")
        implementation("androidx.collection:collection-ktx:1.5.0")
        implementation("androidx.lifecycle:lifecycle-common:2.9.4")
        implementation("androidx.lifecycle:lifecycle-common-java8:2.9.4")
        implementation("androidx.lifecycle:lifecycle-common-jvm:2.9.4")
        implementation("androidx.lifecycle:lifecycle-process:2.9.4")
        implementation("androidx.lifecycle:lifecycle-runtime:2.9.4")
        implementation("androidx.lifecycle:lifecycle-runtime-android:2.9.4")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.4")
    }

    implementation(project(":design"))
    implementation(project(":sablestart-presentation-check"))

    implementation(
        platform("androidx.compose:compose-bom:2026.02.01"),
    )

    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
