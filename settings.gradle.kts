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
        // TODO: Pleos Connect SDK 저장소 (Playground 가이드 확인 후 추가)
    }
}

rootProject.name = "MobileSalesAssistant"
include(":app")
