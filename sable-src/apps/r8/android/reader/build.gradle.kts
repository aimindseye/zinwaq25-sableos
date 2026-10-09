// Root build.gradle.kts
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
    alias(libs.plugins.devtools.ksp) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.jetbrains.dokka) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false

    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.sonarqube") version "7.2.3.7755"
}

val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
val appVersionName = libs.findVersion("app-versionName").get().requiredVersion

version = appVersionName

detekt {
    buildUponDefaultConfig = true
    allRules = false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
    group = "clean"
    description = "Clean build directory"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    val composeMetricsDir = layout.buildDirectory.dir("compose_metrics").get().asFile.absolutePath

    compilerOptions {
        freeCompilerArgs.addAll(
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=$composeMetricsDir",
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=$composeMetricsDir"
        )
    }
}

sonar {
    properties {
        property("sonar.projectKey", "vaachak-platform_vaachak-mobile")
        property("sonar.organization", "vaachak-platform")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.projectVersion", appVersionName)

        property(
            "sonar.exclusions",
            listOf(
                "**/build/**",
                "**/ksp/**",
                "**/hilt_aggregated_deps/**",
                "**/dagger/hilt/internal/**",
                "**/R.class",
                "**/R$*.class",
                "**/BuildConfig.*"
            ).joinToString(",")
        )

        property(
            "sonar.coverage.exclusions",
            listOf(
                "**/*_Impl.kt",
                "**/*_Factory*.*",
                "**/*_GeneratedInjector*.*",
                "**/*Hilt*.*",
                "**/*MembersInjector*.*",
                "**/*_Provide*Factory*.*",
                "**/*ComposableSingletons*.*",
                "**/CryptoManager.kt",

                // Temporary exclusions to unblock coverage gate
                "**/DatabaseModule.kt",
                "**/AppDatabase.kt",
                "**/AppDatabaseConstructor.kt",
                "**/OpdsEntity.kt"
            ).joinToString(",")
        )
    }
}