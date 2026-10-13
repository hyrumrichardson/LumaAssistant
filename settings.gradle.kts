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
        // The Google Home APIs SDK isn't published to Google Maven yet. It's downloaded from
        // developers.home.google.com and installed into the local Maven repo (see README).
        mavenLocal {
            content {
                includeModule("com.google.android.gms", "play-services-home")
                includeModule("com.google.android.gms", "play-services-home-types")
            }
        }
    }
}

rootProject.name = "Luma"
include(":app")
