pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // KHAI BÁO VERSION CHO SAFE ARGS Ở ĐÂY
        id("com.android.application") version "8.6.1"
        id("com.google.gms.google-services") version "4.4.2"
        id("androidx.navigation.safeargs") version "2.8.3"
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
