package com.glinboy.onair

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * Windows implementation — **BEST-EFFORT, NOT VERIFIED** (this session runs on Linux).
 *
 * Reads the per-user CapabilityAccessManager consent store that Windows updates for every app that
 * uses the microphone or camera:
 *
 * `HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\<capability>`
 *
 * Each app has a subkey with `LastUsedTimeStart` / `LastUsedTimeStop` (FILETIME, REG_QWORD).
 * A capability is considered in use while `LastUsedTimeStop == 0` and `LastUsedTimeStart != 0`.
 * Store apps appear directly; classic desktop apps live under the `NonPackaged` subkey, whose
 * children are path fragments with `#` separators.
 *
 * This is deliberately a polling detector (the registry has no change-notification for these
 * values useful here). It requires no elevated privileges for the current user's own hive.
 * Because it is unverified, every read is defensive and the class is only instantiated on Windows.
 *
 * Planned alternative if this proves insufficient: JNA Core Audio (`IAudioSessionManager2` on
 * capture endpoints) plus camera handle inspection — both considerably more code and equally
 * untestable in this session.
 */
internal class WindowsMediaDetector : PlatformMediaDetector {

    override val platformName: String = "windows (best-effort, unverified)"

    private companion object {
        private const val CONSENT_STORE =
            "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore"
        private const val MIC_CAPABILITY = "microphone"
        private const val CAM_CAPABILITY = "webcam"
        private const val NON_PACKAGED = "NonPackaged"
        private const val LAST_USED_START = "LastUsedTimeStart"
        private const val LAST_USED_STOP = "LastUsedTimeStop"
    }

    override fun isMicInUse(): Boolean = isAnyActive("$CONSENT_STORE\\$MIC_CAPABILITY")

    override fun isCamInUse(): Boolean = isAnyActive("$CONSENT_STORE\\$CAM_CAPABILITY")

    private fun isAnyActive(basePath: String): Boolean {
        if (!Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, basePath)) {
            // Key absent on very old Windows builds; nothing is reported as active.
            return false
        }
        for (child in Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, basePath)) {
            val childPath = "$basePath\\$child"
            if (isCurrentlyActive(childPath)) return true
            if (child.equals(NON_PACKAGED, ignoreCase = true)) {
                val nonPackaged = Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, childPath)
                for (app in nonPackaged) {
                    if (isCurrentlyActive("$childPath\\$app")) return true
                }
            }
        }
        return false
    }

    private fun isCurrentlyActive(path: String): Boolean = try {
        val start = Advapi32Util.registryGetLongValue(WinReg.HKEY_CURRENT_USER, path, LAST_USED_START)
        val stop = Advapi32Util.registryGetLongValue(WinReg.HKEY_CURRENT_USER, path, LAST_USED_STOP)
        start > 0L && stop == 0L
    } catch (t: Throwable) {
        // Missing/invalid values are normal for apps that never used the capability.
        false
    }
}
