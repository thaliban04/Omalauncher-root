package dev.omalauncher.droidspaces

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-level facade for the DroidSpaces container runtime.
 *
 * Combines root-access, kernel-feature, and installer checks into a single
 * entry point and exposes container lifecycle operations (start / stop /
 * exec).
 *
 * @param context  Android [Context] forwarded to [DroidSpacesInstaller].
 */
class DroidSpacesBackend(private val context: Context) {

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private val rootShell      = RootShell()
    private val kernelChecker  = KernelFeatureChecker()
    val installer              = DroidSpacesInstaller(context, rootShell)

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private val BINARY = DroidSpacesInstaller.BINARY_PATH

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    @Volatile private var running: Boolean = false

    // -------------------------------------------------------------------------
    // Availability checks
    // -------------------------------------------------------------------------

    /** Returns `true` if `su` is usable and the shell identity is uid=0. */
    fun isRootAvailable(): Boolean = rootShell.isRootAvailable()

    /** Returns `true` if all required kernel namespaces / features are present. */
    fun isKernelSupported(): Boolean = kernelChecker.isKernelSuitable()

    /** Returns `true` if the DroidSpaces binary has been deployed to the device. */
    fun isBinaryInstalled(): Boolean = installer.isBinaryInstalled()

    /**
     * Returns `true` only if root access, a suitable kernel, **and** the
     * DroidSpaces binary are all available.  Use this as a single gate before
     * attempting any container operation.
     */
    fun isFullyAvailable(): Boolean =
        isRootAvailable() && isKernelSupported() && isBinaryInstalled()

    /** Returns the full list of kernel features and their availability status. */
    fun getKernelFeatures(): List<KernelFeatureChecker.KernelFeature> =
        kernelChecker.checkAll()

    /** Returns only the required kernel features that are currently missing. */
    fun getMissingFeatures(): List<KernelFeatureChecker.KernelFeature> =
        kernelChecker.getMissingRequired()

    // -------------------------------------------------------------------------
    // Container lifecycle
    // -------------------------------------------------------------------------

    fun ensureContainerConfig(containerName: String = installer.getContainerName(), hostRuntimeDir: File) {
        val confPath = "${DroidSpacesInstaller.CONTAINERS_DIR}/$containerName/container.config"
        val confPathLower = "${DroidSpacesInstaller.CONTAINERS_DIR_LOWER}/$containerName/container.config"
        val binds = listOf(
            "${hostRuntimeDir.absolutePath}:/run/user/0",
            "${dev.omalauncher.LauncherData.widgetsDir(context).absolutePath}:/root/.config/omalauncher/widgets",
            "${dev.omalauncher.LauncherData.styleDir(context).absolutePath}:/root/.config/omalauncher/style",
            "${dev.omalauncher.LauncherData.homeDir(context).absolutePath}:/root/.config/omalauncher/home",
            "${dev.omalauncher.LauncherData.omarchyDir(context).absolutePath}:/root/.local/share/omarchy",
            "${dev.omalauncher.LauncherData.userThemesDir(context).absolutePath}:/root/.config/omarchy/themes",
            "${dev.omalauncher.LauncherData.userBackgroundsDir(context).absolutePath}:/root/.config/omarchy/backgrounds",
        ).joinToString(",")
        val bindLine = "bind_mounts=$binds"
        val script = """
            for conf in $confPath $confPathLower; do
                if [ -f "${'$'}conf" ]; then
                    if grep -q "bind_mounts=" "${'$'}conf"; then
                        sed -i "s|^bind_mounts=.*|$bindLine|" "${'$'}conf"
                    else
                        echo "$bindLine" >> "${'$'}conf"
                    fi
                fi
            done
        """.trimIndent()
        rootShell.execute(script)
    }

    /**
     * Starts the named container by streaming output from the `droidspaces
     * start` command.
     *
     * @param containerName  Logical name of the container (default: "linux").
     * @return [Result.success] when the process exits with code 0, or
     *         [Result.failure] on a non-zero exit or exception.
     */
    suspend fun startContainer(containerName: String = installer.getContainerName()): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                ensureContainerConfig(containerName, File(context.filesDir, "xdg"))
                // Check if already running and has wayland socket mounted
                val showResult = rootShell.execute("$BINARY show")
                val isRunning = showResult.success && showResult.stdout.contains(containerName)
                if (isRunning) {
                    val checkMount = rootShell.execute("$BINARY --name=$containerName run ls /run/user/0/wayland-0 2>/dev/null")
                    val checkWidgets = rootShell.execute("$BINARY --name=$containerName run ls /root/.config/omalauncher/widgets 2>/dev/null")
                    if (checkMount.success && checkMount.stdout.contains("wayland-0") && checkWidgets.success) {
                        android.util.Log.i("DroidSpaces", "Container $containerName is already running with Wayland and widgets mounted")
                        running = true
                        return@withContext Result.success(Unit)
                    }
                    android.util.Log.i("DroidSpaces", "Container $containerName missing Wayland socket or widgets, restarting with bind mounts")
                    rootShell.execute("$BINARY --name=$containerName stop")
                }

