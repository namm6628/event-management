pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // KHAI BÁO VERSION CHO SAFE ARGS Ở ĐÂY
        id("androidx.navigation.safeargs") version "2.7.7"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyApplication" // hoặc tên bạn đặt
include(":app")
