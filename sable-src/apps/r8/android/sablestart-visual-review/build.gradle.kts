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

    buildTypes {
        release {
            // The image ships the debug variant (product/q25/apps.tsv); a release build is shrunk and obfuscated.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
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
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.collection)
        implementation(libs.androidx.collection.jvm)
        implementation(libs.androidx.collection.ktx)
        implementation(libs.androidx.lifecycle.common)
        implementation(libs.androidx.lifecycle.common.java8)
        implementation(libs.androidx.lifecycle.common.jvm)
        implementation(libs.androidx.lifecycle.process)
        implementation(libs.androidx.lifecycle.runtime)
        implementation(libs.androidx.lifecycle.runtime.android)
        implementation(libs.androidx.lifecycle.runtime.ktx)
        implementation(libs.androidx.lifecycle.runtime.ktx.android)
    }

    implementation(project(":design"))
    implementation(project(":sablestart-presentation-check"))

    implementation(
        platform(libs.androidx.compose.bom),
    )

    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
