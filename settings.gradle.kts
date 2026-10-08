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
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "JMReader"
include(":app")

// Windows / macOS / Linux desktop build (Compose Multiplatform for Desktop).
// It is a separate Gradle module on purpose: `:app` stays a pure Android module, so the
// Android build cannot be broken by anything that happens in the desktop build.
include(":desktop")
