plugins {
    id("com.android.application")
    id("androidx.navigation.safeargs")   // Safe Args cho Java
    id("com.google.gms.google-services") // Firebase
    // Nếu module có Kotlin:
    // id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36   // giữ như bạn đang dùng

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"
    }

    // ✅ Chuyển ngôn ngữ Java sang 17 để dùng cú pháp hiện đại
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // (Tuỳ chọn) Nếu bạn cần API Java mới hơn trên minSdk thấp:
        // coreLibraryDesugaringEnabled = true
    }

    // ✅ Nếu có Kotlin trong module, bật JVM 17
    // kotlinOptions {
    //     jvmTarget = "17"
    // }

    buildFeatures {
        viewBinding = true
        // dataBinding = true // nếu bạn dùng
    }

    // (Tuỳ chọn) Nếu bạn gặp cảnh báo về R/namespace:
    // androidResources {
    //     generateLocaleConfig = true
    // }
}

dependencies {
    // (Tuỳ chọn) Desugaring nếu bạn dùng API mới của Java (time, stream…)
    // coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.navigation:navigation-fragment:2.8.3")
    implementation("androidx.navigation:navigation-ui:2.8.3")

    // Room runtime
    implementation("androidx.room:room-runtime:2.6.1")

    // Annotation processor cho Java (nếu bạn viết code bằng Java)
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // Firebase BoM & libs (giữ như bạn đã cấu hình)
    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")

    // Room, Navigation… giữ nguyên như bạn đang dùng

    implementation("com.google.android.material:material:1.12.0")

    // Glide cho load ảnh
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
}
