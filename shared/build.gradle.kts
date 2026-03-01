plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
}

compose.resources {
    packageOfResClass = "com.app.ralaunch.shared.generated.resources"
}

kotlin {
    androidTarget {
        // Cấu hình compiler options cho Android nếu cần thiết
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // THAY ĐỔI 1: Hạ xuống Java 17
    // Java 21 có thể chạy được nhưng Java 17 ổn định hơn cho Android 7 khi dùng KMP
    jvmToolchain(17)

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("src/commonMain/kotlin")
            dependencies {
                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.materialIconsExtended)

                // Kotlinx
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                
                // DateTime: Rất quan trọng cần Desugaring trên Android < 8
                implementation(libs.kotlinx.datetime)

                // Haze (Glassmorphism blur) - Lưu ý: Sẽ không có blur trên Android 7
                implementation(libs.haze)
                implementation(libs.haze.materials)

                // Koin DI
                implementation(libs.koin.core)
                implementation(libs.koin.compose)
                implementation(libs.koin.compose.viewmodel)

                // DataStore (Common)
                implementation(libs.datastore.preferences.core)
            }
        }

        val androidMain by getting {
            kotlin.srcDir("src/androidMain/kotlin")
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.koin.android)
                implementation(libs.datastore.preferences)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.lifecycle.viewmodel.compose)
            }
        }
    }
}

android {
    namespace = "com.app.ralaunch.shared"
    compileSdk = 36

    defaultConfig {
        // THAY ĐỔI 2: Hạ minSdk xuống 25 (Android 7.1.1)
        minSdk = 25
    }

    // THAY ĐỔI 3: Cấu hình Java và Desugaring
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Bắt buộc bật để dùng kotlinx-datetime và các API hiện đại trên Android 7
        isCoreLibraryDesugaringEnabled = true
    }
}

// THAY ĐỔI 4: Thêm thư viện Desugaring cho module Shared
dependencies {
    // Bạn có thể thay bằng alias libs.desugar.jdk.libs nếu đã khai báo trong toml
    // Hoặc dùng trực tiếp chuỗi này:
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
}
