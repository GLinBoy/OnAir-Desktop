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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "OnAir"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.glinboy.onair"
            }
        }
    }
}