                val exitCode = rootShell.executeStream(
                    command = "$BINARY --name=$containerName start",
                    timeoutSeconds = 60L,
                ) { line ->
                    // Forward log lines to Logcat for debugging.
                    android.util.Log.d("DroidSpaces", "[start] $line")
                }
                if (exitCode == 0) {
                    running = true
                    Result.success(Unit)
                } else {
                    Result.failure(RuntimeException("Container start failed (exit $exitCode)"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Stops the named container.
     *
     * @param containerName  Logical name of the container.
     */
    suspend fun stopContainer(containerName: String = installer.getContainerName()): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val result = rootShell.execute("$BINARY --name=$containerName stop")
                running = false          // mark stopped regardless of exit code
                if (result.success) {
                    Result.success(Unit)
                } else {
                    Result.failure(RuntimeException("Container stop failed: ${result.stderr}"))
                }
            } catch (e: Exception) {
                running = false
                Result.failure(e)
            }
        }

    /**
     * Returns the last-known running state of the container.
     *
     * Note: this reflects what this instance observed; it does not query the
     * kernel, so it can be stale after a process death.
     */
    fun isRunning(): Boolean = running

    // -------------------------------------------------------------------------
    // Command execution
    // -------------------------------------------------------------------------

    /**
     * Runs [command] inside the named container via `droidspaces run` and
     * returns the captured stdout.
     *
     * @param containerName  Logical name of the container.
     * @param command        Shell command to run inside the container.
     * @return [Result.success] with stdout on exit 0, [Result.failure] otherwise.
     */
    suspend fun executeInContainer(
        containerName: String = installer.getContainerName(),
        command: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val envCmd = "export HOME=/root USER=root LANG=en_US.UTF-8 TERM=xterm-256color PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/root/.local/bin:/root/.local/share/mise/shims:\$PATH XDG_RUNTIME_DIR=/run/user/0 WAYLAND_DISPLAY=wayland-0 GDK_BACKEND=wayland QT_QPA_PLATFORM=wayland MOZ_ENABLE_WAYLAND=1 SDL_VIDEODRIVER=wayland TAR_OPTIONS=--no-same-permissions MISE_NPM_PACKAGE_MANAGER=npm DISABLE_AUTOUPDATER=1 OPENCODE_DISABLE_AUTOUPDATE=1 GROK_DISABLE_AUTOUPDATER=1 CLAUDE_CODE_HARBOR_KITE=0 BROWSER=xdg-open; $command"
            // Single-quote the inner command to protect it from the outer shell.
            val escaped = envCmd.replace("'", "'\\''")
            val result = rootShell.execute(
                "$BINARY --name=$containerName run sh -c '$escaped'"
            )
            if (result.success) {
                Result.success(result.stdout)
            } else {
                Result.failure(RuntimeException("run failed (exit ${result.exitCode}): ${result.stderr}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Wayland helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the host-side path to the Wayland compositor socket that
     * DroidSpaces exposes for the given container.
     */
    fun getWaylandSocketPath(containerName: String = installer.getContainerName()): String =
        "/mnt/Droidspaces/$containerName/run/user/0/wayland-0"

    /**
     * Binds OmaLauncher's runtime directory (containing wayland-0 socket)
     * into the DroidSpaces container's /run/user/0 directory.
     */
    suspend fun mountWaylandSocket(
        hostRuntimeDir: File,
        containerName: String = installer.getContainerName(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mountTarget = "/mnt/Droidspaces/$containerName/run/user/0"
            val cmd = "mkdir -p $mountTarget && chmod 777 $mountTarget && mount --bind ${hostRuntimeDir.absolutePath} $mountTarget && chmod 777 $mountTarget/wayland-0 2>/dev/null || true"
            val res = rootShell.execute(cmd)
            val rootDir = "/mnt/Droidspaces/$containerName/root"
            val permCmd = "chmod 777 $rootDir && mkdir -p $rootDir/.config $rootDir/.local/bin $rootDir/.local/share/mise/shims $rootDir/.local/share/applications && chmod -R 777 $rootDir/.config $rootDir/.local 2>/dev/null || true"
            rootShell.execute(permCmd)
            val profileCmd = """
                echo 'export HOME=/root
                export USER=root
                export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/root/.local/bin:/root/.local/share/mise/shims:${'$'}PATH
                export XDG_RUNTIME_DIR=/run/user/0
                export WAYLAND_DISPLAY=wayland-0
                export GDK_BACKEND=wayland
                export QT_QPA_PLATFORM=wayland
                export MOZ_ENABLE_WAYLAND=1
                export SDL_VIDEODRIVER=wayland
                export TAR_OPTIONS=--no-same-permissions
                export MISE_NPM_PACKAGE_MANAGER=npm
                export DISABLE_AUTOUPDATER=1
                export OPENCODE_DISABLE_AUTOUPDATE=1
                export GROK_DISABLE_AUTOUPDATER=1
                export CLAUDE_CODE_HARBOR_KITE=0
                export BROWSER=xdg-open' > /mnt/Droidspaces/$containerName/etc/profile.d/99-omalauncher.sh && chmod 755 /mnt/Droidspaces/$containerName/etc/profile.d/99-omalauncher.sh
                grep -q "mise/shims" /mnt/Droidspaces/$containerName/etc/bash.bashrc 2>/dev/null || echo 'export PATH=/usr/local/sbin:/usr/local/bin:/usr/bin:/usr/sbin:/root/.local/bin:/root/.local/share/mise/shims:${'$'}PATH' >> /mnt/Droidspaces/$containerName/etc/bash.bashrc
                mkdir -p /mnt/Droidspaces/$containerName/usr/local/bin
                echo '#!/bin/sh
timeout 2 sh -c '\''printf "%s\n" "${'$'}1" > /run/user/0/open.fifo'\'' sh "${'$'}1"' > /mnt/Droidspaces/$containerName/usr/local/bin/xdg-open
                echo '#!/bin/sh
exec xdg-open "${'$'}@"' > /mnt/Droidspaces/$containerName/usr/local/bin/x-www-browser
                chmod 755 /mnt/Droidspaces/$containerName/usr/local/bin/xdg-open /mnt/Droidspaces/$containerName/usr/local/bin/x-www-browser
            """.trimIndent()
            rootShell.execute(profileCmd)
            if (res.success) Result.success(Unit) else Result.failure(RuntimeException("mount failed: ${res.stderr}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
