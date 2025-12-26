package com.taqin.droid2run.terminal

import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.taqin.droid2run.databinding.ActivityTerminalBinding
import com.taqin.droid2run.linux.LinuxEnvironment
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalViewClient

class LinuxTerminalActivity : AppCompatActivity(), TerminalViewClient {

    private lateinit var binding: ActivityTerminalBinding
    private lateinit var linuxEnv: LinuxEnvironment
    private var terminalSession: TerminalSession? = null
    private var isCtrlPressed = false
    private var isAltPressed = false
    private var currentTextSize = 24

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            binding.terminalView.onScreenUpdated()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {}

        override fun onSessionFinished(finishedSession: TerminalSession) {
            finish()
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text))
        }

        override fun onPasteTextFromClipboard(session: TerminalSession?) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this@LinuxTerminalActivity).toString()
                terminalSession?.write(text)
            }
        }

        override fun onBell(session: TerminalSession) {}

        override fun onColorsChanged(session: TerminalSession) {
            binding.terminalView.onScreenUpdated()
        }

        override fun onTerminalCursorStateChange(state: Boolean) {}

        override fun getTerminalCursorStyle(): Int = TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK

        override fun logError(tag: String, message: String) {
            android.util.Log.e(tag, message)
        }

        override fun logWarn(tag: String, message: String) {
            android.util.Log.w(tag, message)
        }

        override fun logInfo(tag: String, message: String) {
            android.util.Log.i(tag, message)
        }

        override fun logDebug(tag: String, message: String) {
            android.util.Log.d(tag, message)
        }

        override fun logVerbose(tag: String, message: String) {
            android.util.Log.v(tag, message)
        }

        override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
            android.util.Log.e(tag, message, e)
        }

        override fun logStackTrace(tag: String, e: Exception) {
            android.util.Log.e(tag, "Exception", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        linuxEnv = LinuxEnvironment(this)

        if (!linuxEnv.isInstalled) {
            Toast.makeText(this, "Linux environment not installed", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupTerminal()
        setupExtraKeys()
    }

    private fun setupTerminal() {
        val env = linuxEnv.getEnvironmentVariables()

        // Run shell in Linux rootfs
        val shell = "${linuxEnv.rootfsDir}/bin/sh"
        val args = arrayOf("-l")
        val cwd = linuxEnv.rootfsDir.absolutePath

        // Use a wrapper script to set up the environment
        val wrapperScript = """
            export HOME=/root
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export TERM=xterm-256color
            export LANG=C.UTF-8
            cd /root
            exec /bin/sh -l
        """.trimIndent()

        // Write wrapper script
        val scriptFile = java.io.File(linuxEnv.rootfsDir, "tmp/start.sh")
        scriptFile.parentFile?.mkdirs()
        scriptFile.writeText(wrapperScript)
        scriptFile.setExecutable(true)

        terminalSession = TerminalSession(
            "/system/bin/sh",
            cwd,
            arrayOf("-c", "cd ${linuxEnv.rootfsDir.absolutePath} && /bin/sh /tmp/start.sh"),
            env,
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            sessionClient
        )

        binding.terminalView.setTerminalViewClient(this)
        binding.terminalView.attachSession(terminalSession)
        binding.terminalView.setTextSize(currentTextSize)

        try {
            binding.terminalView.setTypeface(Typeface.MONOSPACE)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun setupExtraKeys() {
        binding.btnEsc.setOnClickListener { sendKey(KeyEvent.KEYCODE_ESCAPE) }
        binding.btnTab.setOnClickListener { sendKey(KeyEvent.KEYCODE_TAB) }
        binding.btnUp.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_UP) }
        binding.btnDown.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_DOWN) }
        binding.btnLeft.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        binding.btnRight.setOnClickListener { sendKey(KeyEvent.KEYCODE_DPAD_RIGHT) }
        binding.btnHome.setOnClickListener { sendKey(KeyEvent.KEYCODE_MOVE_HOME) }
        binding.btnEnd.setOnClickListener { sendKey(KeyEvent.KEYCODE_MOVE_END) }
        binding.btnPgup.setOnClickListener { sendKey(KeyEvent.KEYCODE_PAGE_UP) }
        binding.btnPgdn.setOnClickListener { sendKey(KeyEvent.KEYCODE_PAGE_DOWN) }

        binding.btnCtrl.setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            updateModifierButtonState(binding.btnCtrl, isCtrlPressed)
        }

        binding.btnAlt.setOnClickListener {
            isAltPressed = !isAltPressed
            updateModifierButtonState(binding.btnAlt, isAltPressed)
        }
    }

    private fun updateModifierButtonState(button: Button, pressed: Boolean) {
        button.alpha = if (pressed) 1.0f else 0.6f
    }

    private fun sendKey(keyCode: Int) {
        var metaState = 0
        if (isCtrlPressed) {
            metaState = metaState or KeyEvent.META_CTRL_ON
            isCtrlPressed = false
            updateModifierButtonState(binding.btnCtrl, false)
        }
        if (isAltPressed) {
            metaState = metaState or KeyEvent.META_ALT_ON
            isAltPressed = false
            updateModifierButtonState(binding.btnAlt, false)
        }

        val event = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        binding.terminalView.onKeyDown(keyCode, event)
    }

    // TerminalViewClient implementation
    override fun onScale(scale: Float): Float {
        currentTextSize = (currentTextSize * scale).toInt().coerceIn(12, 48)
        binding.terminalView.setTextSize(currentTextSize)
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        showKeyboard()
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    override fun shouldEnforceCharBasedInput(): Boolean = true
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true
    override fun copyModeChanged(copyMode: Boolean) {}
    override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
    override fun onLongPress(event: MotionEvent): Boolean = false
    override fun readControlKey(): Boolean = isCtrlPressed
    override fun readAltKey(): Boolean = isAltPressed
    override fun readShiftKey(): Boolean = false
    override fun readFnKey(): Boolean = false
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun logError(tag: String, message: String) { android.util.Log.e(tag, message) }
    override fun logWarn(tag: String, message: String) { android.util.Log.w(tag, message) }
    override fun logInfo(tag: String, message: String) { android.util.Log.i(tag, message) }
    override fun logDebug(tag: String, message: String) { android.util.Log.d(tag, message) }
    override fun logVerbose(tag: String, message: String) { android.util.Log.v(tag, message) }
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) { android.util.Log.e(tag, message, e) }
    override fun logStackTrace(tag: String, e: Exception) { android.util.Log.e(tag, "Exception", e) }

    override fun onEmulatorSet() {
        binding.terminalView.setTerminalCursorBlinkerState(true, true)
    }

    private fun showKeyboard() {
        binding.terminalView.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.terminalView, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onResume() {
        super.onResume()
        binding.terminalView.onScreenUpdated()
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }
}
