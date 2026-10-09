pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sable-r8-apps"
include(":design")
include(":calculator")
include(":convert")
include(":games")
include(":media")
include(":hub")
include(":messages")
include(":sablestart-presentation-check")
include(":sablestart-visual-review")
include(":weather")
include(":calendar")
