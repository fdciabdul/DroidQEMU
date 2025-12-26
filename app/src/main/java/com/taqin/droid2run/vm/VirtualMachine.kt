package com.taqin.droid2run.vm

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * OS Type presets for optimal QEMU configuration
 */
enum class OSType(val displayName: String, val minMemory: Int, val recommendedMemory: Int, val minDisk: Int) {
    WINDOWS_XP("Windows XP", 256, 512, 4096),
    WINDOWS_7("Windows 7", 1024, 2048, 20480),
    WINDOWS_10("Windows 10/11", 2048, 4096, 40960),
    LINUX("Linux", 256, 1024, 8192),
    OTHER("Other OS", 256, 512, 4096)
}

/**
 * Represents a virtual machine configuration
 */
data class VMConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val arch: VMArch = VMArch.X86_64,
    val osType: OSType = OSType.OTHER,
    val memory: Int = 512, // MB
    val cpus: Int = 2,
    val diskSize: Int = 4096, // MB
    val diskImage: String? = null,
    val cdrom: String? = null,
    val vncPort: Int = 5900,
    val enableKvm: Boolean = false,
    val bootOrder: String = "cd",
    val enableAcpi: Boolean = true,
    val vgaType: String = "std", // std, cirrus, vmware, qxl
    val soundEnabled: Boolean = false
)

enum class VMArch(val qemuName: String, val displayName: String) {
    X86_64("qemu-system-x86_64", "x86 64-bit"),
    AARCH64("qemu-system-aarch64", "ARM 64-bit"),
    I386("qemu-system-i386", "x86 32-bit"),
    ARM("qemu-system-arm", "ARM 32-bit")
}

enum class VMState {
    STOPPED, STARTING, RUNNING, STOPPING, ERROR
}

/**
 * Manages QEMU virtual machines
 */
class VirtualMachineManager(private val context: Context) {

    companion object {
        private const val TAG = "VMManager"
    }

    private val vmDir = File(context.filesDir, "vms")
    private val imagesDir = File(vmDir, "images")
    private val configDir = File(vmDir, "configs")

    val qemuInstaller = QemuInstaller(context)
    val isQemuInstalled: Boolean get() = qemuInstaller.isInstalled

    private val runningVMs = mutableMapOf<String, Process>()

    init {
        vmDir.mkdirs()
        imagesDir.mkdirs()
        configDir.mkdirs()
    }

    fun getVMList(): List<VMConfig> {
        return configDir.listFiles()
            ?.filter { it.extension == "conf" }
            ?.mapNotNull { loadConfig(it) }
            ?: emptyList()
    }

    fun getVMState(vmId: String): VMState {
        val process = runningVMs[vmId]
        return when {
            process == null -> VMState.STOPPED
            process.isAlive -> VMState.RUNNING
            else -> {
                runningVMs.remove(vmId)
                VMState.STOPPED
            }
        }
    }

    suspend fun createDiskImage(name: String, sizeMB: Int): Result<File> = withContext(Dispatchers.IO) {
        try {
            val imageFile = File(imagesDir, "$name.qcow2")

            if (imageFile.exists()) {
                Log.d(TAG, "Disk image already exists: ${imageFile.absolutePath}")
                return@withContext Result.success(imageFile)
            }

            // Create qcow2 image using qemu-img
            val qemuImg = File(qemuInstaller.binDir, "qemu-img")
            if (qemuImg.exists()) {
                // Use linker for Android 10+ noexec bypass
                val linker = if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                    "/system/bin/linker64"
                } else {
                    "/system/bin/linker"
                }

                val cmd = listOf(linker, qemuImg.absolutePath, "create", "-f", "qcow2", imageFile.absolutePath, "${sizeMB}M")
                Log.d(TAG, "Creating disk image: ${cmd.joinToString(" ")}")

                val pb = ProcessBuilder(cmd)
                    .directory(imagesDir)
                    .redirectErrorStream(true)
                pb.environment().putAll(qemuInstaller.getQemuEnvironment())

                val process = pb.start()
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                Log.d(TAG, "qemu-img output: $output, exit: $exitCode")

                if (exitCode != 0 || !imageFile.exists()) {
                    Log.e(TAG, "qemu-img failed: $output")
                    // Fallback to raw image
                    return@withContext createRawImage(name, sizeMB)
                }
            } else {
                // Fallback: create raw image
                return@withContext createRawImage(name, sizeMB)
            }

            Result.success(imageFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create disk image", e)
            Result.failure(e)
        }
    }

