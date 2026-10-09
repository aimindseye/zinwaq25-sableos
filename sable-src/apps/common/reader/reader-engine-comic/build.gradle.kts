import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM comic engine: archive and folder catalogs with hostile-input limits, ComicInfo.xml parsing,
// natural page order, bounded byte-budget caching and decode planning. The Android bitmap decoding and the SAF
// directory listing live in :core; nothing here depends on Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
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
