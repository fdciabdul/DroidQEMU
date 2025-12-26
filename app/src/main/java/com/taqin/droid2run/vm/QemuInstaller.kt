package com.taqin.droid2run.vm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and installs QEMU from Termux packages using pure Java extraction
 */
class QemuInstaller(private val context: Context) {

    companion object {
        private const val TAG = "QemuInstaller"

        // Termux package repository
        private const val TERMUX_REPO = "https://packages.termux.dev/apt/termux-main"

        // Package paths for QEMU and its dependencies
        // Note: Package names in Termux don't always match library names
        private val PACKAGES = mapOf(
            // Core QEMU packages
            "qemu-common" to "pool/main/q/qemu-common/",
            "qemu-system-x86-64-headless" to "pool/main/q/qemu-system-x86-64-headless/",
            "qemu-system-aarch64-headless" to "pool/main/q/qemu-system-aarch64-headless/",
            "qemu-utils" to "pool/main/q/qemu-utils/",
            // Required dependencies - correct Termux package names with lib prefix
            "libgnutls" to "pool/main/libg/libgnutls/",
            "libnettle" to "pool/main/libn/libnettle/",
            "libpixman" to "pool/main/libp/libpixman/",
            "libjpeg-turbo" to "pool/main/libj/libjpeg-turbo/",
            "libpng" to "pool/main/libp/libpng/",
            "libslirp" to "pool/main/libs/libslirp/",
            "glib" to "pool/main/g/glib/",
            "libgmp" to "pool/main/libg/libgmp/",
            "libidn2" to "pool/main/libi/libidn2/",
            "libunistring" to "pool/main/libu/libunistring/",
            "pcre2" to "pool/main/p/pcre2/",
            "libffi" to "pool/main/libf/libffi/",
            "zlib" to "pool/main/z/zlib/",
            "libtasn1" to "pool/main/libt/libtasn1/",
            "p11-kit" to "pool/main/p/p11-kit/",
            "brotli" to "pool/main/b/brotli/",
            "zstd" to "pool/main/z/zstd/",
            "liblzo" to "pool/main/libl/liblzo/",
            "libcap-ng" to "pool/main/libc/libcap-ng/",
            "dtc" to "pool/main/d/dtc/",       // provides libfdt.so
            "libelf" to "pool/main/libe/libelf/",
            "libdw" to "pool/main/libd/libdw/",
            "ncurses" to "pool/main/n/ncurses/",
            "libiconv" to "pool/main/libi/libiconv/",
            // Additional QEMU dependencies
            "alsa-lib" to "pool/main/a/alsa-lib/",
            "libbz2" to "pool/main/libb/libbz2/",
            "libcurl" to "pool/main/libc/libcurl/",
            "libnfs" to "pool/main/libn/libnfs/",
            "libspice-server" to "pool/main/libs/libspice-server/",
            "libssh" to "pool/main/libs/libssh/",
            "libusb" to "pool/main/libu/libusb/",
            "libusbredir" to "pool/main/libu/libusbredir/",
            "pulseaudio" to "pool/main/p/pulseaudio/",
            "libandroid-shmem" to "pool/main/liba/libandroid-shmem/",
            "libogg" to "pool/main/libo/libogg/",
            "libsndfile" to "pool/main/libs/libsndfile/",
            "libvorbis" to "pool/main/libv/libvorbis/",
            "libflac" to "pool/main/libf/libflac/",
            "opus" to "pool/main/o/opus/",
            "libx11" to "pool/main/libx/libx11/",
            "libxcb" to "pool/main/libx/libxcb/",
            "libxau" to "pool/main/libx/libxau/",
            "libxdmcp" to "pool/main/libx/libxdmcp/",
            "libnghttp2" to "pool/main/libn/libnghttp2/",
            "openssl" to "pool/main/o/openssl/",
            "ca-certificates" to "pool/main/c/ca-certificates/",
            "libnghttp3" to "pool/main/libn/libnghttp3/",
            "jack2" to "pool/main/j/jack2/",
            "libopus" to "pool/main/libo/libopus/",
            "libsamplerate" to "pool/main/libs/libsamplerate/",
            "libcairo" to "pool/main/libc/libcairo/",
            "libsasl" to "pool/main/libs/libsasl/",
            "libandroid-support" to "pool/main/liba/libandroid-support/",
            "argp" to "pool/main/a/argp/",
            // More deps discovered
            "libandroid-sysv-semaphore" to "pool/main/liba/libandroid-sysv-semaphore/",
            "libandroid-posix-semaphore" to "pool/main/liba/libandroid-posix-semaphore/",
            "libandroid-glob" to "pool/main/liba/libandroid-glob/",
            "libandroid-execinfo" to "pool/main/liba/libandroid-execinfo/",
            "dbus" to "pool/main/d/dbus/",
            "libexpat" to "pool/main/libe/libexpat/",
            "libltdl" to "pool/main/libl/libltdl/",
            "liblzma" to "pool/main/libl/liblzma/",
            "liblz4" to "pool/main/libl/liblz4/",
            "libssh2" to "pool/main/libs/libssh2/",
            "libdb" to "pool/main/libd/libdb/",
            "libspice-protocol" to "pool/main/libs/libspice-protocol/",
            "libsoxr" to "pool/main/libs/libsoxr/",
            "liborc" to "pool/main/libo/liborc/",
            "libunbound" to "pool/main/libu/libunbound/",
            "libxrender" to "pool/main/libx/libxrender/",
            "libxext" to "pool/main/libx/libxext/",
            "fontconfig" to "pool/main/f/fontconfig/",
            "freetype" to "pool/main/f/freetype/",
            "libharfbuzz" to "pool/main/libh/libharfbuzz/",
            "graphite" to "pool/main/g/graphite/",
            "gstreamer" to "pool/main/g/gstreamer/",
            "gst-plugins-base" to "pool/main/g/gst-plugins-base/",
            "libc++" to "pool/main/libc/libc++/"
        )
    }

