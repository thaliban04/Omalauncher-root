package dev.omalauncher.droidspaces

import java.util.concurrent.TimeUnit

/**
 * Executes shell commands as root using `su`.
 *
 * All blocking I/O is left to the caller to dispatch on an appropriate thread
 * (e.g. Dispatchers.IO).  This class itself is stateless and thread-safe.
 */
class RootShell {

    /** Holds the captured output of a root command. */
    data class RootResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    ) {
        val success: Boolean get() = exitCode == 0
    }

    /**
     * Runs [command] as root and returns a [RootResult] with both stdout and
     * stderr captured separately.
     *
     * @param command  The shell command to run (passed verbatim to `su -c`).
     * @param timeoutSeconds  Maximum seconds to wait before the process is
     *   killed and an exit-code of -1 is returned.
     */
    fun execute(command: String, timeoutSeconds: Long = 30L): RootResult {
        val pb = ProcessBuilder("su", "-c", command).apply {
            redirectErrorStream(true)
        }
        val process = pb.start()

        val reader = java.util.concurrent.Executors.newSingleThreadExecutor()
        val stdoutFuture = reader.submit<String> {
            process.inputStream.bufferedReader().use { it.readText() }
        }

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            reader.shutdownNow()
            return RootResult(
                stdout = runCatching { stdoutFuture.get(500, TimeUnit.MILLISECONDS) }.getOrDefault(""),
                stderr = "Process timed out after ${timeoutSeconds}s",
                exitCode = -1,
            )
        }

        val out = runCatching { stdoutFuture.get(2, TimeUnit.SECONDS) }.getOrDefault("")
        reader.shutdown()
        return RootResult(
            stdout = out,
            stderr = "",
            exitCode = process.exitValue(),
        )
    }

    /**
     * Runs [command] as root and delivers each output line to [onLine] as it
     * arrives (stdout only).  Stderr is discarded.
     *
     * @return The process exit code, or -1 on timeout.
     */
    fun executeStream(
        command: String,
        timeoutSeconds: Long = 30L,
        onLine: (String) -> Unit,
    ): Int {
        val pb = ProcessBuilder("su", "-c", command).apply {
            redirectErrorStream(true)   // merge stderr into stdout for streaming
        }
        val process = pb.start()

        process.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().forEach { line -> onLine(line) }
        }

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return -1
        }
        return process.exitValue()
    }

    /**
     * Returns `true` if `su` is available and the shell identity is root
     * (uid=0).
     */
    fun isRootAvailable(): Boolean {
        return try {
            val result = execute("id", timeoutSeconds = 10L)
            result.success && result.stdout.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }
}
