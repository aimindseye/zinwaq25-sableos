import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM audiobook engine: manifest and timeline, chapters (MP4 `chpl`), sleep timer with an injected clock,
// playback settings and the local-only source policy. Media3, MediaMetadataRetriever and the SAF listing live in
// :core and :leisure; nothing here depends on Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":reader-model"))

    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
