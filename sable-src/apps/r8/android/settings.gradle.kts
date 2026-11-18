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
include(":media")
include(":hub")
include(":messages")
include(":weather")
include(":calendar")
include(":notes")
include(":tasks")
include(":browser")
include(":assistant")
