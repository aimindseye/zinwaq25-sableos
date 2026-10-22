plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.sableos.games"
    compileSdk = 36
    ndkVersion = "30.0.16248370"

    defaultConfig {
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    flavorDimensions += "game"
    productFlavors {
        create("sudoku") {
            dimension = "game"
            applicationId = "org.sableos.sudoku"
            resValue("string", "app_name", "Sable Sudoku")
            buildConfigField("String", "SABLE_GAME", "\"sudoku\"")
        }
        create("minesweeper") {
            dimension = "game"
            applicationId = "org.sableos.minesweeper"
            resValue("string", "app_name", "Sable Minesweeper")
            buildConfigField("String", "SABLE_GAME", "\"minesweeper\"")
        }
        create("game2048") {
            dimension = "game"
            applicationId = "org.sableos.game2048"
            resValue("string", "app_name", "Sable 2048")
            buildConfigField("String", "SABLE_GAME", "\"2048\"")
        }
    }

    buildTypes {
        // The Q25 image ships the release variants (product/q25/apps.tsv): shrunk and signed by
        // the platform build, so the three games fit the dynamic partitions (the ~29 MB debug
        // APKs each overflowed them on the first Q2 build).
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

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

dependencies {
    implementation(project(":design"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(libs.junit)
}

tasks
    .matching {
        it.name.startsWith("merge") &&
            it.name.endsWith("JniLibFolders")
    }.configureEach {
        dependsOn(rootProject.tasks.named("stageGamesRustJni"))
    }

androidComponents {
    beforeVariants(selector().all()) { variantBuilder ->
        variantBuilder.enableAndroidTest = false
    }
}
