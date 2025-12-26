package com.taqin.droid2run.termux

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manages Termux bootstrap installation and package management
 */
class TermuxBootstrap(private val context: Context) {

    companion object {
        private const val TAG = "TermuxBootstrap"

        // Termux bootstrap URLs for different architectures
        private const val BOOTSTRAP_BASE = "https://github.com/niclaslindstedt/termux-bootstrap/releases/download/v0.118.0"

        private val BOOTSTRAP_URLS = mapOf(
            "aarch64" to "$BOOTSTRAP_BASE/bootstrap-aarch64.zip",
            "arm" to "$BOOTSTRAP_BASE/bootstrap-arm.zip",
            "x86_64" to "$BOOTSTRAP_BASE/bootstrap-x86_64.zip",
            "i686" to "$BOOTSTRAP_BASE/bootstrap-i686.zip"
        )

        // Fallback to official Termux bootstrap
        private const val TERMUX_BOOTSTRAP_AARCH64 = "https://github.com/niclaslindstedt/termux-bootstrap/releases/download/v0.118.0/bootstrap-aarch64.zip"
    }

    val termuxDir = File(context.filesDir, "termux")
    val usrDir = File(termuxDir, "usr")
    val binDir = File(usrDir, "bin")
    val libDir = File(usrDir, "lib")
    val etcDir = File(usrDir, "etc")
    val tmpDir = File(termuxDir, "tmp")
    val homeDir = File(termuxDir, "home")

    val isInstalled: Boolean
        get() = File(binDir, "sh").exists() && File(binDir, "pkg").exists()

    val isQemuInstalled: Boolean
        get() = File(binDir, "qemu-system-x86_64").exists()

    init {
        termuxDir.mkdirs()
        usrDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        etcDir.mkdirs()
        tmpDir.mkdirs()
        homeDir.mkdirs()
    }

