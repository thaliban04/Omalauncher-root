package dev.omalauncher.droidspaces

import android.content.Context
import android.util.Log

private const val TAG = "BackendDetector"

/**
 * Detects the best available Linux backend for OmaLauncher.
 *
 * Priority order:
 * 1. **DroidSpaces** — if root is available + kernel supports namespaces + binary is installed
 * 2. **PRoot** — fallback, always available (no root required)
 *
 * Usage:
 * ```kotlin
 * val mode = BackendDetector.detect(context)
 * if (mode == LinuxBackendMode.DROIDSPACES) {
 *     // Use DroidSpacesBackend
 * } else {
 *     // Use original Proot-based flow
 * }
 * ```
 */
object BackendDetector {

    /** The active backend mode for this session. Determined once at app start. */
    @Volatile
    private var cachedMode: LinuxBackendMode? = null

    /**
     * Detects and returns the best available backend.
     * Result is cached after the first call; subsequent calls return instantly.
     *
     * @param context Android context (needed to access DroidSpacesBackend)
     * @param forceRefresh If true, re-runs detection even if cached
     */
    fun detect(context: Context, forceRefresh: Boolean = false): LinuxBackendMode {
        if (!forceRefresh) {
            cachedMode?.let { return it }
        }

        val mode = runCatching { doDetect(context) }.getOrElse { e ->
            Log.w(TAG, "Backend detection error, falling back to PRoot", e)
            LinuxBackendMode.PROOT
        }

        cachedMode = mode
        return mode
    }

    private fun doDetect(context: Context): LinuxBackendMode {
        val backend = DroidSpacesBackend(context)

        val hasRoot = backend.isRootAvailable()
        Log.d(TAG, "Root available: $hasRoot")
        if (!hasRoot) {
            Log.i(TAG, "No root → using PRoot mode")
            return LinuxBackendMode.PROOT
        }

        val kernelOk = backend.isKernelSupported()
        Log.d(TAG, "Kernel namespace support: $kernelOk")
        if (!kernelOk) {
            val missing = backend.getMissingFeatures()
            Log.i(TAG, "Kernel missing features: ${missing.map { it.name }} → using PRoot mode")
            return LinuxBackendMode.PROOT
        }

        val binaryInstalled = backend.isBinaryInstalled()
        Log.d(TAG, "DroidSpaces binary installed: $binaryInstalled")
        if (!binaryInstalled) {
            Log.i(TAG, "DroidSpaces binary not yet deployed → needs setup (will use DroidSpaces after install)")
            // Return DROIDSPACES_PENDING so the UI can trigger installation
            return LinuxBackendMode.DROIDSPACES_PENDING
        }

        Log.i(TAG, "✓ All DroidSpaces requirements met → using native container mode")
        return LinuxBackendMode.DROIDSPACES
    }

    /** Resets the cached mode (useful for testing or after installation). */
    fun reset() {
        cachedMode = null
    }
}

/**
 * The Linux backend mode in use for this session.
 */
enum class LinuxBackendMode {
    /** Original OmaLauncher PRoot mode (rootless, works on any device) */
    PROOT,

    /**
     * DroidSpaces is fully installed and ready — using native kernel namespace containers.
     * Root + capable kernel + DroidSpaces binary all present.
     */
    DROIDSPACES,

    /**
     * Root and kernel are capable of DroidSpaces, but the binary hasn't been deployed yet.
     * UI should prompt setup → after setup, mode becomes [DROIDSPACES].
     */
    DROIDSPACES_PENDING;

    val isDroidSpaces get() = this == DROIDSPACES || this == DROIDSPACES_PENDING
    val isFullyReady get() = this == DROIDSPACES || this == PROOT
    val displayName get() = when (this) {
        PROOT -> "PRoot (Rootless)"
        DROIDSPACES -> "DroidSpaces (Native)"
        DROIDSPACES_PENDING -> "DroidSpaces (Setup Required)"
    }
}

