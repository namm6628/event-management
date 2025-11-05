// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Khai báo version ở root để module apply được
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false

    // Nếu bạn dùng Kotlin Android (có module Kotlin) thì để thêm:
    // id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Safe Args (Java/Kotlin đều dùng id này với AGP 8.x)
    id("androidx.navigation.safeargs") version "2.8.3" apply false

    // Google Services (Firebase)
    id("com.google.gms.google-services") version "4.4.2" apply false


}