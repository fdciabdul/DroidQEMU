package com.taqin.droid2run.vnc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * Simple VNC client for connecting to QEMU VNC server
 */
class VncClient {

    companion object {
        private const val TAG = "VncClient"

        // VNC protocol constants
        private const val RFB_VERSION = "RFB 003.008\n"
        private const val SECURITY_NONE = 1
        private const val ENCODING_RAW = 0
        private const val ENCODING_COPYRECT = 1
        private const val ENCODING_DESKTOP_SIZE = -223
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private var framebuffer: Bitmap? = null
    private var width = 800
    private var height = 600

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _frameBuffer = MutableStateFlow<Bitmap?>(null)
    val frameBuffer: StateFlow<Bitmap?> = _frameBuffer

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    suspend fun connect(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.CONNECTING

            socket = Socket(host, port)
            socket?.soTimeout = 5000

            input = DataInputStream(socket!!.getInputStream())
            output = DataOutputStream(socket!!.getOutputStream())

            // Handshake
            performHandshake()

            // Initialize
            sendClientInit()
            readServerInit()

            // Request initial framebuffer
            requestFramebufferUpdate(false)

            _connectionState.value = ConnectionState.CONNECTED
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionState.value = ConnectionState.ERROR
            Result.failure(e)
        }
    }

    private fun performHandshake() {
        // Read server version
        val serverVersion = ByteArray(12)
        input?.readFully(serverVersion)
        Log.d(TAG, "Server version: ${String(serverVersion)}")

        // Send client version
        output?.write(RFB_VERSION.toByteArray())
        output?.flush()

        // Read security types
        val numSecurityTypes = input?.readByte()?.toInt() ?: 0
        if (numSecurityTypes == 0) {
            val reasonLength = input?.readInt() ?: 0
            val reason = ByteArray(reasonLength)
            input?.readFully(reason)
            throw Exception("Server rejected: ${String(reason)}")
        }

        val securityTypes = ByteArray(numSecurityTypes)
        input?.readFully(securityTypes)

        // Choose no authentication
        if (securityTypes.contains(SECURITY_NONE.toByte())) {
            output?.writeByte(SECURITY_NONE)
            output?.flush()
        } else {
            throw Exception("No supported security type")
        }

        // Read security result (RFB 3.8+)
        val result = input?.readInt() ?: 1
        if (result != 0) {
            throw Exception("Security handshake failed")
        }
    }

    private fun sendClientInit() {
        // Shared flag (1 = share desktop)
        output?.writeByte(1)
        output?.flush()
    }

    private fun readServerInit() {
        width = input?.readUnsignedShort() ?: 800
        height = input?.readUnsignedShort() ?: 600

        // Pixel format (16 bytes)
        val bitsPerPixel = input?.readByte()?.toInt() ?: 32
        val depth = input?.readByte()?.toInt() ?: 24
        val bigEndian = input?.readByte()?.toInt() ?: 0
        val trueColor = input?.readByte()?.toInt() ?: 1
        val redMax = input?.readUnsignedShort() ?: 255
        val greenMax = input?.readUnsignedShort() ?: 255
        val blueMax = input?.readUnsignedShort() ?: 255
        val redShift = input?.readByte()?.toInt() ?: 16
        val greenShift = input?.readByte()?.toInt() ?: 8
        val blueShift = input?.readByte()?.toInt() ?: 0
        input?.skipBytes(3) // Padding

        // Name
        val nameLength = input?.readInt() ?: 0
        val name = ByteArray(nameLength)
        input?.readFully(name)

        Log.d(TAG, "Desktop: ${width}x${height} - ${String(name)}")

        // Create framebuffer
        framebuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        framebuffer?.eraseColor(Color.BLACK)
        _frameBuffer.value = framebuffer

        // Set pixel format to 32-bit BGRA
        setPixelFormat()
        setEncodings()
    }

    private fun setPixelFormat() {
        output?.writeByte(0) // Message type: SetPixelFormat
        output?.writeByte(0) // Padding
        output?.writeByte(0)
        output?.writeByte(0)

        // Pixel format
        output?.writeByte(32) // bits per pixel
        output?.writeByte(24) // depth
        output?.writeByte(0)  // big endian
        output?.writeByte(1)  // true color

        output?.writeShort(255) // red max
        output?.writeShort(255) // green max
        output?.writeShort(255) // blue max

        output?.writeByte(16) // red shift
        output?.writeByte(8)  // green shift
        output?.writeByte(0)  // blue shift

        output?.writeByte(0) // Padding
        output?.writeByte(0)
        output?.writeByte(0)

        output?.flush()
    }

