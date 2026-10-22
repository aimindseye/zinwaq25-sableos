plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.sableos.calculator"
    compileSdk = 36
    ndkVersion = "30.0.16248370"
    defaultConfig {
        applicationId = "org.sableos.calculator"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    buildTypes {
        // The Q25 image ships the release variant (product/q25/apps.tsv): shrunk and signed by the
        // platform build, so Q4 with microG fits the dynamic partitions (the ~30 MB debug APK
        // overflowed them).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation(project(":design"))
    implementation(project(":convert"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(libs.junit)
}

android {
    sourceSets {
        getByName("main") {
            jniLibs.setSrcDirs(
                listOf(
                    layout.buildDirectory
                        .dir("generated/rustJni")
                        .get()
                        .asFile,
                ),
            )
        }
    }
}

tasks
    .matching {
        it.name.startsWith("merge") &&
            it.name.endsWith("JniLibFolders")
    }.configureEach {
        dependsOn(rootProject.tasks.named("stageCalculatorRustJni"))
    }
