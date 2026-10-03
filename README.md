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

### Linux (`.deb`, AppImage)

```bash
./gradlew packageDeb
./gradlew packageAppImage
```

Output: `build/compose/binaries/main/deb/` and `build/compose/binaries/main/app/`.

The `.deb` task uses jpackage's Debian bundler, which requires the `dpkg-deb` and `fakeroot`
tools to be installed (both are part of a normal Debian/Ubuntu build environment; on Arch-based
distros install the `dpkg` package). The Compose `AppImage` target builds jpackage's portable
application image (a self-contained directory you can run directly); it does not require
`dpkg-deb`.

After installing the `.deb`, launch OnAir from the application menu or `/opt/onair/bin/OnAir`.

## App icon

Per-OS icon assets live in `packaging/icons/` (`onair.png`, `onair.ico`, `onair.icns`) and are
wired into the packaging config. To regenerate them from the single design source:

```bash
python3 -m pip install Pillow
python3 packaging/icons/generate_icons.py
```

## Not yet covered: code signing & notarization

**Code signing and notarization are future work and are not covered by the packaging setup
here.** As a result, shipped installers are unsigned: Windows SmartScreen and macOS Gatekeeper
will warn about them, and macOS will not notarize the `.dmg`. Signing (Windows
Authenticode/macOS Developer ID) and Apple notarization will be added in a later phase.
