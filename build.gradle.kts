import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
}

// CI overrides the version from the pushed git tag via `-Pversion=...` (or the ONAIR_VERSION
// env var). Local builds default to 0.1.0.
val rawVersion: String = providers.gradleProperty("version").orNull
    ?: System.getenv("ONAIR_VERSION")
    ?: "0.1.0"

// jpackage only accepts numeric versions (major[.minor[.patch]]), so strip pre-release/build
// suffixes (e.g. 1.2.3-rc1 -> 1.2.3) before handing the value to the native distributions.
val numericVersion: String =
    Regex("""\d+(\.\d+){1,2}""").find(rawVersion)?.value ?: "0.1.0"

// jpackage's macOS bundler rejects versions whose first component is 0, and Compose exposes no
// per-OS app-version override. On macOS only, map a 0.x.y release to a 1.x.y package version so
// the DMG can be built; the app/jar version stays 0.x.y (the DMG reports the mapped version).
val packageVersionNumber: String =
    if (System.getProperty("os.name").orEmpty().lowercase().contains("mac")) {
        numericVersion.replaceFirst(Regex("""^0(?=\.|$)"""), "1")
    } else {
        numericVersion
    }

group = "com.glinboy.onair"
version = rawVersion

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

// Extra Skiko native runtimes so a single jar can run on Linux x64, Windows x64 and macOS
// arm64. The normal desktop runtime classpath only contains the current OS's native library.
val universalJarNatives: Configuration by configurations.creating

dependencies {
    universalJarNatives("org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$composeVersion")
    universalJarNatives("org.jetbrains.compose.desktop:desktop-jvm-windows-x64:$composeVersion")
    universalJarNatives("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:$composeVersion")
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
            packageVersion = packageVersionNumber
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

// A single "runs on everything" jar for users who already have a Java 21 runtime. Skiko is the
// only OS-specific dependency, so its native runtime for Linux x64, Windows x64 and macOS arm64
// is bundled on top of the regular desktop runtime classpath.
val packageUniversalJar by tasks.registering(Jar::class) {
    group = "compose desktop"
    description = "Builds one cross-platform executable jar (requires a Java 21 runtime)."
    archiveBaseName.set("OnAir")
    archiveClassifier.set("universal")
    archiveVersion.set(rawVersion)
    destinationDirectory.set(layout.buildDirectory.dir("compose/jars"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.glinboy.onair.MainKt"
    }

    from(sourceSets["desktopMain"].output)

    val runtimeContents: Provider<List<FileTree>> =
        configurations.named("desktopRuntimeClasspath").map { runtime ->
            runtime.map { if (it.isDirectory) project.fileTree(it) else project.zipTree(it) }
        }
    val nativeContents: Provider<List<FileTree>> =
        universalJarNatives.elements.map { locations ->
            locations.map { project.zipTree(it.asFile) }
        }

    from(runtimeContents)
    from(nativeContents)

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}