    private val baseDir = File(context.filesDir, "termux")
    private val usrDir = File(baseDir, "usr")
    val binDir = File(usrDir, "bin")
    private val libDir = File(usrDir, "lib")
    private val shareDir = File(usrDir, "share")
    private val tmpDir = File(context.cacheDir, "termux_tmp")

    val isInstalled: Boolean
        get() {
            val qemuFile = File(binDir, "qemu-system-x86_64")
            val gnutlsLib = File(libDir, "libgnutls.so")
            val pixmanLib = File(libDir, "libpixman-1.so")
            // Check both QEMU binary and critical libraries
            return qemuFile.exists() && qemuFile.length() > 100000 &&
                   gnutlsLib.exists() && pixmanLib.exists()
        }

    fun clearInstallation() {
        usrDir.deleteRecursively()
        usrDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        shareDir.mkdirs()
    }

    val qemuX86Path: String get() = File(binDir, "qemu-system-x86_64").absolutePath
    val qemuArmPath: String get() = File(binDir, "qemu-system-aarch64").absolutePath

    init {
        baseDir.mkdirs()
        usrDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        shareDir.mkdirs()
        tmpDir.mkdirs()
    }

    suspend fun installQemu(
        onProgress: (Int, String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress(0, "Checking installation...")

            if (isInstalled) {
                onProgress(100, "QEMU already installed")
                return@withContext Result.success(Unit)
            }

            // Clear any partial installation to start fresh
            onProgress(2, "Preparing installation...")
            clearInstallation()

            // Packages to install - includes all dependencies
            // Using correct Termux package names with lib prefix
            val packagesToInstall = listOf(
                // Dependencies first (order matters for library loading)
                "libc++",              // C++ standard library
                "libandroid-support",  // Core Android compatibility lib
                "argp",                // libargp.so
                "zlib",
                "libffi",
                "pcre2",
                "libgmp",
                "libnettle",       // libnettle includes libhogweed
                "libunistring",
                "libidn2",
                "libtasn1",
                "brotli",
                "zstd",
                "p11-kit",
                "libgnutls",       // libgnutls not gnutls
                "glib",
                "libpixman",
                "libjpeg-turbo",
                "libpng",
                "libslirp",
                "liblzo",
                "libcap-ng",
                "dtc",             // provides libfdt.so
                "libelf",
                "libdw",
                "ncurses",
                "libiconv",
                // Additional deps
                "openssl",
                "ca-certificates",
                "libnghttp2",
                "libnghttp3",
                "libbz2",
                "libcurl",
                "libnfs",
                "libssh",
                "libusb",
                "libusbredir",
                "alsa-lib",
                "libandroid-shmem",
                "libogg",
                "libvorbis",
                "libflac",
                "opus",
                "libsndfile",
                "pulseaudio",
                "libxau",
                "libxdmcp",
                "libxcb",
                "libx11",
                "libspice-server",
                "jack2",
                "libopus",
                "libsamplerate",
                "libcairo",
                "libsasl",
                // More deps
                "libandroid-sysv-semaphore",
                "libandroid-posix-semaphore",
                "libandroid-glob",
                "libandroid-execinfo",
                "dbus",
                "libexpat",
                "libltdl",
                "liblzma",
                "liblz4",
                "libssh2",
                "libdb",
                "libspice-protocol",
                "libsoxr",
                "liborc",
                "libunbound",
                "libxrender",
                "libxext",
                "fontconfig",
                "freetype",
                "libharfbuzz",
                "graphite",
                "gstreamer",
                "gst-plugins-base",
                // Core QEMU packages last
                "qemu-common",
                "qemu-system-x86-64-headless"
            )

            var progress = 5
            val progressPerPackage = 90 / packagesToInstall.size

            for (packageName in packagesToInstall) {
                onProgress(progress, "Finding $packageName...")

                val debUrl = findPackageUrl(packageName)
                if (debUrl != null) {
                    onProgress(progress + 5, "Downloading $packageName...")
                    Log.d(TAG, "Downloading: $debUrl")

                    val debFile = File(tmpDir, "$packageName.deb")
                    val downloaded = downloadFile(debUrl, debFile) { dlProgress ->
                        onProgress(
                            progress + 5 + (dlProgress * progressPerPackage / 200),
                            "Downloading $packageName... $dlProgress%"
                        )
                    }

                    if (downloaded) {
                        onProgress(progress + progressPerPackage / 2, "Extracting $packageName...")
                        Log.d(TAG, "Downloaded $packageName (${debFile.length()} bytes), extracting...")
                        extractDebPure(debFile)
                        debFile.delete()
                        Log.d(TAG, "Successfully extracted: $packageName")
                    } else {
                        Log.e(TAG, "FAILED to download: $packageName from $debUrl")
                    }
                } else {
                    Log.e(TAG, "FAILED to find package URL for: $packageName")
                }

                progress += progressPerPackage
            }

            // Set executable permissions
            onProgress(95, "Setting permissions...")
            setPermissions()

            if (isInstalled) {
                onProgress(100, "QEMU installed successfully!")
                Result.success(Unit)
            } else {
                // Check what we have
                val files = binDir.listFiles()?.map { it.name } ?: emptyList()
                Log.d(TAG, "Files in binDir: $files")
                onProgress(100, "Installation incomplete")
                Result.failure(Exception("QEMU binary not found. Files: $files"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            Result.failure(e)
        }
    }

    private fun findPackageUrl(packageName: String): String? {
        return try {
            val packagePath = PACKAGES[packageName] ?: "pool/main/${packageName[0]}/$packageName/"
            val indexUrl = "$TERMUX_REPO/$packagePath"

            Log.d(TAG, "Looking for package at: $indexUrl")

            val connection = URL(indexUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "AndroidInAndroid/1.0")
            connection.connect()

            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                // Find the aarch64 .deb file (for ARM64 Android devices)
                val regex = """href="([^"]*aarch64[^"]*\.deb)"""".toRegex()
                val matches = regex.findAll(html).toList()

                if (matches.isNotEmpty()) {
                    val debName = matches.last().groupValues[1]
                    val fullUrl = "$indexUrl$debName"
                    Log.d(TAG, "Found package: $fullUrl")
                    return fullUrl
                }

                // Fallback: any .deb
                val fallbackRegex = """href="([^"]+\.deb)"""".toRegex()
                val fallbackMatch = fallbackRegex.findAll(html).lastOrNull()
                fallbackMatch?.let {
                    val debName = it.groupValues[1]
                    return "$indexUrl$debName"
                }
            }

            Log.w(TAG, "Package not found: $packageName (HTTP ${connection.responseCode})")
            connection.disconnect()
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding package $packageName: ${e.message}")
            null
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

            Log.d(TAG, "Downloaded: ${outputFile.name} (${outputFile.length()} bytes)")
            return outputFile.exists() && outputFile.length() > 1000
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Extract .deb file using pure Java (Apache Commons Compress)
     * .deb files are ar archives containing:
     * - debian-binary
     * - control.tar.xz (or .gz/.zst)
     * - data.tar.xz (or .gz/.zst) <- actual files
     */
    private fun extractDebPure(debFile: File) {
        try {
            Log.d(TAG, "Extracting deb: ${debFile.name} (${debFile.length()} bytes)")

            FileInputStream(debFile).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    ArArchiveInputStream(bis).use { arIn ->
                        var entry = arIn.nextEntry
                        while (entry != null) {
                            Log.d(TAG, "AR entry: ${entry.name} (${entry.size} bytes)")

                            if (entry.name.startsWith("data.tar")) {
                                // Extract the data.tar to a temp file first
                                val dataTarFile = File(tmpDir, entry.name)
                                FileOutputStream(dataTarFile).use { fos ->
                                    arIn.copyTo(fos)
                                }

                                // Now extract the tar archive
                                extractDataTar(dataTarFile)
                                dataTarFile.delete()
                            }

                            entry = arIn.nextEntry
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extract failed: ${e.message}", e)
        }
    }

    // Store deferred symlinks for second pass resolution
    private val deferredSymlinks = mutableListOf<Pair<File, String>>()

    private fun extractDataTar(dataTarFile: File) {
        try {
            Log.d(TAG, "Extracting data tar: ${dataTarFile.name}")
            deferredSymlinks.clear()

            val inputStream = when {
                dataTarFile.name.endsWith(".xz") -> {
                    TarArchiveInputStream(XZCompressorInputStream(BufferedInputStream(FileInputStream(dataTarFile))))
                }
                dataTarFile.name.endsWith(".gz") -> {
                    TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(FileInputStream(dataTarFile))))
                }
                dataTarFile.name.endsWith(".zst") -> {
                    TarArchiveInputStream(ZstdCompressorInputStream(BufferedInputStream(FileInputStream(dataTarFile))))
                }
                else -> {
                    TarArchiveInputStream(BufferedInputStream(FileInputStream(dataTarFile)))
                }
            }

            inputStream.use { tarIn ->
                var tarEntry = tarIn.nextEntry
                while (tarEntry != null) {
                    // Termux packages have paths like: data/data/com.termux/files/usr/...
                    // We need to extract to our usr directory
                    val name = tarEntry.name

                    // Find the usr/ part and extract relative to our usrDir
                    val usrIndex = name.indexOf("/usr/")
                    if (usrIndex >= 0) {
                        val relativePath = name.substring(usrIndex + 5) // Skip "/usr/"
                        val destFile = File(usrDir, relativePath)

                        if (tarEntry.isDirectory) {
                            destFile.mkdirs()
                        } else if (tarEntry.isSymbolicLink) {
                            // Handle symlinks by copying the target file
                            val linkTarget = tarEntry.linkName
                            Log.d(TAG, "Symlink: $relativePath -> $linkTarget")

                            // Resolve the symlink target relative to the current file's directory
                            val parentDir = destFile.parentFile
                            val targetFile = if (linkTarget.startsWith("/")) {
                                // Absolute path within package - map to our usrDir
                                val usrIdx = linkTarget.indexOf("/usr/")
                                if (usrIdx >= 0) {
                                    File(usrDir, linkTarget.substring(usrIdx + 5))
                                } else {
                                    File(usrDir, linkTarget.removePrefix("/"))
                                }
                            } else {
                                // Relative path
                                File(parentDir, linkTarget).canonicalFile
                            }

                            // If target exists, copy it; otherwise create a placeholder
                            if (targetFile.exists()) {
                                destFile.parentFile?.mkdirs()
                                targetFile.copyTo(destFile, overwrite = true)
                                if (targetFile.canExecute()) {
                                    destFile.setExecutable(true, false)
                                }
                                Log.d(TAG, "Resolved symlink: $relativePath (copied from ${targetFile.name})")
                            } else {
                                // Target doesn't exist yet - will be created later
                                // Store symlink info for later resolution
                                deferredSymlinks.add(destFile to linkTarget)
                                Log.d(TAG, "Deferred symlink: $relativePath -> $linkTarget (target not yet available)")
                            }
                        } else {
                            destFile.parentFile?.mkdirs()
                            FileOutputStream(destFile).use { fos ->
                                tarIn.copyTo(fos)
                            }

                            // Preserve executable permission
                            if ((tarEntry.mode and 0b001001001) != 0) {
                                destFile.setExecutable(true, false)
                            }
                        }
                    }

                    tarEntry = tarIn.nextEntry
                }
            }

            // Second pass: resolve deferred symlinks with multiple iterations
            // (handles symlink chains like libfoo.so -> libfoo.so.1 -> libfoo.so.1.2.3)
            if (deferredSymlinks.isNotEmpty()) {
                Log.d(TAG, "Resolving ${deferredSymlinks.size} deferred symlinks...")
                var remainingSymlinks = deferredSymlinks.toMutableList()
                var lastCount = remainingSymlinks.size + 1
                var iterations = 0
                val maxIterations = 10

                while (remainingSymlinks.isNotEmpty() && remainingSymlinks.size < lastCount && iterations < maxIterations) {
                    lastCount = remainingSymlinks.size
                    iterations++
                    val stillDeferred = mutableListOf<Pair<File, String>>()

                    for ((destFile, linkTarget) in remainingSymlinks) {
                        val parentDir = destFile.parentFile
                        val targetFile = if (linkTarget.startsWith("/")) {
                            val usrIdx = linkTarget.indexOf("/usr/")
                            if (usrIdx >= 0) {
                                File(usrDir, linkTarget.substring(usrIdx + 5))
                            } else {
                                File(usrDir, linkTarget.removePrefix("/"))
                            }
                        } else {
                            File(parentDir, linkTarget).canonicalFile
                        }

                        if (targetFile.exists() && !destFile.exists()) {
                            destFile.parentFile?.mkdirs()
                            targetFile.copyTo(destFile, overwrite = true)
                            if (targetFile.canExecute()) {
                                destFile.setExecutable(true, false)
                            }
                            Log.d(TAG, "Resolved deferred symlink: ${destFile.name}")
                        } else if (!destFile.exists()) {
                            stillDeferred.add(destFile to linkTarget)
                        }
                    }
                    remainingSymlinks = stillDeferred
                }

                // Log any remaining unresolved symlinks (but don't spam for copyright files)
                for ((destFile, linkTarget) in remainingSymlinks) {
                    if (!destFile.name.startsWith("copyright")) {
                        Log.w(TAG, "Cannot resolve symlink ${destFile.name}: target $linkTarget not found")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Data tar extraction failed: ${e.message}", e)
        }
    }

    private fun setPermissions() {
        binDir.listFiles()?.forEach { file ->
            file.setExecutable(true, false)
            file.setReadable(true, false)
        }

        // Set library permissions
        libDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "so" || file.name.contains(".so."))) {
                file.setExecutable(true, false)
                file.setReadable(true, false)
            }
        }
    }

    fun getQemuEnvironment(): Map<String, String> = mapOf(
        "QEMU_AUDIO_DRV" to "none",
        "HOME" to context.filesDir.absolutePath,
        "TMPDIR" to context.cacheDir.absolutePath,
        "LD_LIBRARY_PATH" to "${libDir.absolutePath}:${libDir.absolutePath}/pulseaudio:/system/lib64",
        "PATH" to "${binDir.absolutePath}:/system/bin",
        "TERMUX_PREFIX" to usrDir.absolutePath
    )
}
