package com.glinboy.onair

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Shared logger for the native detection layer. Errors must never be swallowed silently: the
 * coordinator logs a WARNING the first time a given failure appears and downgrades repeats to
 * FINE so a permanently unavailable API does not flood the console.
 */
internal val DETECTION_LOGGER: Logger = Logger.getLogger("com.glinboy.onair.detection")

/**
 * A single OS probe behind [NativeMediaMonitor]. One instance handles both metrics so a platform
 * implementation can share whatever session/connection it needs.
 *
 * Contract:
 *  - [isMicInUse]/[isCamInUse] may throw when the underlying API is unavailable; the coordinator
 *    catches, logs and treats the metric as `false`. Throwing is preferred over silently returning
 *    a wrong value, because the coordinator's logging tells the user what is missing.
 *  - Implementations must be safe to call from a background thread.
 */
internal interface PlatformMediaDetector : AutoCloseable {
    /** Human-readable identifier included in log lines, e.g. "linux", "windows (best-effort, unverified)". */
    val platformName: String

    fun isMicInUse(): Boolean

    fun isCamInUse(): Boolean

    /**
     * Optional push-based hint. When the OS offers change notifications an implementation starts a
     * background watcher that calls [onChange] whenever mic/cam state may have changed, letting the
     * monitor react faster than the polling interval. Default is a no-op (poll-only).
     */
    fun startEventListener(onChange: () -> Unit) {}

    /** Releases any OS resources (child processes, notification registrations). Must not throw. */
    override fun close() {}
}

/**
 * Selects the detector for the current OS.
 *
 * Verification status by design (see each implementation's header):
 *  - Linux   — implemented and verified on the development machine (Manjaro, PipeWire + PulseAudio).
 *  - Windows — best-effort, written against the Core Audio / CapabilityAccessManager consent store
 *              APIs but NOT testable in this session; marked unverified.
 *  - macOS   — best-effort, written against Core Audio / CoreMediaIO via JNA but NOT testable in
 *              this session; marked unverified.
 */
internal fun createPlatformMediaDetector(): PlatformMediaDetector {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return try {
        when {
            os.contains("win") -> WindowsMediaDetector()
            os.contains("mac") -> MacOsMediaDetector()
            os.contains("nux") || os.contains("nix") || os.contains("aix") -> LinuxMediaDetector()
            else -> UnsupportedMediaDetector(os)
        }
    } catch (t: Throwable) {
        // e.g. a native library that fails to load; degrade gracefully instead of crashing at startup.
        DETECTION_LOGGER.log(Level.SEVERE, "Could not initialise native detector for os.name='$os'.", t)
        UnsupportedMediaDetector(os)
    }
}

/**
 * Fallback used on an unrecognised OS: reports nothing and logs once, then stays quiet. This keeps
 * the app alive at `false` rather than crashing on an exotic platform.
 */
internal class UnsupportedMediaDetector(private val os: String) : PlatformMediaDetector {
    override val platformName: String = "unsupported($os)"

    @Volatile
    private var warned = false

    private fun warnOnce(): Boolean {
        if (!warned) {
            warned = true
            DETECTION_LOGGER.warning("No native media detector for os.name='$os'; mic/cam will stay false.")
        }
        return false
    }

    override fun isMicInUse(): Boolean = warnOnce()

    override fun isCamInUse(): Boolean = warnOnce()
}
