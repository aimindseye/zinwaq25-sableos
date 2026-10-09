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
        "com/sable/start/platform/LiveSurfaceRepository.kt",
        "com/sable/start/platform/PrivacyFactsReader.kt",
        "com/sable/start/platform/StartStateRepository.kt",
        "com/sable/start/privacy/AllAppsInteraction.kt",
        "com/sable/start/privacy/PrivacyModel.kt",
        "com/sable/start/privacy/PrivacySnapshotCache.kt",
        "com/sable/start/privacy/PrivacySummaryPolicy.kt",
        "com/sable/start/ui/SableStartScreen.kt",
        "com/sable/start/ui/SableGlyphIcon.kt",
    )

val syncSableStartPresentationSources =
    tasks.register<Sync>("syncSableStartPresentationSources") {
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

    implementation(
        platform("androidx.compose:compose-bom:2026.02.01"),
    )

    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Pure JVM tests for the All Apps privacy policy (DESIGN-KF-D).
    testImplementation("junit:junit:4.13.2")
}
