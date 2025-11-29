plugins {
    id("com.android.application")
    id("androidx.navigation.safeargs")   // Safe Args cho Java
    id("com.google.gms.google-services") // Firebase
    id("org.jetbrains.kotlin.android")
    // Nếu module có Kotlin:
    // id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35   // giữ như bạn đang dùng


    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 35

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

    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-functions:20.4.0")

    // Lifecycle (đang dùng ViewModel/LiveData)
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.6")

    // Room, Navigation… giữ nguyên như bạn đang dùng
    // UI căn bản (nếu thiếu)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")

    // Glide cho load ảnh
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    //Thêm video
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("com.android.volley:volley:1.2.1")

    implementation("androidx.cardview:cardview:1.0.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")



}





