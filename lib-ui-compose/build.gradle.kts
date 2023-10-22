plugins {
    kotlin("multiplatform")
    id("com.android.library")
    alias(libs.plugins.jetbrains.compose)
}

//Roughly as per compose multiplatform template
// see https://github.com/JetBrains/compose-multiplatform-template/blob/main/shared/build.gradle.kts

kotlin {
    androidTarget {

    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {

            val version_voyager = "1.0.0-rc05"
            val version_kotlinx_datetime = "0.4.0"

            dependencies {
                api(project(":core"))
                api(project(":lib-database"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                implementation(libs.kodein.di)
                implementation(libs.moko.resources)
                implementation(libs.moko.resources.compose)


                // Compose Multiplatform Navigator
                implementation("cafe.adriel.voyager:voyager-navigator:$version_voyager")

                implementation("org.jetbrains.kotlinx:kotlinx-datetime:$version_kotlinx_datetime")

                api(libs.paging.multiplatform.common)
                api(libs.paging.multiplatform.compose)
            }
        }

        val androidMain by getting {
            dependencies {
                api(libs.activity.compose)
                api(libs.androidx.appcompat)
                api(libs.androidx.core.ktx)
                api(libs.androidx.paging.runtime)
                api(libs.androidx.paging.compose)
                implementation(compose.material)

                val version_android_material = "1.9.0"
                implementation("com.google.android.material:material:$version_android_material")

                //HTML editor
                val version_android_aztec = "v1.6.3"
                implementation("org.wordpress:aztec:$version_android_aztec")
            }
        }

        val desktopMain by getting {
            dependencies {
                api(project(":core"))
                api(compose.desktop.common)
                implementation(compose.desktop.currentOs)
                implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-beta03")
            }
        }
    }

}

android {
    compileSdk = 33
    namespace = "com.ustadmobile.libuicompose"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    sourceSets["main"].resources.srcDirs("src/commonMain/resources")

    defaultConfig {
        minSdk = 21
        targetSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}