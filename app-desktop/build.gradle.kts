import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

//Roughly as per
// https://github.com/JetBrains/compose-multiplatform-desktop-template#readme

//Slightly different to the same module on the all-in-one template, but seems to work better for
//running within the IDE

plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.conveyor)
}

kotlin {
    jvmToolchain(17)
}

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":core"))
    implementation(project(":lib-ui-compose"))
    implementation(project(":lib-util"))
    implementation(project(":lib-cache"))
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.okhttp)
    implementation(libs.quartz)
    implementation(libs.napier)
    implementation(libs.javaffmpeg)

    api(libs.moko.resources)
    api(libs.moko.resources.compose)
    api(libs.precompose)
    api(libs.precompose.viewmodel)
    implementation(libs.libphonenumber.google)
    implementation(libs.kamel)
    implementation(libs.ktor.client.okhttp)

    //as per https://conveyor.hydraulic.dev/13.0/tutorial/tortoise/2-gradle/#adapting-a-compose-desktop-app
    linuxAmd64(compose.desktop.linux_x64)
    macAmd64(compose.desktop.macos_x64)
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
}

//As per https://conveyor.hydraulic.dev/13.0/tutorial/tortoise/2-gradle/#adapting-a-compose-desktop-app
configurations.all {
    attributes {
        attribute(Attribute.of("ui", String::class.java), "awt")
    }
}

compose.desktop {
    application {
        mainClass = "com.ustadmobile.port.desktop.AppKt"

        nativeDistributions {
            // https://github.com/JetBrains/compose-multiplatform/blob/master/tutorials/Native_distributions_and_local_execution/README.md
            modules("java.sql")
            modules("java.base")
            modules("java.compiler")
            modules("java.instrument")
            modules("java.management")
            modules("java.naming")
            modules("java.rmi")
            modules("jdk.unsupported")
            modules("jdk.xml.dom")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            packageVersion = "1.0.0"
            packageName = "UstadMobile"
            version = rootProject.version
            description = "Ustad Mobile"
            copyright = "© UstadMobile FZ-LLC."
            licenseFile.set(rootProject.file("LICENSE"))
            windows {
                packageVersion = "1.0.0"
                msiPackageVersion = "1.0.0"
                exePackageVersion = "1.0.0"
                iconFile.set(project.file("ustad-logo.ico"))
            }
        }
    }
}
