plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.sableos.notes"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.sableos.notes"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        // The Q25 image ships the release variant (product/q25/apps.tsv): R8-shrunk and signed by
        // the platform build, so it fits the dynamic partitions (a debug APK is about 30 MB).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }

    lint {
        checkTestSources = false
    }
}

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        variantBuilder.enableAndroidTest = false
    }
}

dependencies {
    implementation(project(":design"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(libs.junit)
}

extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.file("config/detekt.yml"))
    ignoreFailures = false
}

extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    ignoreFailures.set(false)
}
