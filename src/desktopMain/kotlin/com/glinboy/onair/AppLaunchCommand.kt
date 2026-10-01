package com.glinboy.onair

import java.io.File

/**
 * Builds the command line that the OS autostart entry should run to relaunch OnAir.
 *
 * Two cases matter:
 *  - **Packaged app** (jpackage app-image/installer): the current process is the native launcher
 *    executable, so its [ProcessHandle.Info.command] is exactly what we want.
 *  - **Dev run** (`./gradlew run`): the process is the JDK's `java`, so we reconstruct a
 *    `java -cp <classpath> com.glinboy.onair.MainKt` invocation from the JVM system properties.
 *    This works for local testing but the classpath can go stale if the build directory moves.
 */
internal object AppLaunchCommand {
    private const val MAIN_CLASS_FALLBACK = "com.glinboy.onair.MainKt"

    fun build(): List<String> {
        val info = ProcessHandle.current().info()
        val command = info.command().orElse(null)
        if (!command.isNullOrBlank() && !isJavaLauncher(command)) {
            @Suppress("UNCHECKED_CAST")
            val args = info.arguments().orElse(emptyArray<String>())
            return listOf(command) + args.toList()
        }
        return javaCommand()
    }

    private fun isJavaLauncher(path: String): Boolean = when (File(path).name.lowercase()) {
        "java", "javaw", "java.exe", "javaw.exe" -> true
        else -> false
    }

    private fun javaCommand(): List<String> {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val binDir = File(System.getProperty("java.home"), "bin")
        val preferredName = if (os.contains("win")) "javaw.exe" else "java"
        val preferred = File(binDir, preferredName)
        val javaPath = if (preferred.exists()) preferred.absolutePath else File(binDir, "java").absolutePath

        val classpath = System.getProperty("java.class.path").orEmpty()
        val mainClass = System.getProperty("sun.java.command").orEmpty()
            .substringBefore(' ')
            .ifBlank { MAIN_CLASS_FALLBACK }

        return listOf(javaPath, "-cp", classpath, mainClass)
    }
}
