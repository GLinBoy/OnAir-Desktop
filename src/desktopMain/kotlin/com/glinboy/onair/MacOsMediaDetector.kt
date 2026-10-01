package com.glinboy.onair

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference

/**
 * macOS implementation — **BEST-EFFORT, NOT VERIFIED** (this session runs on Linux).
 *
 * - Microphone: Core Audio `kAudioDevicePropertyDeviceIsRunningSomewhere` (`'gone'`) on the
 *   default input device — the public signal for "some process is capturing on this device".
 * - Camera: CoreMediaIO `kCMIODevicePropertyDeviceIsRunningSomewhere` (`'gone'`) on every device
 *   returned by `kCMIOHardwarePropertyDevices`. This is the same source AVCaptureDevice's private
 *   "in use by another application" flag is derived from.
 *
 * Both frameworks are reached through JNA, so no native helper/JNI build step is required. Because
 * this code cannot be executed in this session, it is written defensively and marked unverified;
 * any failure surfaces as a thrown error that the coordinator logs while keeping the flow `false`.
 * It also cannot detect permission-denied as distinct from "not in use" — both report `false`,
 * which is the required safe behaviour.
 */
internal class MacOsMediaDetector : PlatformMediaDetector {

    override val platformName: String = "macos (best-effort, unverified)"

    private companion object {
        // Core Audio
        private const val kAudioObjectSystemObject = 1
        private const val kAudioHardwarePropertyDefaultInputDevice = 0x64496E20 // 'dIn '
        private const val kAudioDevicePropertyDeviceIsRunningSomewhere = 0x676F6E65 // 'gone'

        // CoreMediaIO
        private const val kCMIOObjectSystemObject = 1
        private const val kCMIOHardwarePropertyDevices = 0x64657623 // 'dev#'
        private const val kCMIODevicePropertyDeviceIsRunningSomewhere = 0x676F6E65 // 'gone'

        // Shared property-address constants
        private const val kObjectPropertyScopeGlobal = 0x676C6F62 // 'glob'
        private const val kObjectPropertyElementMain = 0

        private const val NO_ERROR = 0
        private const val INT_SIZE_BYTES = 4
    }

    override fun isMicInUse(): Boolean {
        val deviceId = defaultInputDeviceId() ?: return false
        return isAudioDeviceRunning(deviceId)
    }

    override fun isCamInUse(): Boolean = cameraDeviceIds().any { isCameraDeviceRunning(it) }

    private fun defaultInputDeviceId(): Int? {
        val address = AudioObjectPropertyAddress(
            kAudioHardwarePropertyDefaultInputDevice,
            kObjectPropertyScopeGlobal,
            kObjectPropertyElementMain,
        )
        val size = IntByReference(INT_SIZE_BYTES)
        val out = Memory(INT_SIZE_BYTES.toLong())
        val status = CoreAudio.INSTANCE.AudioObjectGetPropertyData(
            kAudioObjectSystemObject, address, 0, null, size, out,
        )
        if (status != NO_ERROR) {
            throw IllegalStateException("Core Audio: reading default input device failed (OSStatus=$status)")
        }
        return out.getInt(0)
    }

    private fun isAudioDeviceRunning(deviceId: Int): Boolean {
        val address = AudioObjectPropertyAddress(
            kAudioDevicePropertyDeviceIsRunningSomewhere,
            kObjectPropertyScopeGlobal,
            kObjectPropertyElementMain,
        )
        val size = IntByReference(INT_SIZE_BYTES)
        val out = Memory(INT_SIZE_BYTES.toLong())
        val status = CoreAudio.INSTANCE.AudioObjectGetPropertyData(deviceId, address, 0, null, size, out)
        if (status != NO_ERROR) {
            throw IllegalStateException("Core Audio: reading device #$deviceId running state failed (OSStatus=$status)")
        }
        return out.getInt(0) != 0
    }

    private fun cameraDeviceIds(): List<Int> {
        val address = CmioObjectPropertyAddress(
            kCMIOHardwarePropertyDevices,
            kObjectPropertyScopeGlobal,
            kObjectPropertyElementMain,
        )
        val size = IntByReference(0)
        // First call with null data queries the required buffer size.
        CoreMediaIo.INSTANCE.CMIOObjectGetPropertyData(
            kCMIOObjectSystemObject, address, 0, null, size, IntByReference(0), null,
        )
        val byteCount = size.value
        if (byteCount <= 0) return emptyList()

        val out = Memory(byteCount.toLong())
        val used = IntByReference(0)
        val status = CoreMediaIo.INSTANCE.CMIOObjectGetPropertyData(
            kCMIOObjectSystemObject, address, 0, null, size, used, out,
        )
        if (status != NO_ERROR) {
            throw IllegalStateException("CoreMediaIO: enumerating devices failed (OSStatus=$status)")
        }
        val count = used.value / INT_SIZE_BYTES
        return (0 until count).map { index -> out.getInt((index * INT_SIZE_BYTES).toLong()) }
    }

    private fun isCameraDeviceRunning(deviceId: Int): Boolean {
        val address = CmioObjectPropertyAddress(
            kCMIODevicePropertyDeviceIsRunningSomewhere,
            kObjectPropertyScopeGlobal,
            kObjectPropertyElementMain,
        )
        val size = IntByReference(INT_SIZE_BYTES)
        val used = IntByReference(0)
        val out = Memory(INT_SIZE_BYTES.toLong())
        val status = CoreMediaIo.INSTANCE.CMIOObjectGetPropertyData(
            deviceId, address, 0, null, size, used, out,
        )
        if (status != NO_ERROR) {
            throw IllegalStateException("CoreMediaIO: reading device #$deviceId running state failed (OSStatus=$status)")
        }
        return out.getInt(0) != 0
    }
}

@Structure.FieldOrder("mSelector", "mScope", "mElement")
internal class AudioObjectPropertyAddress(
    @JvmField var mSelector: Int = 0,
    @JvmField var mScope: Int = 0,
    @JvmField var mElement: Int = 0,
) : Structure()

@Structure.FieldOrder("mSelector", "mScope", "mElement")
internal class CmioObjectPropertyAddress(
    @JvmField var mSelector: Int = 0,
    @JvmField var mScope: Int = 0,
    @JvmField var mElement: Int = 0,
) : Structure()

private interface CoreAudio : Library {
    fun AudioObjectGetPropertyData(
        inObjectID: Int,
        inAddress: AudioObjectPropertyAddress,
        inQualifierDataSize: Int,
        inQualifierData: Pointer?,
        ioDataSize: IntByReference,
        outData: Pointer?,
    ): Int

    companion object {
        val INSTANCE: CoreAudio = Native.load("CoreAudio", CoreAudio::class.java)
    }
}

private interface CoreMediaIo : Library {
    fun CMIOObjectGetPropertyData(
        inObjectID: Int,
        inAddress: CmioObjectPropertyAddress,
        inQualifierDataSize: Int,
        inQualifierData: Pointer?,
        outDataSize: IntByReference,
        outDataUsed: IntByReference,
        outData: Pointer?,
    ): Int

    companion object {
        val INSTANCE: CoreMediaIo = Native.load("CoreMediaIO", CoreMediaIo::class.java)
    }
}
