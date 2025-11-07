// Top-level build file
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false

    // Nếu có module Kotlin:
    // id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Safe Args (áp dụng ở module app qua plugins {})
    id("androidx.navigation.safeargs") version "2.8.3" apply false

    // Google Services (áp dụng ở module app qua plugins {})
    id("com.google.gms.google-services") version "4.4.2" apply false
}

// Không cần buildscript/classpath nữa khi dùng plugins DSL.
