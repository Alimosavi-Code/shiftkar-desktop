import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Automatically delete legacy package directories (ir/shiftkar/neo) if extracted over an older checkout
listOf(
    "src/commonMain/kotlin/ir",
    "src/desktopMain/kotlin/ir",
    "src/commonTest/kotlin/ir"
).forEach { relPath ->
    val legacyDir = project.file(relPath)
    if (legacyDir.exists()) {
        legacyDir.deleteRecursively()
    }
}

kotlin {
    jvm("desktop") {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }

        desktopMain.dependencies {
            val osName = System.getProperty("os.name")?.lowercase().orEmpty()
            val osArch = System.getProperty("os.arch")?.lowercase().orEmpty()
            if (osName.contains("win") && (osArch.contains("aarch64") || osArch.contains("arm64"))) {
                // Compose 1.7.3 publishes windows-x64 artifact; Windows 11 ARM runs x64 native/Prism or x64 JDK cleanly
                implementation(compose.desktop.windows_x64)
            } else {
                implementation(compose.desktop.currentOs)
            }
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.okhttp)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.seyedhosseinmousavisaeedi.shiftkar.MainKt"

        nativeDistributions {
            // Note: TargetFormat.AppImage is omitted on macOS because the Compose Gradle plugin
            // throws "Unexpected target format for MacOS: AppImage" during project evaluation (Issue #3814).
            // On Linux, we use createDistributable + appimagetool to produce the single-file .AppImage!
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )
            packageName = "ShiftKar"
            packageVersion = "6.1.0"
            // Pure ASCII for jpackage / Windows WiX / macOS hdiutil compatibility
            description = "ShiftKar Smart Shift Calendar"
            vendor = "Seyed Hossein Mousavi Saeedi"
            copyright = "Copyright (c) 2026 Seyed Hossein Mousavi Saeedi"

            // Bundle required JDK modules so users never need Java installed
            modules("java.instrument", "java.management", "java.naming", "java.prefs", "java.sql", "jdk.unsupported")

            macOS {
                bundleID = "com.seyedhosseinmousavisaeedi.shiftkar"
                dockName = "ShiftKar"
                iconFile.set(project.file("packaging/ShiftKar.icns"))
            }
            windows {
                menuGroup = "ShiftKar"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8f49d561-74e2-4a89-9a0b-3c92b0e4f604"
                iconFile.set(project.file("packaging/ShiftKar.ico"))
            }
            linux {
                packageName = "shiftkar"
                debMaintainer = "h.mosavi.s@gmail.com"
                menuGroup = "Utility"
                appCategory = "Utility"
                shortcut = true
                iconFile.set(project.file("packaging/ShiftKar.png"))
            }
        }
    }
}