    private fun getArchitecture(): String {
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            arch.contains("arm64") || arch.contains("aarch64") -> "aarch64"
            arch.contains("armeabi") -> "arm"
            arch.contains("x86_64") -> "x86_64"
            arch.contains("x86") -> "i686"
            else -> "aarch64"
        }
    }

    suspend fun installBootstrap(
        onProgress: (Int, String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (isInstalled) {
                onProgress(100, "Termux already installed")
                return@withContext Result.success(Unit)
            }

            val arch = getArchitecture()
            val bootstrapUrl = BOOTSTRAP_URLS[arch] ?: TERMUX_BOOTSTRAP_AARCH64

            onProgress(5, "Downloading Termux bootstrap ($arch)...")
            Log.d(TAG, "Downloading bootstrap from: $bootstrapUrl")

            val zipFile = File(context.cacheDir, "bootstrap.zip")

            // Download bootstrap
            val downloaded = downloadFile(bootstrapUrl, zipFile) { progress ->
                onProgress(5 + (progress * 60 / 100), "Downloading... $progress%")
            }

            if (!downloaded) {
                return@withContext Result.failure(Exception("Failed to download bootstrap"))
            }

            onProgress(70, "Extracting bootstrap...")
            extractBootstrap(zipFile)

            onProgress(85, "Setting up environment...")
            setupEnvironment()

            onProgress(95, "Setting permissions...")
            setPermissions()

            zipFile.delete()

            if (isInstalled) {
                onProgress(100, "Termux installed successfully!")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Bootstrap extraction failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bootstrap installation failed", e)
            Result.failure(e)
        }
    }

    suspend fun installPackage(
        packageName: String,
        onProgress: (Int, String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!isInstalled) {
                return@withContext Result.failure(Exception("Termux not installed"))
            }

            onProgress(10, "Installing $packageName...")

            // Run pkg install command
            val result = runTermuxCommand("pkg install -y $packageName")

            if (result.first == 0) {
                onProgress(100, "$packageName installed!")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Installation failed: ${result.second}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Package installation failed", e)
            Result.failure(e)
        }
    }

    suspend fun installQemu(
        onProgress: (Int, String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // First ensure bootstrap is installed
            if (!isInstalled) {
                onProgress(0, "Installing Termux first...")
                val bootstrapResult = installBootstrap { progress, status ->
                    onProgress(progress / 2, status)
                }
                if (bootstrapResult.isFailure) {
                    return@withContext bootstrapResult
                }
            }

            onProgress(50, "Updating package lists...")
            runTermuxCommand("pkg update -y")

            onProgress(60, "Installing QEMU...")
            val result = runTermuxCommand("pkg install -y qemu-system-x86-64-headless qemu-utils")

            if (result.first == 0 || isQemuInstalled) {
                onProgress(100, "QEMU installed successfully!")
                Result.success(Unit)
            } else {
                // Try alternative package name
                onProgress(70, "Trying alternative package...")
                val altResult = runTermuxCommand("pkg install -y qemu-system-x86_64-headless")

                if (altResult.first == 0 || isQemuInstalled) {
                    onProgress(100, "QEMU installed!")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("QEMU installation failed: ${result.second}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "QEMU installation failed", e)
            Result.failure(e)
        }
    }

    private fun downloadFile(urlString: String, outputFile: File, onProgress: (Int) -> Unit): Boolean {
        var connection: HttpURLConnection? = null
        try {
            var currentUrl = urlString
            var redirects = 0

            while (redirects < 5) {
                connection = URL(currentUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 180000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "AndroidInAndroid/1.0")
                connection.connect()

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> break
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307, 308 -> {
                        currentUrl = connection.getHeaderField("Location") ?: return false
                        connection.disconnect()
                        redirects++
                    }
                    else -> {
                        Log.e(TAG, "HTTP ${connection.responseCode} for $urlString")
                        return false
                    }
                }
            }

            val fileLength = connection?.contentLength ?: -1
            var downloaded = 0

            connection?.inputStream?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        if (fileLength > 0) {
                            onProgress(downloaded * 100 / fileLength)
                        }
                    }
                }
            }

            Log.d(TAG, "Downloaded: ${outputFile.length()} bytes")
            return outputFile.exists() && outputFile.length() > 1000
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            return false
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractBootstrap(zipFile: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val destFile = File(termuxDir, entry.name)

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Handle symlinks file if present
        val symlinkFile = File(termuxDir, "SYMLINKS.txt")
        if (symlinkFile.exists()) {
            createSymlinks(symlinkFile)
        }
    }

    private fun createSymlinks(symlinkFile: File) {
        try {
            symlinkFile.readLines().forEach { line ->
                val parts = line.split("←")
                if (parts.size == 2) {
                    val target = parts[0].trim()
                    val linkPath = parts[1].trim()

                    val linkFile = File(termuxDir, linkPath)
                    val targetFile = File(termuxDir, target)

                    // Create symlink or copy
                    try {
                        linkFile.parentFile?.mkdirs()
                        if (targetFile.exists()) {
                            targetFile.copyTo(linkFile, overwrite = true)
                            if (targetFile.canExecute()) {
                                linkFile.setExecutable(true)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create symlink: $linkPath -> $target")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process symlinks", e)
        }
    }

    private fun setupEnvironment() {
        // Create essential config files
        val profile = File(etcDir, "profile")
        profile.writeText("""
            export PREFIX="${usrDir.absolutePath}"
            export HOME="${homeDir.absolutePath}"
            export PATH="${binDir.absolutePath}:${'$'}PATH"
            export LD_LIBRARY_PATH="${libDir.absolutePath}"
            export TMPDIR="${tmpDir.absolutePath}"
            export TERM=xterm-256color
            export LANG=en_US.UTF-8
        """.trimIndent())

        // Create apt sources if not exists
        val sourcesDir = File(etcDir, "apt/sources.list.d")
        sourcesDir.mkdirs()

        val sourcesFile = File(sourcesDir, "termux.list")
        if (!sourcesFile.exists()) {
            sourcesFile.writeText("""
                deb https://packages.termux.dev/apt/termux-main stable main
            """.trimIndent())
        }
    }

    private fun setPermissions() {
        // Set executable permission for all binaries
        binDir.listFiles()?.forEach { file ->
            file.setExecutable(true, false)
            file.setReadable(true, false)
        }

        // Set library permissions
        libDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                file.setReadable(true, false)
                if (file.extension == "so" || file.name.contains(".so.")) {
                    file.setExecutable(true, false)
                }
            }
        }
    }

    fun runTermuxCommand(command: String): Pair<Int, String> {
        return try {
            val shell = File(binDir, "sh")
            if (!shell.exists()) {
                return Pair(-1, "Shell not found")
            }

            val env = getEnvironment()
            val pb = ProcessBuilder(shell.absolutePath, "-c", command)
            pb.environment().putAll(env)
            pb.directory(homeDir)
            pb.redirectErrorStream(true)

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            Log.d(TAG, "Command: $command -> Exit: $exitCode")
            if (exitCode != 0) {
                Log.w(TAG, "Output: $output")
            }

            Pair(exitCode, output)
        } catch (e: Exception) {
            Log.e(TAG, "Command failed: ${e.message}")
            Pair(-1, e.message ?: "Unknown error")
        }
    }

    fun getEnvironment(): Map<String, String> = mapOf(
        "PREFIX" to usrDir.absolutePath,
        "HOME" to homeDir.absolutePath,
        "PATH" to "${binDir.absolutePath}:/system/bin",
        "LD_LIBRARY_PATH" to "${libDir.absolutePath}:/system/lib64",
        "TMPDIR" to tmpDir.absolutePath,
        "TERM" to "xterm-256color",
        "LANG" to "en_US.UTF-8",
        "ANDROID_DATA" to "/data",
        "ANDROID_ROOT" to "/system"
    )

    fun getShellPath(): String = File(binDir, "sh").absolutePath

    fun getQemuPath(): String = File(binDir, "qemu-system-x86_64").absolutePath
}
