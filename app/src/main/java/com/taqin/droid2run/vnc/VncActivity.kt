package com.taqin.droid2run.vnc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.taqin.droid2run.R
import kotlinx.coroutines.launch

class VncActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
    }

    private lateinit var vncClient: VncClient
    private lateinit var displayView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var controlToolbar: LinearLayout
    private lateinit var btnKeyboard: ImageButton
    private lateinit var btnLeftClick: ImageButton
    private lateinit var btnRightClick: ImageButton
    private lateinit var btnCtrl: ImageButton
    private lateinit var btnAlt: ImageButton
    private lateinit var btnEsc: ImageButton

    private var lastTouchX = 0
    private var lastTouchY = 0
    private var isCtrlPressed = false
    private var isAltPressed = false
    private var currentMouseButton = 1 // 1=left, 4=right

    // Custom input view for capturing keyboard
    private lateinit var keyboardInputView: KeyboardInputView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vnc)

        displayView = findViewById(R.id.vnc_display)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
        controlToolbar = findViewById(R.id.control_toolbar)
        btnKeyboard = findViewById(R.id.btn_keyboard)
        btnLeftClick = findViewById(R.id.btn_left_click)
        btnRightClick = findViewById(R.id.btn_right_click)
        btnCtrl = findViewById(R.id.btn_ctrl)
        btnAlt = findViewById(R.id.btn_alt)
        btnEsc = findViewById(R.id.btn_esc)

        // Create keyboard input view
        keyboardInputView = KeyboardInputView(this)
        keyboardInputView.setOnKeyListener { key, down ->
            sendKeyWithModifiers(key, down)
        }

        val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
        val port = intent.getIntExtra(EXTRA_PORT, 5900)

        vncClient = VncClient()

        setupToolbarButtons()
        setupTouchHandling()
        connectToVnc(host, port)
    }

    private fun setupToolbarButtons() {
        // Set button icons with text
        btnCtrl.apply {
            setImageDrawable(null)
            background = createButtonBackground(false)
        }
        btnAlt.apply {
            setImageDrawable(null)
            background = createButtonBackground(false)
        }
        btnEsc.apply {
            setImageDrawable(null)
            background = createButtonBackground(false)
        }

        // Add text views for Ctrl, Alt, Esc
        (btnCtrl as? ImageButton)?.let {
            it.setImageDrawable(TextDrawable(this, "Ctrl"))
        }
        (btnAlt as? ImageButton)?.let {
            it.setImageDrawable(TextDrawable(this, "Alt"))
        }
        (btnEsc as? ImageButton)?.let {
            it.setImageDrawable(TextDrawable(this, "Esc"))
        }

        btnKeyboard.setOnClickListener {
            toggleKeyboard()
        }

        btnLeftClick.setOnClickListener {
            currentMouseButton = 1
            updateMouseButtonHighlight()
        }

        btnRightClick.setOnClickListener {
            currentMouseButton = 4
            updateMouseButtonHighlight()
        }

        btnCtrl.setOnClickListener {
            isCtrlPressed = !isCtrlPressed
            btnCtrl.background = createButtonBackground(isCtrlPressed)
            if (isCtrlPressed) {
                vncClient.sendKeyEvent(X11KeySyms.XK_Control_L, true)
            } else {
                vncClient.sendKeyEvent(X11KeySyms.XK_Control_L, false)
            }
        }

        btnAlt.setOnClickListener {
            isAltPressed = !isAltPressed
            btnAlt.background = createButtonBackground(isAltPressed)
            if (isAltPressed) {
                vncClient.sendKeyEvent(X11KeySyms.XK_Alt_L, true)
            } else {
                vncClient.sendKeyEvent(X11KeySyms.XK_Alt_L, false)
            }
        }

        btnEsc.setOnClickListener {
            vncClient.sendKeyEvent(X11KeySyms.XK_Escape, true)
            vncClient.sendKeyEvent(X11KeySyms.XK_Escape, false)
        }

        updateMouseButtonHighlight()
    }

    private fun createButtonBackground(pressed: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(if (pressed) 0xFF4CAF50.toInt() else 0x00000000)
        }
    }

    private fun updateMouseButtonHighlight() {
        btnLeftClick.alpha = if (currentMouseButton == 1) 1.0f else 0.5f
        btnRightClick.alpha = if (currentMouseButton == 4) 1.0f else 0.5f
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        // Add keyboard input view if not already added
        if (keyboardInputView.parent == null) {
            (window.decorView as? android.view.ViewGroup)?.addView(keyboardInputView)
        }

        keyboardInputView.requestFocus()
        imm.showSoftInput(keyboardInputView, InputMethodManager.SHOW_FORCED)
    }

    private fun sendKeyWithModifiers(keySym: Int, down: Boolean) {
        vncClient.sendKeyEvent(keySym, down)

        // Release modifiers after key press if they were held
        if (!down && (isCtrlPressed || isAltPressed)) {
            if (isCtrlPressed) {
                vncClient.sendKeyEvent(X11KeySyms.XK_Control_L, false)
                isCtrlPressed = false
                btnCtrl.background = createButtonBackground(false)
            }
            if (isAltPressed) {
                vncClient.sendKeyEvent(X11KeySyms.XK_Alt_L, false)
                isAltPressed = false
                btnAlt.background = createButtonBackground(false)
            }
        }
    }

    private fun connectToVnc(host: String, port: Int) {
        lifecycleScope.launch {
            statusText.text = "Connecting to $host:$port..."
            progressBar.visibility = View.VISIBLE

            vncClient.connectionState.collect { state ->
                when (state) {
                    VncClient.ConnectionState.CONNECTING -> {
                        statusText.text = "Connecting..."
                    }
                    VncClient.ConnectionState.CONNECTED -> {
                        statusText.visibility = View.GONE
                        progressBar.visibility = View.GONE
                        controlToolbar.visibility = View.VISIBLE
                        startFrameUpdates()
                    }
                    VncClient.ConnectionState.ERROR -> {
                        statusText.text = "Connection failed"
                        progressBar.visibility = View.GONE
                    }
                    VncClient.ConnectionState.DISCONNECTED -> {
                        statusText.text = "Disconnected"
                        progressBar.visibility = View.GONE
                        controlToolbar.visibility = View.GONE
                    }
                }
            }
        }

        lifecycleScope.launch {
            val result = vncClient.connect(host, port)
            if (result.isFailure) {
                statusText.text = "Failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private fun startFrameUpdates() {
        lifecycleScope.launch {
            vncClient.frameBuffer.collect { bitmap ->
                bitmap?.let {
                    runOnUiThread {
                        displayView.setImageBitmap(it)
                    }
                }
            }
        }

        lifecycleScope.launch {
            vncClient.processServerMessages()
        }
    }

    private fun setupTouchHandling() {
        displayView.setOnTouchListener { view, event ->
            val imageWidth = displayView.drawable?.intrinsicWidth ?: return@setOnTouchListener false
            val imageHeight = displayView.drawable?.intrinsicHeight ?: return@setOnTouchListener false

            // Calculate touch position relative to the VNC framebuffer
            val scaleX = imageWidth.toFloat() / view.width
            val scaleY = imageHeight.toFloat() / view.height

            val x = (event.x * scaleX).toInt().coerceIn(0, imageWidth - 1)
            val y = (event.y * scaleY).toInt().coerceIn(0, imageHeight - 1)

            lastTouchX = x
            lastTouchY = y

            val buttonMask = when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> currentMouseButton
                else -> 0
            }

            vncClient.sendPointerEvent(x, y, buttonMask)
            true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keySym = androidKeyToX11KeySym(keyCode, event)
        if (keySym != 0) {
            vncClient.sendKeyEvent(keySym, true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val keySym = androidKeyToX11KeySym(keyCode, event)
        if (keySym != 0) {
            vncClient.sendKeyEvent(keySym, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun androidKeyToX11KeySym(keyCode: Int, event: KeyEvent?): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> if (event?.isShiftPressed == true) X11KeySyms.XK_A else X11KeySyms.XK_a
            KeyEvent.KEYCODE_B -> if (event?.isShiftPressed == true) X11KeySyms.XK_B else X11KeySyms.XK_b
            KeyEvent.KEYCODE_C -> if (event?.isShiftPressed == true) X11KeySyms.XK_C else X11KeySyms.XK_c
            KeyEvent.KEYCODE_D -> if (event?.isShiftPressed == true) X11KeySyms.XK_D else X11KeySyms.XK_d
            KeyEvent.KEYCODE_E -> if (event?.isShiftPressed == true) X11KeySyms.XK_E else X11KeySyms.XK_e
            KeyEvent.KEYCODE_F -> if (event?.isShiftPressed == true) X11KeySyms.XK_F else X11KeySyms.XK_f
            KeyEvent.KEYCODE_G -> if (event?.isShiftPressed == true) X11KeySyms.XK_G else X11KeySyms.XK_g
            KeyEvent.KEYCODE_H -> if (event?.isShiftPressed == true) X11KeySyms.XK_H else X11KeySyms.XK_h
            KeyEvent.KEYCODE_I -> if (event?.isShiftPressed == true) X11KeySyms.XK_I else X11KeySyms.XK_i
            KeyEvent.KEYCODE_J -> if (event?.isShiftPressed == true) X11KeySyms.XK_J else X11KeySyms.XK_j
            KeyEvent.KEYCODE_K -> if (event?.isShiftPressed == true) X11KeySyms.XK_K else X11KeySyms.XK_k
            KeyEvent.KEYCODE_L -> if (event?.isShiftPressed == true) X11KeySyms.XK_L else X11KeySyms.XK_l
            KeyEvent.KEYCODE_M -> if (event?.isShiftPressed == true) X11KeySyms.XK_M else X11KeySyms.XK_m
            KeyEvent.KEYCODE_N -> if (event?.isShiftPressed == true) X11KeySyms.XK_N else X11KeySyms.XK_n
            KeyEvent.KEYCODE_O -> if (event?.isShiftPressed == true) X11KeySyms.XK_O else X11KeySyms.XK_o
            KeyEvent.KEYCODE_P -> if (event?.isShiftPressed == true) X11KeySyms.XK_P else X11KeySyms.XK_p
            KeyEvent.KEYCODE_Q -> if (event?.isShiftPressed == true) X11KeySyms.XK_Q else X11KeySyms.XK_q
            KeyEvent.KEYCODE_R -> if (event?.isShiftPressed == true) X11KeySyms.XK_R else X11KeySyms.XK_r
            KeyEvent.KEYCODE_S -> if (event?.isShiftPressed == true) X11KeySyms.XK_S else X11KeySyms.XK_s
            KeyEvent.KEYCODE_T -> if (event?.isShiftPressed == true) X11KeySyms.XK_T else X11KeySyms.XK_t
            KeyEvent.KEYCODE_U -> if (event?.isShiftPressed == true) X11KeySyms.XK_U else X11KeySyms.XK_u
            KeyEvent.KEYCODE_V -> if (event?.isShiftPressed == true) X11KeySyms.XK_V else X11KeySyms.XK_v
            KeyEvent.KEYCODE_W -> if (event?.isShiftPressed == true) X11KeySyms.XK_W else X11KeySyms.XK_w
            KeyEvent.KEYCODE_X -> if (event?.isShiftPressed == true) X11KeySyms.XK_X else X11KeySyms.XK_x
            KeyEvent.KEYCODE_Y -> if (event?.isShiftPressed == true) X11KeySyms.XK_Y else X11KeySyms.XK_y
            KeyEvent.KEYCODE_Z -> if (event?.isShiftPressed == true) X11KeySyms.XK_Z else X11KeySyms.XK_z
            KeyEvent.KEYCODE_0 -> X11KeySyms.XK_0
            KeyEvent.KEYCODE_1 -> X11KeySyms.XK_1
            KeyEvent.KEYCODE_2 -> X11KeySyms.XK_2
            KeyEvent.KEYCODE_3 -> X11KeySyms.XK_3
            KeyEvent.KEYCODE_4 -> X11KeySyms.XK_4
            KeyEvent.KEYCODE_5 -> X11KeySyms.XK_5
            KeyEvent.KEYCODE_6 -> X11KeySyms.XK_6
            KeyEvent.KEYCODE_7 -> X11KeySyms.XK_7
            KeyEvent.KEYCODE_8 -> X11KeySyms.XK_8
            KeyEvent.KEYCODE_9 -> X11KeySyms.XK_9
            KeyEvent.KEYCODE_SPACE -> X11KeySyms.XK_space
            KeyEvent.KEYCODE_ENTER -> X11KeySyms.XK_Return
            KeyEvent.KEYCODE_DEL -> X11KeySyms.XK_BackSpace
            KeyEvent.KEYCODE_FORWARD_DEL -> X11KeySyms.XK_Delete
            KeyEvent.KEYCODE_TAB -> X11KeySyms.XK_Tab
            KeyEvent.KEYCODE_ESCAPE -> X11KeySyms.XK_Escape
            KeyEvent.KEYCODE_DPAD_UP -> X11KeySyms.XK_Up
            KeyEvent.KEYCODE_DPAD_DOWN -> X11KeySyms.XK_Down
            KeyEvent.KEYCODE_DPAD_LEFT -> X11KeySyms.XK_Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> X11KeySyms.XK_Right
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> X11KeySyms.XK_Shift_L
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> X11KeySyms.XK_Control_L
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> X11KeySyms.XK_Alt_L
            KeyEvent.KEYCODE_PERIOD -> X11KeySyms.XK_period
            KeyEvent.KEYCODE_COMMA -> X11KeySyms.XK_comma
            KeyEvent.KEYCODE_SLASH -> X11KeySyms.XK_slash
            KeyEvent.KEYCODE_MINUS -> X11KeySyms.XK_minus
            KeyEvent.KEYCODE_EQUALS -> X11KeySyms.XK_equal
            KeyEvent.KEYCODE_LEFT_BRACKET -> X11KeySyms.XK_bracketleft
            KeyEvent.KEYCODE_RIGHT_BRACKET -> X11KeySyms.XK_bracketright
            KeyEvent.KEYCODE_BACKSLASH -> X11KeySyms.XK_backslash
            KeyEvent.KEYCODE_SEMICOLON -> X11KeySyms.XK_semicolon
            KeyEvent.KEYCODE_APOSTROPHE -> X11KeySyms.XK_apostrophe
            KeyEvent.KEYCODE_GRAVE -> X11KeySyms.XK_grave
            KeyEvent.KEYCODE_F1 -> X11KeySyms.XK_F1
            KeyEvent.KEYCODE_F2 -> X11KeySyms.XK_F2
            KeyEvent.KEYCODE_F3 -> X11KeySyms.XK_F3
            KeyEvent.KEYCODE_F4 -> X11KeySyms.XK_F4
            KeyEvent.KEYCODE_F5 -> X11KeySyms.XK_F5
            KeyEvent.KEYCODE_F6 -> X11KeySyms.XK_F6
            KeyEvent.KEYCODE_F7 -> X11KeySyms.XK_F7
            KeyEvent.KEYCODE_F8 -> X11KeySyms.XK_F8
            KeyEvent.KEYCODE_F9 -> X11KeySyms.XK_F9
            KeyEvent.KEYCODE_F10 -> X11KeySyms.XK_F10
            KeyEvent.KEYCODE_F11 -> X11KeySyms.XK_F11
            KeyEvent.KEYCODE_F12 -> X11KeySyms.XK_F12
            KeyEvent.KEYCODE_INSERT -> X11KeySyms.XK_Insert
            KeyEvent.KEYCODE_MOVE_HOME -> X11KeySyms.XK_Home
            KeyEvent.KEYCODE_MOVE_END -> X11KeySyms.XK_End
            KeyEvent.KEYCODE_PAGE_UP -> X11KeySyms.XK_Page_Up
            KeyEvent.KEYCODE_PAGE_DOWN -> X11KeySyms.XK_Page_Down
            else -> 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vncClient.disconnect()
    }
}

// Simple drawable that renders text for button labels
class TextDrawable(context: Context, private val text: String) : android.graphics.drawable.Drawable() {
    private val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 12f * context.resources.displayMetrics.density
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun draw(canvas: android.graphics.Canvas) {
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, x, y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

// Custom view for capturing keyboard input
class KeyboardInputView(context: Context) : View(context) {
    private var keyListener: ((Int, Boolean) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // Make it visible but tiny
        minimumWidth = 1
        minimumHeight = 1
    }

    fun setOnKeyListener(listener: (Int, Boolean) -> Unit) {
        keyListener = listener
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        outAttrs.initialSelStart = 0
        outAttrs.initialSelEnd = 0
        return VncInputConnection(this, keyListener)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val keySym = keyCodeToX11(keyCode, event)
        if (keySym != 0) {
            keyListener?.invoke(keySym, true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val keySym = keyCodeToX11(keyCode, event)
        if (keySym != 0) {
            keyListener?.invoke(keySym, false)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun keyCodeToX11(keyCode: Int, event: KeyEvent?): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> X11KeySyms.XK_BackSpace
            KeyEvent.KEYCODE_ENTER -> X11KeySyms.XK_Return
            KeyEvent.KEYCODE_SPACE -> X11KeySyms.XK_space
            KeyEvent.KEYCODE_TAB -> X11KeySyms.XK_Tab
            else -> 0
        }
    }
}

// Input connection for handling soft keyboard input
class VncInputConnection(
    private val view: View,
    private val keyListener: ((Int, Boolean) -> Unit)?
) : android.view.inputmethod.BaseInputConnection(view, false) {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        text?.forEach { char ->
            val keySym = charToX11KeySym(char)
            if (keySym != 0) {
                keyListener?.invoke(keySym, true)
                keyListener?.invoke(keySym, false)
            }
        }
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        // Commit composing text immediately
        return commitText(text, newCursorPosition)
    }

    override fun finishComposingText(): Boolean {
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        repeat(beforeLength) {
            keyListener?.invoke(X11KeySyms.XK_BackSpace, true)
            keyListener?.invoke(X11KeySyms.XK_BackSpace, false)
        }
        repeat(afterLength) {
            keyListener?.invoke(X11KeySyms.XK_Delete, true)
            keyListener?.invoke(X11KeySyms.XK_Delete, false)
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        event?.let {
            val keySym = when (it.keyCode) {
                KeyEvent.KEYCODE_DEL -> X11KeySyms.XK_BackSpace
                KeyEvent.KEYCODE_FORWARD_DEL -> X11KeySyms.XK_Delete
                KeyEvent.KEYCODE_ENTER -> X11KeySyms.XK_Return
                KeyEvent.KEYCODE_TAB -> X11KeySyms.XK_Tab
                KeyEvent.KEYCODE_SPACE -> X11KeySyms.XK_space
                KeyEvent.KEYCODE_DPAD_UP -> X11KeySyms.XK_Up
                KeyEvent.KEYCODE_DPAD_DOWN -> X11KeySyms.XK_Down
                KeyEvent.KEYCODE_DPAD_LEFT -> X11KeySyms.XK_Left
                KeyEvent.KEYCODE_DPAD_RIGHT -> X11KeySyms.XK_Right
                else -> 0
            }
            if (keySym != 0) {
                val down = it.action == KeyEvent.ACTION_DOWN
                keyListener?.invoke(keySym, down)
                return true
            }
        }
        return super.sendKeyEvent(event)
    }

    private fun charToX11KeySym(char: Char): Int {
        return when {
            char in 'a'..'z' -> X11KeySyms.XK_a + (char - 'a')
            char in 'A'..'Z' -> X11KeySyms.XK_A + (char - 'A')
            char in '0'..'9' -> X11KeySyms.XK_0 + (char - '0')
            char == ' ' -> X11KeySyms.XK_space
            char == '\n' -> X11KeySyms.XK_Return
            char == '\t' -> X11KeySyms.XK_Tab
            char == '.' -> X11KeySyms.XK_period
            char == ',' -> X11KeySyms.XK_comma
            char == '/' -> X11KeySyms.XK_slash
            char == '-' -> X11KeySyms.XK_minus
            char == '=' -> X11KeySyms.XK_equal
            char == '[' -> X11KeySyms.XK_bracketleft
            char == ']' -> X11KeySyms.XK_bracketright
            char == '\\' -> X11KeySyms.XK_backslash
            char == ';' -> X11KeySyms.XK_semicolon
            char == '\'' -> X11KeySyms.XK_apostrophe
            char == '`' -> X11KeySyms.XK_grave
            char == '!' -> X11KeySyms.XK_exclam
            char == '@' -> X11KeySyms.XK_at
            char == '#' -> X11KeySyms.XK_numbersign
            char == '$' -> X11KeySyms.XK_dollar
            char == '%' -> X11KeySyms.XK_percent
            char == '^' -> X11KeySyms.XK_asciicircum
            char == '&' -> X11KeySyms.XK_ampersand
            char == '*' -> X11KeySyms.XK_asterisk
            char == '(' -> X11KeySyms.XK_parenleft
            char == ')' -> X11KeySyms.XK_parenright
            char == '_' -> X11KeySyms.XK_underscore
            char == '+' -> X11KeySyms.XK_plus
            char == '{' -> X11KeySyms.XK_braceleft
            char == '}' -> X11KeySyms.XK_braceright
            char == '|' -> X11KeySyms.XK_bar
            char == ':' -> X11KeySyms.XK_colon
            char == '"' -> X11KeySyms.XK_quotedbl
            char == '<' -> X11KeySyms.XK_less
            char == '>' -> X11KeySyms.XK_greater
            char == '?' -> X11KeySyms.XK_question
            char == '~' -> X11KeySyms.XK_asciitilde
            else -> char.code
        }
    }
}

// X11 key symbols for VNC protocol
object X11KeySyms {
    const val XK_BackSpace = 0xff08
    const val XK_Tab = 0xff09
    const val XK_Return = 0xff0d
    const val XK_Escape = 0xff1b
    const val XK_Delete = 0xffff
    const val XK_Home = 0xff50
    const val XK_Left = 0xff51
    const val XK_Up = 0xff52
    const val XK_Right = 0xff53
    const val XK_Down = 0xff54
    const val XK_Page_Up = 0xff55
    const val XK_Page_Down = 0xff56
    const val XK_End = 0xff57
    const val XK_Insert = 0xff63
    const val XK_F1 = 0xffbe
    const val XK_F2 = 0xffbf
    const val XK_F3 = 0xffc0
    const val XK_F4 = 0xffc1
    const val XK_F5 = 0xffc2
    const val XK_F6 = 0xffc3
    const val XK_F7 = 0xffc4
    const val XK_F8 = 0xffc5
    const val XK_F9 = 0xffc6
    const val XK_F10 = 0xffc7
    const val XK_F11 = 0xffc8
    const val XK_F12 = 0xffc9
    const val XK_Shift_L = 0xffe1
    const val XK_Control_L = 0xffe3
    const val XK_Alt_L = 0xffe9
    const val XK_space = 0x0020
    const val XK_exclam = 0x0021
    const val XK_quotedbl = 0x0022
    const val XK_numbersign = 0x0023
    const val XK_dollar = 0x0024
    const val XK_percent = 0x0025
    const val XK_ampersand = 0x0026
    const val XK_apostrophe = 0x0027
    const val XK_parenleft = 0x0028
    const val XK_parenright = 0x0029
    const val XK_asterisk = 0x002a
    const val XK_plus = 0x002b
    const val XK_comma = 0x002c
    const val XK_minus = 0x002d
    const val XK_period = 0x002e
    const val XK_slash = 0x002f
    const val XK_0 = 0x0030
    const val XK_1 = 0x0031
    const val XK_2 = 0x0032
    const val XK_3 = 0x0033
    const val XK_4 = 0x0034
    const val XK_5 = 0x0035
    const val XK_6 = 0x0036
    const val XK_7 = 0x0037
    const val XK_8 = 0x0038
    const val XK_9 = 0x0039
    const val XK_colon = 0x003a
    const val XK_semicolon = 0x003b
    const val XK_less = 0x003c
    const val XK_equal = 0x003d
    const val XK_greater = 0x003e
    const val XK_question = 0x003f
    const val XK_at = 0x0040
    const val XK_A = 0x0041
    const val XK_B = 0x0042
    const val XK_C = 0x0043
    const val XK_D = 0x0044
    const val XK_E = 0x0045
    const val XK_F = 0x0046
    const val XK_G = 0x0047
    const val XK_H = 0x0048
    const val XK_I = 0x0049
    const val XK_J = 0x004a
    const val XK_K = 0x004b
    const val XK_L = 0x004c
    const val XK_M = 0x004d
    const val XK_N = 0x004e
    const val XK_O = 0x004f
    const val XK_P = 0x0050
    const val XK_Q = 0x0051
    const val XK_R = 0x0052
    const val XK_S = 0x0053
    const val XK_T = 0x0054
    const val XK_U = 0x0055
    const val XK_V = 0x0056
    const val XK_W = 0x0057
    const val XK_X = 0x0058
    const val XK_Y = 0x0059
    const val XK_Z = 0x005a
    const val XK_bracketleft = 0x005b
    const val XK_backslash = 0x005c
    const val XK_bracketright = 0x005d
    const val XK_asciicircum = 0x005e
    const val XK_underscore = 0x005f
    const val XK_grave = 0x0060
    const val XK_a = 0x0061
    const val XK_b = 0x0062
    const val XK_c = 0x0063
    const val XK_d = 0x0064
    const val XK_e = 0x0065
    const val XK_f = 0x0066
    const val XK_g = 0x0067
    const val XK_h = 0x0068
    const val XK_i = 0x0069
    const val XK_j = 0x006a
    const val XK_k = 0x006b
    const val XK_l = 0x006c
    const val XK_m = 0x006d
    const val XK_n = 0x006e
    const val XK_o = 0x006f
    const val XK_p = 0x0070
    const val XK_q = 0x0071
    const val XK_r = 0x0072
    const val XK_s = 0x0073
    const val XK_t = 0x0074
    const val XK_u = 0x0075
    const val XK_v = 0x0076
    const val XK_w = 0x0077
    const val XK_x = 0x0078
    const val XK_y = 0x0079
    const val XK_z = 0x007a
    const val XK_braceleft = 0x007b
    const val XK_bar = 0x007c
    const val XK_braceright = 0x007d
    const val XK_asciitilde = 0x007e
}