    private fun createRawImage(name: String, sizeMB: Int): Result<File> {
        return try {
            val rawFile = File(imagesDir, "$name.img")
            Log.d(TAG, "Creating raw image: ${rawFile.absolutePath}")

            // Create sparse file using truncate-like approach
            java.io.RandomAccessFile(rawFile, "rw").use { raf ->
                raf.setLength(sizeMB.toLong() * 1024 * 1024)
            }

            Result.success(rawFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create raw image", e)
            Result.failure(e)
        }
    }

    fun saveConfig(config: VMConfig) {
        val configFile = File(configDir, "${config.id}.conf")
        configFile.writeText("""
            id=${config.id}
            name=${config.name}
            arch=${config.arch.name}
            osType=${config.osType.name}
            memory=${config.memory}
            cpus=${config.cpus}
            diskSize=${config.diskSize}
            diskImage=${config.diskImage ?: ""}
            cdrom=${config.cdrom ?: ""}
            vncPort=${config.vncPort}
            enableKvm=${config.enableKvm}
            bootOrder=${config.bootOrder}
            enableAcpi=${config.enableAcpi}
            vgaType=${config.vgaType}
            soundEnabled=${config.soundEnabled}
        """.trimIndent())
    }

    private fun loadConfig(file: File): VMConfig? {
        return try {
            val props = file.readLines()
                .filter { it.contains("=") }
                .associate {
                    val (key, value) = it.split("=", limit = 2)
                    key.trim() to value.trim()
                }

            VMConfig(
                id = props["id"] ?: return null,
                name = props["name"] ?: "Unknown",
                arch = VMArch.valueOf(props["arch"] ?: "X86_64"),
                osType = try { OSType.valueOf(props["osType"] ?: "OTHER") } catch (e: Exception) { OSType.OTHER },
                memory = props["memory"]?.toIntOrNull() ?: 512,
                cpus = props["cpus"]?.toIntOrNull() ?: 2,
                diskSize = props["diskSize"]?.toIntOrNull() ?: 4096,
                diskImage = props["diskImage"]?.takeIf { it.isNotEmpty() },
                cdrom = props["cdrom"]?.takeIf { it.isNotEmpty() },
                vncPort = props["vncPort"]?.toIntOrNull() ?: 5900,
                enableKvm = props["enableKvm"]?.toBoolean() ?: false,
                bootOrder = props["bootOrder"] ?: "cd",
                enableAcpi = props["enableAcpi"]?.toBoolean() ?: true,
                vgaType = props["vgaType"] ?: "std",
                soundEnabled = props["soundEnabled"]?.toBoolean() ?: false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config: ${file.name}", e)
            null
        }
    }

    fun deleteVM(vmId: String) {
        stopVM(vmId)
        File(configDir, "$vmId.conf").delete()
    }

    fun buildQemuCommand(config: VMConfig): List<String> {
        val cmd = mutableListOf<String>()

        // Use Termux QEMU - force x86_64 for i386 since we only have x86_64 installed
        val qemuBinary = when (config.arch) {
            VMArch.X86_64, VMArch.I386 -> "qemu-system-x86_64" // x86_64 can run 32-bit OS
            VMArch.AARCH64, VMArch.ARM -> "qemu-system-aarch64" // aarch64 can run 32-bit ARM
        }
        cmd.add(File(qemuInstaller.binDir, qemuBinary).absolutePath)

        // BIOS/firmware directory
        val shareDir = File(qemuInstaller.binDir.parentFile, "share/qemu")
        cmd.addAll(listOf("-L", shareDir.absolutePath))

        // Machine type based on OS - optimized for TCG (software emulation)
        when (config.arch) {
            VMArch.X86_64, VMArch.I386 -> {
                // Use isapc for fastest boot, pc for compatibility
                cmd.addAll(listOf("-machine", "pc,accel=tcg"))
                // Use qemu64 for speed - simpler than max
                cmd.addAll(listOf("-cpu", "qemu64"))
            }
            VMArch.AARCH64, VMArch.ARM -> {
                cmd.addAll(listOf("-machine", "virt,accel=tcg"))
                cmd.addAll(listOf("-cpu", "cortex-a53"))
            }
        }

        // Memory and CPUs - single CPU is faster in TCG
        cmd.addAll(listOf("-m", "${config.memory}M"))
        cmd.addAll(listOf("-smp", "1"))

        // Speed optimizations
        cmd.addAll(listOf("-global", "isa-debugcon.chardev=null"))
        cmd.addAll(listOf("-chardev", "null,id=null"))

        // ACPI is enabled by default on PC machines
        // Use -no-acpi to disable if needed
        if (!config.enableAcpi) {
            cmd.add("-no-acpi")
        }

        // VGA display - cirrus is faster than std for TCG
        val vga = when (config.osType) {
            OSType.LINUX -> "virtio" // Fastest for Linux with drivers
            OSType.WINDOWS_XP -> "cirrus" // XP has cirrus driver
            else -> "std" // Safe default
        }
        cmd.addAll(listOf("-vga", vga))
        cmd.addAll(listOf("-display", "none")) // VNC only

        // Disk - Use IDE for Windows compatibility (no drivers needed)
        val diskFile = config.diskImage ?: File(imagesDir, "${config.id}.qcow2").absolutePath
        val diskFormat = if (diskFile.endsWith(".qcow2")) "qcow2" else "raw"

        when (config.osType) {
            OSType.WINDOWS_XP, OSType.WINDOWS_7 -> {
                // IDE for older Windows - no drivers needed
                cmd.addAll(listOf("-drive", "file=$diskFile,format=$diskFormat,if=ide,index=0,media=disk"))
            }
            OSType.WINDOWS_10 -> {
                // AHCI/SATA for Windows 10
                cmd.addAll(listOf("-drive", "file=$diskFile,format=$diskFormat,if=ide,index=0,media=disk"))
            }
            OSType.LINUX -> {
                // VirtIO for Linux (better performance)
                cmd.addAll(listOf("-drive", "file=$diskFile,format=$diskFormat,if=virtio"))
            }
            else -> {
                cmd.addAll(listOf("-drive", "file=$diskFile,format=$diskFormat,if=ide,index=0,media=disk"))
            }
        }

        // CD-ROM - Always use IDE for compatibility
        config.cdrom?.let { isoPath ->
            if (File(isoPath).exists()) {
                cmd.addAll(listOf("-cdrom", isoPath))
                Log.d(TAG, "CD-ROM: $isoPath")
            } else {
                Log.e(TAG, "ISO not found: $isoPath")
            }
        }

        // Boot order - 'd' means CD-ROM first
        val bootOrd = if (config.cdrom != null) "d" else config.bootOrder
        cmd.addAll(listOf("-boot", "order=$bootOrd,menu=on"))

        // VNC display with keyboard layout
        val vncDisplay = config.vncPort - 5900
        cmd.addAll(listOf("-vnc", ":$vncDisplay"))

        // Network - Use compatible adapters for Windows
        when (config.osType) {
            OSType.WINDOWS_XP -> {
                // RTL8139 - XP has built-in driver
                cmd.addAll(listOf("-netdev", "user,id=net0"))
                cmd.addAll(listOf("-device", "rtl8139,netdev=net0"))
            }
            OSType.WINDOWS_7, OSType.WINDOWS_10 -> {
                // E1000 - Windows 7/10 has built-in driver
                cmd.addAll(listOf("-netdev", "user,id=net0"))
                cmd.addAll(listOf("-device", "e1000,netdev=net0"))
            }
            OSType.LINUX -> {
                // VirtIO for Linux
                cmd.addAll(listOf("-netdev", "user,id=net0"))
                cmd.addAll(listOf("-device", "virtio-net-pci,netdev=net0"))
            }
            else -> {
                cmd.addAll(listOf("-netdev", "user,id=net0"))
                cmd.addAll(listOf("-device", "e1000,netdev=net0"))
            }
        }

        // USB for mouse/keyboard - better compatibility
        cmd.addAll(listOf("-usb"))
        cmd.addAll(listOf("-device", "usb-tablet")) // Absolute positioning for VNC mouse

        // RTC (Real Time Clock) - Windows needs this
        cmd.addAll(listOf("-rtc", "base=localtime,clock=host"))

        // KVM acceleration (usually not available on Android)
        if (config.enableKvm) {
            cmd.add("-enable-kvm")
        }

        return cmd
    }

    suspend fun startVM(config: VMConfig): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (getVMState(config.id) == VMState.RUNNING) {
                return@withContext Result.success(config.vncPort)
            }

            if (!isQemuInstalled) {
                return@withContext Result.failure(Exception("QEMU not installed"))
            }

            // Create disk image if not exists
            val qcow2File = File(imagesDir, "${config.id}.qcow2")
            val rawFile = File(imagesDir, "${config.id}.img")
            val diskFile = when {
                qcow2File.exists() -> qcow2File
                rawFile.exists() -> rawFile
                else -> {
                    Log.d(TAG, "Creating disk image for VM: ${config.id}")
                    val result = createDiskImage(config.id, config.diskSize)
                    result.getOrNull() ?: qcow2File
                }
            }
            Log.d(TAG, "Using disk image: ${diskFile.absolutePath}")

            // Resolve ISO path if present (handles content:// URIs)
            val resolvedConfig = if (config.cdrom != null) {
                val resolvedIso = resolveIsoPath(context, config.cdrom)
                config.copy(cdrom = resolvedIso, diskImage = diskFile.absolutePath)
            } else {
                config.copy(diskImage = diskFile.absolutePath)
            }

            val cmd = buildQemuCommand(resolvedConfig)
            Log.d(TAG, "Starting VM: ${cmd.joinToString(" ")}")

            // On Android 10+, we need to execute through the linker due to noexec on /data
            // Determine the correct linker based on architecture
            val linker = if (android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                "/system/bin/linker64"
            } else {
                "/system/bin/linker"
            }

            // Build command with linker prefix
            val linkerCmd = mutableListOf(linker)
            linkerCmd.addAll(cmd)

            Log.d(TAG, "Executing via linker: ${linkerCmd.joinToString(" ")}")

            val processBuilder = ProcessBuilder(linkerCmd)
                .directory(vmDir)
                .redirectErrorStream(true)

            // Set environment for QEMU
            val env = processBuilder.environment()
            env.putAll(qemuInstaller.getQemuEnvironment())
            env["QEMU_AUDIO_DRV"] = "none"

            val process = processBuilder.start()

            runningVMs[config.id] = process

            // Wait a bit for QEMU to start
            Thread.sleep(2000)

            if (!process.isAlive) {
                val error = process.inputStream.bufferedReader().readText()
                Log.e(TAG, "QEMU failed: $error")
                return@withContext Result.failure(Exception("QEMU failed to start: $error"))
            }

            Result.success(config.vncPort)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VM", e)
            Result.failure(e)
        }
    }

    fun stopVM(vmId: String) {
        runningVMs[vmId]?.let { process ->
            process.destroy()
            runningVMs.remove(vmId)
        }
    }

    fun getImagesDir(): File = imagesDir

    /**
     * Resolve ISO path - handles both file:// and content:// URIs
     * For content URIs, copies the file to local storage for QEMU access
     */
    suspend fun resolveIsoPath(context: Context, isoUri: String): String? = withContext(Dispatchers.IO) {
        try {
            when {
                isoUri.startsWith("content://") -> {
                    // Content URI - need to copy to local storage
                    val uri = Uri.parse(isoUri)
                    val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "boot.iso"
                    val localFile = File(imagesDir, "iso_${fileName.hashCode()}.iso")

                    if (!localFile.exists()) {
                        Log.d(TAG, "Copying ISO from content URI to local: ${localFile.absolutePath}")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(localFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    localFile.absolutePath
                }
                isoUri.startsWith("file://") -> {
                    Uri.parse(isoUri).path
                }
                else -> {
                    // Assume it's already a file path
                    isoUri
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve ISO path", e)
            null
        }
    }
}