    private fun setEncodings() {
        output?.writeByte(2) // Message type: SetEncodings
        output?.writeByte(0) // Padding
        output?.writeShort(2) // Number of encodings

        output?.writeInt(ENCODING_RAW)
        output?.writeInt(ENCODING_DESKTOP_SIZE)

        output?.flush()
    }

    fun requestFramebufferUpdate(incremental: Boolean) {
        try {
            output?.writeByte(3) // Message type: FramebufferUpdateRequest
            output?.writeByte(if (incremental) 1 else 0)
            output?.writeShort(0) // x
            output?.writeShort(0) // y
            output?.writeShort(width) // width
            output?.writeShort(height) // height
            output?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request framebuffer update", e)
        }
    }

    suspend fun processServerMessages() = withContext(Dispatchers.IO) {
        while (_connectionState.value == ConnectionState.CONNECTED) {
            try {
                val messageType = input?.readByte()?.toInt() ?: break

                when (messageType) {
                    0 -> handleFramebufferUpdate()
                    1 -> handleSetColorMapEntries()
                    2 -> handleBell()
                    3 -> handleServerCutText()
                    else -> Log.w(TAG, "Unknown message type: $messageType")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing server message", e)
                break
            }
        }
    }

    private fun handleFramebufferUpdate() {
        input?.readByte() // Padding
        val numRects = input?.readUnsignedShort() ?: 0

        for (i in 0 until numRects) {
            val x = input?.readUnsignedShort() ?: 0
            val y = input?.readUnsignedShort() ?: 0
            val w = input?.readUnsignedShort() ?: 0
            val h = input?.readUnsignedShort() ?: 0
            val encoding = input?.readInt() ?: 0

            when (encoding) {
                ENCODING_RAW -> handleRawRect(x, y, w, h)
                ENCODING_DESKTOP_SIZE -> handleDesktopSize(w, h)
                else -> Log.w(TAG, "Unsupported encoding: $encoding")
            }
        }

        _frameBuffer.value = framebuffer
        requestFramebufferUpdate(true)
    }

    private fun handleRawRect(x: Int, y: Int, w: Int, h: Int) {
        val pixels = IntArray(w * h)
        for (i in pixels.indices) {
            val b = input?.readUnsignedByte() ?: 0
            val g = input?.readUnsignedByte() ?: 0
            val r = input?.readUnsignedByte() ?: 0
            val a = input?.readUnsignedByte() ?: 255
            pixels[i] = Color.argb(255, r, g, b)
        }

        framebuffer?.setPixels(pixels, 0, w, x, y, w, h)
    }

    private fun handleDesktopSize(w: Int, h: Int) {
        width = w
        height = h
        framebuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        framebuffer?.eraseColor(Color.BLACK)
    }

    private fun handleSetColorMapEntries() {
        input?.readByte() // Padding
        val firstColor = input?.readUnsignedShort() ?: 0
        val numColors = input?.readUnsignedShort() ?: 0

        for (i in 0 until numColors) {
            input?.readUnsignedShort() // red
            input?.readUnsignedShort() // green
            input?.readUnsignedShort() // blue
        }
    }

    private fun handleBell() {
        // Bell event - could trigger vibration
    }

    private fun handleServerCutText() {
        input?.skipBytes(3) // Padding
        val length = input?.readInt() ?: 0
        input?.skipBytes(length)
    }

    fun sendKeyEvent(key: Int, down: Boolean) {
        try {
            output?.writeByte(4) // Message type: KeyEvent
            output?.writeByte(if (down) 1 else 0)
            output?.writeShort(0) // Padding
            output?.writeInt(key)
            output?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send key event", e)
        }
    }

    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        try {
            output?.writeByte(5) // Message type: PointerEvent
            output?.writeByte(buttonMask)
            output?.writeShort(x)
            output?.writeShort(y)
            output?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send pointer event", e)
        }
    }

    fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        socket = null
        input = null
        output = null
    }
}
