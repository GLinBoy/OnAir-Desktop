package com.glinboy.onair

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level

/**
 * Phase 4 real [MediaMonitor] for the desktop target.
 *
 * It delegates to a per-OS [PlatformMediaDetector], consuming push notifications when the platform
 * provides them (Linux/PulseAudio currently) and always running a safety-net poll at the
 * [ConfigurableMediaMonitor] interval. That interval is wired to the Settings window's
 * "Polling interval (ms)" field via [setPollingInterval], and doubles as the sole cadence on
 * platforms without event support.
 *
 * Robustness: every probe is wrapped so an unavailable API, a denied permission, or a transient
 * failure logs a warning (deduplicated per failure signature) and leaves the corresponding flow at
 * `false` — the app never crashes because detection failed.
 *
 * Known limitation: "in use" means an application currently holds the capture device open. Some
 * applications — most notably browsers in a call — keep the device open while muted and simply
 * feed silence, so there is no OS-level signal to distinguish muted from active and the flow stays
 * `true` until the application actually releases the device. This was confirmed on Linux/PulseAudio
 * (a disabled WebRTC audio track leaves `Corked: no, Mute: no`) and mirrors the OS privacy
 * indicators on macOS and Windows, which also stay lit while a call is muted.
 */
internal class NativeMediaMonitor(
    private val detector: PlatformMediaDetector = createPlatformMediaDetector(),
    initialPollingIntervalMs: Long = ConfigurableMediaMonitor.DEFAULT_POLLING_INTERVAL_MS,
) : ConfigurableMediaMonitor {

    private val _isMicInUse = MutableStateFlow(false)
    override val isMicInUse: StateFlow<Boolean> = _isMicInUse.asStateFlow()

    private val _isCamInUse = MutableStateFlow(false)
    override val isCamInUse: StateFlow<Boolean> = _isCamInUse.asStateFlow()

    private val pollingIntervalMs = AtomicLong(clampInterval(initialPollingIntervalMs))

    /** Serialises probes so an event callback and the polling loop cannot read hardware at once. */
    private val probeMutex = Mutex()

    /** Last logged failure signature per metric, so repeats are logged at FINE instead of WARNING. */
    private val lastFailure = ConcurrentHashMap<String, String>()

    @Volatile
    private var scope: CoroutineScope? = null

    override fun setPollingInterval(intervalMs: Long) {
        pollingIntervalMs.set(clampInterval(intervalMs))
    }

    override fun startMonitoring() {
        if (scope != null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        this.scope = scope

        DETECTION_LOGGER.info(
            "MediaMonitor starting: detector=${detector.platformName}, poll=${pollingIntervalMs.get()}ms",
        )

        // Prefer OS push events where offered; failures here degrade to polling and are logged.
        try {
            detector.startEventListener { scope.launch { refresh() } }
        } catch (t: Throwable) {
            DETECTION_LOGGER.log(
                Level.WARNING,
                "Event subscription unavailable on ${detector.platformName}; falling back to polling only.",
                t,
            )
        }

        scope.launch {
            while (isActive) {
                refresh()
                delay(pollingIntervalMs.get())
            }
        }
    }

    override fun stopMonitoring() {
        scope?.cancel()
        scope = null
        try {
            detector.close()
        } catch (t: Throwable) {
            DETECTION_LOGGER.log(Level.WARNING, "Error while closing ${detector.platformName} detector.", t)
        }
        setMic(false)
        setCam(false)
        DETECTION_LOGGER.info("MediaMonitor stopped.")
    }

    private suspend fun refresh() {
        probeMutex.withLock {
            setMic(probe("mic") { detector.isMicInUse() })
            setCam(probe("cam") { detector.isCamInUse() })
        }
    }

    private fun setMic(value: Boolean) {
        if (_isMicInUse.value != value) DETECTION_LOGGER.info("mic in use -> $value")
        _isMicInUse.value = value
    }

    private fun setCam(value: Boolean) {
        if (_isCamInUse.value != value) DETECTION_LOGGER.info("cam in use -> $value")
        _isCamInUse.value = value
    }

    private inline fun probe(metric: String, block: () -> Boolean): Boolean =
        try {
            block()
        } catch (t: Throwable) {
            logFailure(metric, t)
            false
        }

    private fun logFailure(metric: String, t: Throwable) {
        val signature = t::class.java.name + ": " + (t.message ?: "no message")
        val previous = lastFailure.put(metric, signature)
        if (previous == signature) {
            DETECTION_LOGGER.log(Level.FINE, "$metric detection still failing on ${detector.platformName}.", t)
        } else {
            DETECTION_LOGGER.log(
                Level.WARNING,
                "$metric detection failed on ${detector.platformName}; reporting false until it recovers.",
                t,
            )
        }
    }

    private fun clampInterval(intervalMs: Long): Long =
        intervalMs.coerceAtLeast(ConfigurableMediaMonitor.MIN_POLLING_INTERVAL_MS)
}
