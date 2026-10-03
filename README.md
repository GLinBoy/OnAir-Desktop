# OnAir

OnAir is a Kotlin Multiplatform + Compose Multiplatform desktop application for Windows, macOS, and Linux that lives in the system tray and shows in real time whether your microphone and/or webcam are currently in use, acting like a "tally light" so others can see at a glance when you're in a meeting. Built with a single `jvm("desktop")` target and a `commonMain`/`desktopMain` source-set split, it keeps platform-specific detection behind `expect`/`actual` boundaries so a future mobile companion can reuse the shared interfaces. All detection and settings are local-only, with no cloud dependency.

## Requirements

- JDK 21+ (the Gradle toolchain resolves JDK 21; packaging uses `jpackage`, which ships with the JDK).
- No Gradle install needed — use the bundled wrapper (`./gradlew`, or `gradlew.bat` on Windows).

## Run from source

```bash
./gradlew run
```

On launch OnAir shows only a tray icon. Use the tray menu to open the settings window or quit.

## Packaging

Installers are produced by Compose Desktop's `jpackage`-based tasks. `jpackage` **cannot
cross-compile**: each OS's installers must be built on that OS (a `.dmg` can only be built on
macOS, the `.msi`/`.exe` only on Windows, and the `.deb`/AppImage only on Linux).

Build a single format, or all formats compatible with the host with
`./gradlew packageDistributionForCurrentOS`.

### Windows (`.msi`, `.exe`)

```powershell
.\gradlew.bat packageMsi
.\gradlew.bat packageExe
```

Output: `build\compose\binaries\main\msi\` and `build\compose\binaries\main\exe\`.
The installers are per-user and let the user choose the install directory.

### macOS (`.dmg`)

```bash
./gradlew packageDmg
```

Output: `build/compose/binaries/main/dmg/`.

### Linux (`.deb`, single-file `.AppImage`)

```bash
./gradlew packageDeb
./gradlew createDistributable
./packaging/linux/make_appimage.sh 1.0.0
```

Output: `build/compose/binaries/main/deb/` and
`build/compose/binaries/main/appimage/OnAir-1.0.0-x86_64.AppImage`.

The `.deb` task uses jpackage's Debian bundler, which requires the `dpkg-deb` and `fakeroot`
tools to be installed (both are part of a normal Debian/Ubuntu build environment; on Arch-based
distros install the `dpkg` package). `packaging/linux/make_appimage.sh` wraps the jpackage
app-image from `createDistributable` into a single-file `.AppImage` using `appimagetool`
(downloaded and cached under `build/appimage/tools` on first run). The Compose `packageAppImage`
task also exists; in this Compose version it produces the same portable app-image directory.

After installing the `.deb`, launch OnAir from the application menu or `/opt/onair/bin/OnAir`.

### Universal jar (all OSes, requires Java 21)

```bash
./gradlew packageUniversalJar -Pversion=1.0.0
```

Output: `build/compose/jars/OnAir-1.0.0-universal.jar`. Run it with `java -jar <file>` on
Linux x64, Windows x64, or macOS arm64. It bundles the app plus the Skiko native libraries for
those platforms, so a single file works across them — but the target machine must already have
a Java 21 runtime installed. Cross-OS behaviour is best-effort.

## App icon

Per-OS icon assets live in `packaging/icons/` (`onair.png`, `onair.ico`, `onair.icns`) and are
wired into the packaging config. To regenerate them from the single design source:

```bash
python3 -m pip install Pillow
python3 packaging/icons/generate_icons.py
```

## Releases (CI/CD)

`.github/workflows/ci.yml` runs `./gradlew check` on Ubuntu, Windows, and macOS (Apple Silicon)
for every push/PR to `main`.

`.github/workflows/release.yml` builds and publishes a release whenever a version tag is pushed.
To cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The tag becomes the app version (the leading `v` is stripped; a pre-release suffix such as
`-rc1` is stripped too, since jpackage only accepts numeric versions). The major version must be
`>= 1` — jpackage's macOS bundler rejects versions that start with `0`, so use e.g. `v1.0.0`,
not `v0.1.0`. The workflow builds on per-OS runners and auto-publishes a GitHub Release with
generated notes and these assets:

| Asset                                        | Platform        |
| -------------------------------------------- | --------------- |
| `OnAir-<version>.msi`, `OnAir-<version>.exe` | Windows x64     |
| `OnAir-<version>.dmg`                        | macOS arm64     |
| `onair_<version>_amd64.deb`                  | Linux x64       |
| `OnAir-<version>-x86_64.AppImage`            | Linux x64       |
| `OnAir-<version>-universal.jar`              | Any (Java 21)   |

All builds are unsigned (see below).

## Not yet covered: code signing & notarization

**Code signing and notarization are future work and are not covered by the packaging setup
here.** As a result, shipped installers are unsigned: Windows SmartScreen and macOS Gatekeeper
will warn about them, and macOS will not notarize the `.dmg`. Signing (Windows
Authenticode/macOS Developer ID) and Apple notarization will be added in a later phase.
