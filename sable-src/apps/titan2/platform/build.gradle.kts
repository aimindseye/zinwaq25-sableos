// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// D2 quality enforcement. Detekt uses the canonical SableOS configuration copied unchanged to config/detekt.yml
// (provenance: config/DETEKT_PROVENANCE.env). Both tools fail the build; there is no report-only mode.
val detektConfigFile = rootProject.file("../../../config/detekt.yml")

subprojects {
    pluginManager.apply("io.gitlab.arturbosch.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        ignoreFailures = false
        parallel = true
        config.setFrom(detektConfigFile)
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        android.set(true)
        ignoreFailures.set(false)
        outputToConsole.set(true)
    }
}
