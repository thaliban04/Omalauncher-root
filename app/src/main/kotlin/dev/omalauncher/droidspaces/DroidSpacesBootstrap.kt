package dev.omalauncher.droidspaces

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Orchestrates the multi-step bootstrap flow needed to bring DroidSpaces up
 * from scratch on a fresh device.
 *
 * The current state is exposed as a [StateFlow] so the UI can observe
 * progress reactively.
 *
 * @param context  Android [Context] (Application or Activity).
 * @param backend  Fully constructed [DroidSpacesBackend] to delegate to.
 */
class DroidSpacesBootstrap(
    private val context: Context,
    private val backend: DroidSpacesBackend,
) {

    // -------------------------------------------------------------------------
    // State machine
    // -------------------------------------------------------------------------

    /** Represents every stage of the DroidSpaces setup pipeline. */
    sealed class DsSetupState {

        /** No setup has been attempted yet. */
        object Idle : DsSetupState()

        /** Currently verifying root access and kernel capabilities. */
        object CheckingRequirements : DsSetupState()

        /**
         * One or more prerequisites are absent.
         *
         * @param missingFeatures  Required kernel features that are unavailable.
         * @param noRoot           `true` when `su` is inaccessible.
         */
        data class MissingRequirements(
            val missingFeatures: List<KernelFeatureChecker.KernelFeature>,
            val noRoot: Boolean,
        ) : DsSetupState()

        /**
         * The DroidSpaces binary is being copied to /data/local/Droidspaces/bin.
         *
         * @param progress  Fraction in [0.0, 1.0]; 0 means "just started".
         */
        data class DeployingBinary(val progress: Float) : DsSetupState()

        /**
         * A distribution rootfs is being fetched from the network.
         *
         * @param distro    Distribution name being downloaded.
         * @param progress  Download fraction in [0.0, 1.0].
         */
        data class DownloadingRootfs(val distro: String, val progress: Float) : DsSetupState()

        /**
         * The distribution is being installed into its container directory.
         *
         * @param distro  Distribution name being installed.
         * @param step    Human-readable description of the current step.
         */
        data class InstallingDistro(val distro: String, val step: String) : DsSetupState()

        /** Setup completed successfully; the backend is ready to use. */
        object Ready : DsSetupState()

        /**
         * Setup failed and cannot proceed.
         *
         * @param reason  Human-readable error message.
         */
        data class Failed(val reason: String) : DsSetupState()
    }

    // -------------------------------------------------------------------------
    // Exposed state
    // -------------------------------------------------------------------------

    private val _state: MutableStateFlow<DsSetupState> =
        MutableStateFlow(DsSetupState.Idle)

    /** Observable stream of [DsSetupState] values. Collect this in the UI. */
    val state: StateFlow<DsSetupState> = _state.asStateFlow()

    // -------------------------------------------------------------------------
    // Setup entry point
    // -------------------------------------------------------------------------

    /**
     * Runs the full DroidSpaces setup pipeline for [distro].
     *
     * This is a suspend function that performs I/O; call it from a
     * [kotlinx.coroutines.CoroutineScope] backed by [Dispatchers.IO] or use
     * [withContext] internally (which this function already does for each
     * blocking step).
     *
     * @param distro  Distribution to set up (default: "arch").
     * @return [Result.success] when every step succeeds, [Result.failure] with
     *         an exception explaining the first failure otherwise.
     */
    suspend fun setup(distro: String = "arch"): Result<Unit> {
        // ------------------------------------------------------------------
        // Step 1 — Check requirements
        // ------------------------------------------------------------------
        _state.value = DsSetupState.CheckingRequirements

        val hasRoot = withContext(Dispatchers.IO) { backend.isRootAvailable() }
        if (!hasRoot) {
            _state.value = DsSetupState.MissingRequirements(
                missingFeatures = emptyList(),
                noRoot = true,
            )
            return Result.failure(RuntimeException("Root access is not available"))
        }

        val missingFeatures = withContext(Dispatchers.IO) { backend.getMissingFeatures() }
        if (missingFeatures.isNotEmpty()) {
            _state.value = DsSetupState.MissingRequirements(
                missingFeatures = missingFeatures,
                noRoot = false,
            )
            val names = missingFeatures.joinToString { it.name }
            return Result.failure(RuntimeException("Missing required kernel features: $names"))
        }

        // ------------------------------------------------------------------
        // Step 2 — Deploy the DroidSpaces binary
        // ------------------------------------------------------------------
        _state.value = DsSetupState.DeployingBinary(0f)

        val deployResult = backend.installer.deployBinary()
        if (deployResult.isFailure) {
            val reason = deployResult.exceptionOrNull()?.message ?: "Unknown error"
            _state.value = DsSetupState.Failed(reason)
            return Result.failure(deployResult.exceptionOrNull()!!)
        }

        // ------------------------------------------------------------------
        // Step 3 — Create container directory
        // ------------------------------------------------------------------
        _state.value = DsSetupState.InstallingDistro(distro, "Setting up container...")

        val containerDir = "${DroidSpacesInstaller.CONTAINERS_DIR}/$distro"
        val mkdirResult = withContext(Dispatchers.IO) {
            backend.installer.run {
                // Use the rootShell exposed indirectly via the backend.
                // We build a temporary RootShell here since the one inside
                // the installer is private; the cost is negligible.
                RootShell().execute("mkdir -p $containerDir")
            }
        }
        if (!mkdirResult.success) {
            val reason = "Failed to create container directory: ${mkdirResult.stderr}"
            _state.value = DsSetupState.Failed(reason)
            return Result.failure(RuntimeException(reason))
        }

        // ------------------------------------------------------------------
        // Step 4 — Configure Wayland bridge inside the container
        // ------------------------------------------------------------------
        _state.value = DsSetupState.InstallingDistro(distro, "Configuring Wayland bridge...")

        val waylandResult = configureWaylandBridge(distro, containerDir)
        if (waylandResult.isFailure) {
            val reason = waylandResult.exceptionOrNull()?.message ?: "Wayland bridge setup failed"
            _state.value = DsSetupState.Failed(reason)
            return Result.failure(waylandResult.exceptionOrNull()!!)
        }

        // ------------------------------------------------------------------
        // Done
        // ------------------------------------------------------------------
        _state.value = DsSetupState.Ready
        return Result.success(Unit)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Writes a small shell script to [containerDir]/setup-wayland.sh and
     * executes it via root so that the container's runtime directory is
     * prepared for Wayland socket forwarding.
     */
    private suspend fun configureWaylandBridge(
        distro: String,
        containerDir: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val runDir = "$containerDir/run/user/0"
            val scriptPath = "$containerDir/setup-wayland.sh"

            // Build the setup script content.
            val scriptContent = buildString {
                appendLine("#!/bin/sh")
                appendLine("# Auto-generated by DroidSpacesBootstrap — do not edit")
                appendLine("set -e")
                appendLine()
                appendLine("# Create Wayland runtime directory")
                appendLine("mkdir -p $runDir")
                appendLine("chmod 700 $runDir")
                appendLine()
                appendLine("# Create placeholder socket file so bind-mounts succeed")
                appendLine("touch $runDir/wayland-0")
                appendLine("chmod 660 $runDir/wayland-0")
                appendLine()
                appendLine("echo 'Wayland bridge configured for distro: $distro'")
            }

            val rootShell = RootShell()

            // Write the script via root using a here-document.
            val escaped = scriptContent.replace("'", "'\\''")
            val writeResult = rootShell.execute(
                "printf '%s' '$escaped' > $scriptPath && chmod 755 $scriptPath"
            )
            if (!writeResult.success) {
                return@withContext Result.failure(
                    RuntimeException("Failed to write Wayland setup script: ${writeResult.stderr}")
                )
            }

            // Execute it.
            val runResult = rootShell.execute("sh $scriptPath")
            if (!runResult.success) {
                return@withContext Result.failure(
                    RuntimeException("Wayland setup script failed: ${runResult.stderr}")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
