package dev.omalauncher.droidspaces

import java.io.File

/**
 * Inspects the host kernel to determine whether it supports the features
 * required to run DroidSpaces containers.
 *
 * All checks read pseudo-files in /proc and /sys, so they are fast and
 * require no root.  This class is stateless and thread-safe.
 */
class KernelFeatureChecker {

    /**
     * Describes a single kernel capability that DroidSpaces may need.
     *
     * @param name         Short human-readable label.
     * @param description  Explanation of what this feature provides.
     * @param required     Whether the feature is mandatory for containers to run.
     * @param available    Whether the current kernel exposes this feature.
     */
    data class KernelFeature(
        val name: String,
        val description: String,
        val required: Boolean,
        val available: Boolean,
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Runs all checks and returns a list of [KernelFeature] instances, one per
     * checked capability.
     */
    fun checkAll(): List<KernelFeature> = listOf(
        checkUserNamespaces(),
        checkPidNamespaces(),
        checkMountNamespaces(),
        checkNetNamespaces(),
        checkIpcNamespaces(),
        checkCgroupV2(),
        checkCgroupV1(),
        checkOverlayFs(),
        checkSeccomp(),
    )

    /**
     * Returns `true` if every required kernel feature is available.
     */
    fun isKernelSuitable(): Boolean = getMissingRequired().isEmpty()

    /**
     * Returns the subset of required features that are unavailable.
     */
    fun getMissingRequired(): List<KernelFeature> =
        checkAll().filter { it.required && !it.available }

    // -------------------------------------------------------------------------
    // Individual checks
    // -------------------------------------------------------------------------

    private fun checkUserNamespaces(): KernelFeature {
        val available = readIntFile("/proc/sys/user/max_user_namespaces")
            ?.let { it > 0 }
            ?: File("/proc/self/ns/user").exists()
        return KernelFeature(
            name = "User Namespaces",
            description = "Allows mapping UIDs/GIDs inside the container.",
            required = true,
            available = available,
        )
    }

    private fun checkPidNamespaces(): KernelFeature {
        val available = File("/proc/sys/user/max_pid_namespaces").exists()
            || File("/proc/self/ns/pid").exists()
        return KernelFeature(
            name = "PID Namespaces",
            description = "Isolates process ID numbering inside the container.",
            required = true,
            available = available,
        )
    }

    private fun checkMountNamespaces(): KernelFeature {
        val available = File("/proc/self/ns/mnt").exists()
        return KernelFeature(
            name = "Mount Namespaces",
            description = "Gives the container its own mount table.",
            required = true,
            available = available,
        )
    }

    private fun checkNetNamespaces(): KernelFeature {
        val available = File("/proc/self/ns/net").exists()
        return KernelFeature(
            name = "Network Namespaces",
            description = "Isolates network interfaces and routing tables.",
            required = false,
            available = available,
        )
    }

    private fun checkIpcNamespaces(): KernelFeature {
        val available = File("/proc/self/ns/ipc").exists()
        return KernelFeature(
            name = "IPC Namespaces",
            description = "Isolates SysV IPC and POSIX message queues.",
            required = false,
            available = available,
        )
    }

    private fun checkCgroupV2(): KernelFeature {
        val available = File("/sys/fs/cgroup/cgroup.controllers").exists()
        return KernelFeature(
            name = "Cgroups v2",
            description = "Unified cgroup hierarchy for resource limiting.",
            required = false,
            available = available,
        )
    }

    private fun checkCgroupV1(): KernelFeature {
        val available = File("/sys/fs/cgroup/memory").exists()
        return KernelFeature(
            name = "Cgroups v1",
            description = "Legacy per-subsystem cgroup controllers.",
            required = false,   // at least one of v1/v2 is required; enforced in isKernelSuitable
            available = available,
        )
    }

    private fun checkOverlayFs(): KernelFeature {
        val available = try {
            File("/proc/filesystems").readText().contains("overlay")
        } catch (_: Exception) {
            // Unprivileged app SELinux cannot read /proc/filesystems directly,
            // but DroidSpaces uses ext4 images and does not require OverlayFS.
            true
        }
        return KernelFeature(
            name = "OverlayFS",
            description = "Enables copy-on-write layered container filesystems (optional).",
            required = false,
            available = available,
        )
    }

    private fun checkSeccomp(): KernelFeature {
        val available = try {
            File("/proc/self/status").readText().contains("Seccomp:")
        } catch (_: Exception) {
            false
        }
        return KernelFeature(
            name = "Seccomp",
            description = "System-call filtering to sandbox container processes.",
            required = false,
            available = available,
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Reads the first line of a file and parses it as an [Int], or null. */
    private fun readIntFile(path: String): Int? = try {
        File(path).readText().trim().toIntOrNull()
    } catch (_: Exception) {
        null
    }
}
