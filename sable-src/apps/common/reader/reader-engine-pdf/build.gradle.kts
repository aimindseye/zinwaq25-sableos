import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM PDF engine logic: bounded page cache, render sizing, page navigation and document lifecycle.
// The Android PdfRenderer binding lives in :core; nothing here depends on Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
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
