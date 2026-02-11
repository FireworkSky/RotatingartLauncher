plugins {
    alias(libs.plugins.kmp)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    androidLibrary {
        namespace = "com.app.ralaunch.shared"
        compileSdk = 36
        minSdk = 28
    }

    jvmToolchain(21)

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
                implementation(libs.kotlinx.datetime)

                // Haze (Glassmorphism blur)
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
