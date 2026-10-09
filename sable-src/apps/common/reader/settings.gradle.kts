pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // Fixed, reviewable repository set. No JitPack: every dependency must come from Google or
    // Maven Central so Gradle dependency verification can pin it.
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SableReader"
include(":leisure")
include(":core")
include(":reader-model")
include(":reader-engine-pdf")
include(":reader-engine-comic")
include(":reader-engine-audio")
