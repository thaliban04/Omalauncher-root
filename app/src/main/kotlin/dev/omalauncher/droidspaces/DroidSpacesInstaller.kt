package dev.omalauncher.droidspaces

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles the installation and initial configuration of the DroidSpaces
 * binary and container directories on the device.
 *
 * All operations that touch /data/local/Droidspaces require root; they are
 * dispatched onto [Dispatchers.IO] by the suspend functions so callers may
 * invoke them from any coroutine context.
 *
 * @param context    Android Context used to access assets and cache directories.
 * @param rootShell  [RootShell] instance used to run privileged commands.
 */
class DroidSpacesInstaller(
    private val context: Context,
    private val rootShell: RootShell,
) {

    companion object {
        const val DROIDSPACES_DIR  = "/data/local/Droidspaces"
        const val BIN_DIR          = "$DROIDSPACES_DIR/bin"
        const val CONTAINERS_DIR   = "$DROIDSPACES_DIR/Containers"
        const val CONTAINERS_DIR_LOWER = "$DROIDSPACES_DIR/containers"
        const val BINARY_PATH      = "$BIN_DIR/droidspaces"
        const val BUSYBOX_PATH     = "$BIN_DIR/busybox"
    }

    /** Returns the name of the container to use (prefers "launcher", or first found). */
    fun getContainerName(): String {
        val checkLauncher = rootShell.execute("ls -d $CONTAINERS_DIR/launcher $CONTAINERS_DIR_LOWER/launcher 2>/dev/null")
        if (checkLauncher.success && checkLauncher.stdout.trim().isNotEmpty()) {
            return "launcher"
        }
        val checkAll = rootShell.execute("ls $CONTAINERS_DIR 2>/dev/null || ls $CONTAINERS_DIR_LOWER 2>/dev/null")
        if (checkAll.success) {
            val first = checkAll.stdout.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            if (first != null) return first
        }
        return "launcher"
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns `true` if the DroidSpaces binary is present at [BINARY_PATH].
     *
     * Because /data/local/Droidspaces may only be readable by root, the check
     * is performed via `su`.
     */
    fun isBinaryInstalled(): Boolean {
        val result = rootShell.execute("ls $BINARY_PATH")
        return result.success
    }

    /**
     * Copies the `droidspaces-arm64` asset into place at [BINARY_PATH] and
     * makes it executable.
     *
     * Steps:
     * 1. Create destination directories with root.
     * 2. Extract the asset to a world-readable temp file in [Context.getCacheDir].
     * 3. Copy the temp file to [BINARY_PATH] via root and set permissions.
     * 4. Delete the temp file.
     *
     * @return [Result.success] on success, [Result.failure] with an exception
     *         describing what went wrong otherwise.
     */
    suspend fun deployBinary(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Create target directories.
            val mkdirResult = rootShell.execute("mkdir -p $BIN_DIR")
            if (!mkdirResult.success) {
                return@withContext Result.failure(
                    RuntimeException("Failed to create $BIN_DIR: ${mkdirResult.stderr}")
                )
            }

            // 2. Extract asset to cache.
            val tempFile = File(context.cacheDir, "droidspaces-arm64.tmp")
            context.assets.open("droidspaces-arm64").use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            // Make the temp file readable by root's su process.
            tempFile.setReadable(true, false)

            // 3. Copy to final location via root and set executable bit.
            val copyCmd = "cp ${tempFile.absolutePath} $BINARY_PATH && chmod 755 $BINARY_PATH"
            val copyResult = rootShell.execute(copyCmd)

            // 4. Clean up temp file regardless of outcome.
            tempFile.delete()

            if (!copyResult.success) {
                return@withContext Result.failure(
                    RuntimeException("Failed to deploy binary: ${copyResult.stderr}")
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs the DroidSpaces setup command for the given [distro], pointing it
     * at [rootfsPath] as the base filesystem.
     *
     * @param distro      Distribution label (e.g. "arch", "debian").
     * @param rootfsPath  Path to the extracted rootfs tarball or directory.
     */
    suspend fun setupContainer(distro: String, rootfsPath: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val result = rootShell.execute(
                    "$BINARY_PATH setup --distro=$distro --rootfs=$rootfsPath"
                )
                if (result.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(RuntimeException("Container setup failed: ${result.stderr}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Returns `true` if the binary exists **and** at least one container
     * directory is present under [CONTAINERS_DIR] or [CONTAINERS_DIR_LOWER].
     */
    fun isInstalled(): Boolean {
        if (!isBinaryInstalled()) return false
        val check = rootShell.execute("ls $CONTAINERS_DIR 2>/dev/null || ls $CONTAINERS_DIR_LOWER 2>/dev/null")
        return check.success && check.stdout.trim().isNotEmpty()
    }
}
