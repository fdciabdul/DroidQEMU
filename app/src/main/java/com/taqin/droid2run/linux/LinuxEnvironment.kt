package com.taqin.droid2run.linux

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Manages Linux environment setup with proot
 */
class LinuxEnvironment(private val context: Context) {

    companion object {
        private const val TAG = "LinuxEnvironment"
        private const val ALPINE_VERSION = "3.19"
        private const val ALPINE_ARCH = "aarch64"
        private const val ROOTFS_URL = "https://dl-cdn.alpinelinux.org/alpine/v$ALPINE_VERSION/releases/$ALPINE_ARCH/alpine-minirootfs-$ALPINE_VERSION.0-$ALPINE_ARCH.tar.gz"
    }

    private val baseDir = File(context.filesDir, "linux")
    val rootfsDir = File(baseDir, "rootfs")
    private val downloadDir = File(baseDir, "downloads")
    private val binDir = File(baseDir, "bin")

    val isInstalled: Boolean
        get() = rootfsDir.exists() && File(rootfsDir, "bin/sh").exists()

    init {
        baseDir.mkdirs()
        downloadDir.mkdirs()
        binDir.mkdirs()
    }

    suspend fun downloadAndExtractRootfs(
        onProgress: (Int, String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress(0, "Preparing...")

            if (isInstalled) {
                onProgress(100, "Already installed")
                return@withContext Result.success(Unit)
            }

            val tarGzFile = File(downloadDir, "alpine-rootfs.tar.gz")

            // Download
            onProgress(10, "Downloading Alpine Linux...")
            downloadFile(ROOTFS_URL, tarGzFile) { progress ->
                onProgress(10 + (progress * 0.5).toInt(), "Downloading... $progress%")
            }

            // Extract
            onProgress(60, "Extracting rootfs...")
            extractTarGz(tarGzFile, rootfsDir)

            // Setup
            onProgress(80, "Setting up environment...")
            setupEnvironment()

            // Cleanup
            tarGzFile.delete()

            onProgress(100, "Installation complete!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup Linux environment", e)
            Result.failure(e)
        }
    }

    private fun downloadFile(urlString: String, outputFile: File, onProgress: (Int) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection()
        connection.connect()

        val fileLength = connection.contentLength
        var downloaded = 0

        url.openStream().use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (fileLength > 0) {
                        onProgress((downloaded * 100 / fileLength))
                    }
                }
            }
        }
    }

    private fun extractTarGz(tarGzFile: File, destDir: File) {
        destDir.mkdirs()

        // Use Android's tar via ProcessBuilder
        val process = ProcessBuilder()
            .command("tar", "-xzf", tarGzFile.absolutePath, "-C", destDir.absolutePath)
            .redirectErrorStream(true)
            .start()

        process.waitFor()

        if (process.exitValue() != 0) {
            // Fallback: manual extraction
            GZIPInputStream(tarGzFile.inputStream()).use { gzis ->
                extractTar(gzis, destDir)
            }
        }
    }

    private fun extractTar(input: java.io.InputStream, destDir: File) {
        // Simple tar extraction - in production use Apache Commons Compress
        val process = ProcessBuilder()
            .command("tar", "-xf", "-", "-C", destDir.absolutePath)
            .redirectErrorStream(true)
            .start()

        process.outputStream.use { output ->
            input.copyTo(output)
        }
        process.waitFor()
    }

    private fun setupEnvironment() {
        // Create necessary directories
        File(rootfsDir, "tmp").mkdirs()
        File(rootfsDir, "proc").mkdirs()
        File(rootfsDir, "sys").mkdirs()
        File(rootfsDir, "dev").mkdirs()
        File(rootfsDir, "root").mkdirs()
        File(rootfsDir, "home").mkdirs()

        // Create resolv.conf for DNS
        File(rootfsDir, "etc/resolv.conf").writeText("""
            nameserver 8.8.8.8
            nameserver 8.8.4.4
        """.trimIndent())

        // Create hosts file
        File(rootfsDir, "etc/hosts").apply {
            if (!exists()) {
                writeText("""
                    127.0.0.1 localhost
                    ::1 localhost
                """.trimIndent())
            }
        }
    }

    fun getProotCommand(command: String = "/bin/sh"): Array<String> {
        val prootPath = File(binDir, "proot").absolutePath

        // If proot binary exists, use it
        return if (File(prootPath).exists()) {
            arrayOf(
                prootPath,
                "-r", rootfsDir.absolutePath,
                "-0",  // Fake root
                "-w", "/root",
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "${context.filesDir.absolutePath}:/storage",
                command
            )
        } else {
            // Fallback: run directly in rootfs with chroot-like behavior
            arrayOf("/system/bin/sh", "-c", "cd ${rootfsDir.absolutePath} && $command")
        }
    }

    fun getEnvironmentVariables(): Array<String> {
        return arrayOf(
            "HOME=/root",
            "USER=root",
            "TERM=xterm-256color",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8",
            "SHELL=/bin/sh",
            "TMPDIR=/tmp"
        )
    }

    suspend fun deleteRootfs(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            rootfsDir.deleteRecursively()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
