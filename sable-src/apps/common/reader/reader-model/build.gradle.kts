import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM logic shared by the Sable Reader engines and the Android app.
// No Android dependency: everything here is unit-testable on a plain JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.sqlite.jdbc)
}

tasks.test {
    useJUnit()
}
