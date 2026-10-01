package com.glinboy.onair

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Linux implementation — **VERIFIED on the development machine** (Manjaro, kernel 6.18,
 * PipeWire + PulseAudio with `pactl`, no V4L2 camera attached at test time; camera logic was
 * exercised against a synthetic open file descriptor).
 *
 * - Microphone: asks PulseAudio/PipeWire for its active `source-output`s (recording streams) and
 *   treats any non-corked stream as "mic in use". This works transparently for native PulseAudio
 *   and for `pipewire-pulse`, both of which expose the same `pactl` interface. It is event-driven:
 *   a `pactl subscribe` watcher notifies the monitor whenever a source-output appears/disappears.
 * - Camera: enumerates `/dev/video*` and scans open file descriptors in `/proc/<pid>/fd` for a
 *   process that currently holds one open. `pactl` has no camera analogue, so this metric is
 *   poll-driven.
 *
 * If `pactl` is missing or the audio server is unreachable the mic probe throws and the monitor
 * logs a warning and reports `false`; camera probing degrades the same way.
 *
 * Known limitation (verified): muting inside a browser call does NOT release or cork the stream —
 * a disabled WebRTC audio track keeps the `source-output` at `Corked: no, Mute: no` — so mute is
 * indistinguishable from active capture at this layer. Treating `Mute: yes` as not-in-use would
 * only help native apps, not browsers, so "in use" is kept to mean "an app holds the mic open".
 */
internal class LinuxMediaDetector : PlatformMediaDetector {

    override val platformName: String = "linux"

    private companion object {
        private const val PACTL = "pactl"
        private val VIDEO_DEVICE = Regex("""video\d+""")
        private const val COMMAND_TIMEOUT_MS = 2_000L
    }

    @Volatile
    private var subscribeProcess: Process? = null

    @Volatile
    private var subscribeThread: Thread? = null

    @Volatile
    private var closed = false

    /** Set once so per-process permission failures on `/proc/<pid>/fd` do not spam the log. */
    @Volatile
    private var warnedAboutProcPermissions = false

    override fun isMicInUse(): Boolean {
        val full = runCommand(PACTL, "list", "source-outputs")
        if (full != null) {
            return parseSourceOutputs(full)
        }
        // Older/short output has no Corked field; a non-empty list still means a capture stream.
        val short = runCommand(PACTL, "list", "short", "source-outputs")
            ?: throw IllegalStateException(
                "`$PACTL` is unavailable or the PulseAudio/PipeWire server is unreachable",
            )
        return short.lineSequence().any { it.isNotBlank() }
    }

    override fun isCamInUse(): Boolean {
        val devicePaths = videoDevicePaths()
        if (devicePaths.isEmpty()) return false
        return devicesHeldOpen(devicePaths, File("/proc"), ProcessHandle.current().pid())
    }

    /** Absolute paths of the V4L2 video devices currently present, e.g. `/dev/video0`. */
    internal fun videoDevicePaths(): List<String> =
        File("/dev").listFiles { file -> file.name.matches(VIDEO_DEVICE) }.orEmpty().map { it.absolutePath }

    /**
     * Returns true when any process other than [selfPid] holds one of [devicePaths] open, by
     * resolving symlinks under `<procRoot>/<pid>/fd/`. Split out from [isCamInUse] so the scan can
     * be exercised against a synthetic proc tree (no real camera was attached during verification).
     */
    internal fun devicesHeldOpen(devicePaths: Collection<String>, procRoot: File, selfPid: Long): Boolean {
        val pidDirs = procRoot.listFiles { file -> file.isDirectory && file.name.all(Char::isDigit) }.orEmpty()
        for (pidDir in pidDirs) {
            val pid = pidDir.name.toLongOrNull() ?: continue
            if (pid == selfPid) continue
            val fdDir = File(pidDir, "fd")
            val fds = try {
                fdDir.listFiles()
            } catch (t: Throwable) {
                if (!warnedAboutProcPermissions) {
                    warnedAboutProcPermissions = true
                    DETECTION_LOGGER.log(
                        Level.FINE,
                        "Cannot read some <proc>/<pid>/fd entries (permissions); camera detection is best-effort.",
                        t,
                    )
                }
                continue
            } ?: continue

            for (fd in fds) {
                val target = try {
                    Files.readSymbolicLink(fd.toPath()).toString()
                } catch (t: Throwable) {
                    continue
                }
                if (devicePaths.any { target.startsWith(it) }) return true
            }
        }
        return false
    }

    override fun startEventListener(onChange: () -> Unit) {
        if (closed) return
        // Throws if pactl is missing; the coordinator logs and continues with polling only.
        val process = ProcessBuilder(PACTL, "subscribe").redirectErrorStream(true).start()
        subscribeProcess = process
        val thread = Thread({
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        // e.g. "Event 'new' on source-output #3"; "on source" also covers default-source changes.
                        if (line.contains("source-output") || line.contains(" on source ")) {
                            onChange()
                        }
                    }
                }
            } catch (t: Throwable) {
                if (!closed) {
                    DETECTION_LOGGER.log(
                        Level.WARNING,
                        "`$PACTL subscribe` watcher stopped; continuing with polling only.",
                        t,
                    )
                }
            }
        }, "onair-pactl-subscribe").apply {
            isDaemon = true
            start()
        }
        subscribeThread = thread
    }

    override fun close() {
        closed = true
        subscribeProcess?.let { process ->
            process.destroy()
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
        subscribeProcess = null
        subscribeThread?.interrupt()
        subscribeThread = null
    }

    /**
     * Returns stdout, or `null` if the command is missing, times out, or exits non-zero. Returning
     * null for a non-zero exit lets the caller raise a descriptive error that the monitor logs,
     * rather than mistaking an error message for "no capture stream".
     */
    private fun runCommand(vararg command: String): String? = try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        when {
            !finished -> {
                process.destroyForcibly()
                null
            }
            process.exitValue() != 0 -> null
            else -> output
        }
    } catch (t: Throwable) {
        null
    }

    /**
     * `pactl list source-outputs` emits one block per recording stream. A stream that is paused but
     * still attached shows `Corked: yes`; only non-corked streams are actually capturing.
     */
    private fun parseSourceOutputs(output: String): Boolean {
        val blocks = output.split(Regex("""(?m)^Source Output #""")).drop(1)
        if (blocks.isEmpty()) return false
        return blocks.any { block -> !block.contains("Corked: yes") }
    }
}
