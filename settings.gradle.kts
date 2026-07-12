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

rootProject.name = "BlockEditorDemo"
include(
    ":blockeditor-domain",
    ":blockeditor-registry",
    ":blockeditor-layout",
    ":blockeditor-interaction",
    ":blockeditor-compose",
    ":blockeditor-serialization",
    ":blockeditor-validation",
    ":blockeditor-ir",
    ":blockeditor-emscript",
    ":app",
)
