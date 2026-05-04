import org.gradle.kotlin.dsl.support.kotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.app.ralaunch"
    compileSdk = 37
    ndkVersion = "30.0.14904198 rc1"

    defaultConfig {
        applicationId = "com.app.ralaunch"
        minSdk = 28
        targetSdk = 37
        versionCode = 4
        versionName = "2.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    externalNativeBuild {
        cmake {
            path = file("../core/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    // 临时只针对真机 arm64-v8a 编译
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            isJniDebuggable = true
        }

        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false // TODO: 修复类重复后启用 R8
            isShrinkResources = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            externalNativeBuild {
                cmake {
                    arguments("-DCMAKE_BUILD_TYPE=Release")
                }
            }
        }
    }

    buildFeatures {
        viewBinding = true
        prefab = true
        compose = true
    }

    androidResources {
        noCompress += listOf("tar.gz", "tar.xz", "xz")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf(
                "lib/arm64-v8a/libc++_shared.so",
                "lib/arm64-v8a/libdotnethost.so"
            )
        }
    }
}

dependencies {
    // 本地 JAR/AAR 依赖
    implementation(files("../external/libs/libSystem.Security.Cryptography.Native.Android.jar"))
    implementation(files("../external/libs/fmod.jar"))
    implementation(files("../external/libs/fishnet-release.aar"))

    // 从 ralib 迁移的依赖
    implementation(libs.sevenzip.jbinding.android)
    implementation(libs.gson)

    // AndroidX 核心库
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    // Material Design
    implementation(libs.material)

    // Kotlin 扩展
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Haze (Glassmorphism blur)
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.runtime)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.compose.markdown)

    debugImplementation(libs.compose.ui.tooling)

    // Koin DI
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.compose)

    // 第三方工具库
    implementation(libs.glide)
    implementation(libs.konfetti)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.android.svg)
    implementation(libs.mozilla.rhino)

    // 测试依赖
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}