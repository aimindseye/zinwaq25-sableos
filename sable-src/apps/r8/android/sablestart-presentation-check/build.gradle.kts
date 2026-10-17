plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.sableos.start.presentationcheck"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
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

    sourceSets {
        getByName("main") {
            java.srcDir(
                layout.buildDirectory.dir("generated/sablestartPresentation"),
            )
        }
    }
}

val canonicalPresentationRoot =
    layout.projectDirectory.dir(
        "../../../../src/android/packages/apps/SableStart/src",
    )
val generatedPresentationDir =
    layout.buildDirectory.dir("generated/sablestartPresentation")
val canonicalPresentationSources =
    listOf(
        "com/sable/start/live/LiveSurfaceModels.kt",
        "com/sable/start/model/AppEntry.kt",
        "com/sable/start/model/CommandQuery.kt",
        "com/sable/start/model/CoreAppIdentity.kt",
        "com/sable/start/model/QuickBar.kt",
        "com/sable/start/platform/DeviceStatsSampler.kt",
        "com/sable/start/platform/LiveSurfaceRepository.kt",
        "com/sable/start/platform/PrivacyFactsReader.kt",
        "com/sable/start/platform/StartStateRepository.kt",
        "com/sable/start/privacy/AllAppsInteraction.kt",
        "com/sable/start/privacy/PrivacyModel.kt",
        "com/sable/start/privacy/PrivacySnapshotCache.kt",
        "com/sable/start/privacy/PrivacySummaryPolicy.kt",
        "com/sable/start/ui/SableStartScreen.kt",
        "com/sable/start/ui/SableGlyphIcon.kt",
        "com/sable/start/ui/SableStatusRings.kt",
    )

val syncSableStartPresentationSources =
    tasks.register<Sync>("syncSableStartPresentationSources") {
        group = "build"
        description = "Copy the canonical SableStart presentation sources into this check module"
        into(generatedPresentationDir)
        canonicalPresentationSources.forEach { relativePath ->
            val parent = relativePath.substringBeforeLast("/")
            from(canonicalPresentationRoot.file(relativePath)) {
                into(parent)
            }
        }
    }

tasks.configureEach {
    if (
        name != "syncSableStartPresentationSources" &&
        (
            name == "preBuild" ||
                name.contains("lint", ignoreCase = true) ||
                name.startsWith("compileDebugKotlin") ||
                name.startsWith("compileReleaseKotlin") ||
                name.contains("UnitTestKotlin")
        )
    ) {
        dependsOn(syncSableStartPresentationSources)
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

    implementation(
        platform(libs.androidx.compose.bom),
    )

    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation(libs.kotlinx.coroutines.android)

    // Pure JVM tests for the All Apps privacy policy (DESIGN-KF-D).
    testImplementation(libs.junit)
}
