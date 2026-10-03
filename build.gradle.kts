import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
}

group = "com.glinboy.onair"
version = "0.1.0"

val composeVersion = "1.12.1"
val composeMaterial3Version = "1.12.0-alpha03"

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
                implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
                implementation("org.jetbrains.compose.ui:ui:$composeVersion")
                implementation("org.jetbrains.compose.material3:material3:$composeMaterial3Version")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                // Phase 5: local settings persistence as JSON in the OS config directory.
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("com.dorkbox:SystemTray:4.4")
                // Native detection (Phase 4): Core Audio / CoreMediaIO on macOS, Windows registry APIs.
                implementation("net.java.dev.jna:jna:5.17.0")
                implementation("net.java.dev.jna:jna-platform:5.17.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.glinboy.onair.MainKt"

        nativeDistributions {
            // Per-OS installer formats. jpackage can only build the formats of the OS it runs on,
            // so build each OS's installers on that OS (see README.md).
            targetFormats(
                TargetFormat.Dmg, // macOS
                TargetFormat.Msi, // Windows (MSI installer)
                TargetFormat.Exe, // Windows (EXE installer)
                TargetFormat.Deb, // Linux (Debian/Ubuntu package)
                TargetFormat.AppImage, // Linux (portable AppImage)
            )
            packageName = "OnAir"
            packageVersion = "0.1.0"
            description = "System-tray tally light showing when your microphone or camera is in use"
            vendor = "GLinBoy"

            macOS {
                bundleID = "com.glinboy.onair"
                iconFile.set(project.file("packaging/icons/onair.icns"))
            }

            windows {
                iconFile.set(project.file("packaging/icons/onair.ico"))
                menuGroup = "OnAir"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Stable ID so MSI upgrades replace the previous install instead of stacking.
                upgradeUuid = "fb8165f1-da31-426a-bc16-1aa7c8228ffd"
            }

            linux {
                iconFile.set(project.file("packaging/icons/onair.png"))
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                shortcut = true
            }
        }
    }
}
