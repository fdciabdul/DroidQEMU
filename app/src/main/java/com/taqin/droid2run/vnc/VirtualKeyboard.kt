package com.taqin.droid2run.vnc

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Space

/**
 * Custom virtual keyboard for VNC viewer
 */
class VirtualKeyboard(context: Context) : LinearLayout(context) {

    private var keyListener: ((Int, Boolean) -> Unit)? = null
    private var shiftPressed = false
    private var ctrlPressed = false
    private var altPressed = false
    private var capsLock = false

    // Key colors
    private val keyBg = Color.parseColor("#2a2a3d")
    private val keyBgPressed = Color.parseColor("#4a4a6d")
    private val keyBgModifier = Color.parseColor("#3d3d5c")
    private val keyBgModifierActive = Color.parseColor("#6a5acd")
    private val keyText = Color.WHITE
    private val keyboardBg = Color.parseColor("#1a1a2e")

    private val modifierButtons = mutableMapOf<String, Button>()

    init {
        orientation = VERTICAL
        setBackgroundColor(keyboardBg)
        setPadding(4.dp, 8.dp, 4.dp, 8.dp)
        buildKeyboard()
    }

    fun setOnKeyListener(listener: (keySym: Int, down: Boolean) -> Unit) {
        keyListener = listener
    }

    private fun sendKey(keySym: Int, down: Boolean) {
        keyListener?.invoke(keySym, down)
    }

    private fun buildKeyboard() {
        // Function keys row (scrollable)
        addView(createFunctionRow())

        // Number row
        addView(createRow(listOf(
            Key("`", "~", X11KeySyms.XK_grave, X11KeySyms.XK_asciitilde),
            Key("1", "!", X11KeySyms.XK_1, X11KeySyms.XK_exclam),
            Key("2", "@", X11KeySyms.XK_2, X11KeySyms.XK_at),
            Key("3", "#", X11KeySyms.XK_3, X11KeySyms.XK_numbersign),
            Key("4", "$", X11KeySyms.XK_4, X11KeySyms.XK_dollar),
            Key("5", "%", X11KeySyms.XK_5, X11KeySyms.XK_percent),
            Key("6", "^", X11KeySyms.XK_6, X11KeySyms.XK_asciicircum),
            Key("7", "&", X11KeySyms.XK_7, X11KeySyms.XK_ampersand),
            Key("8", "*", X11KeySyms.XK_8, X11KeySyms.XK_asterisk),
            Key("9", "(", X11KeySyms.XK_9, X11KeySyms.XK_parenleft),
            Key("0", ")", X11KeySyms.XK_0, X11KeySyms.XK_parenright),
            Key("-", "_", X11KeySyms.XK_minus, X11KeySyms.XK_underscore),
            Key("=", "+", X11KeySyms.XK_equal, X11KeySyms.XK_plus),
            Key("⌫", "⌫", X11KeySyms.XK_BackSpace, X11KeySyms.XK_BackSpace, 1.5f)
        )))

        // QWERTY row
        addView(createRow(listOf(
            Key("Tab", "Tab", X11KeySyms.XK_Tab, X11KeySyms.XK_Tab, 1.2f),
            Key("q", "Q", X11KeySyms.XK_q, X11KeySyms.XK_Q),
            Key("w", "W", X11KeySyms.XK_w, X11KeySyms.XK_W),
            Key("e", "E", X11KeySyms.XK_e, X11KeySyms.XK_E),
            Key("r", "R", X11KeySyms.XK_r, X11KeySyms.XK_R),
            Key("t", "T", X11KeySyms.XK_t, X11KeySyms.XK_T),
            Key("y", "Y", X11KeySyms.XK_y, X11KeySyms.XK_Y),
            Key("u", "U", X11KeySyms.XK_u, X11KeySyms.XK_U),
            Key("i", "I", X11KeySyms.XK_i, X11KeySyms.XK_I),
            Key("o", "O", X11KeySyms.XK_o, X11KeySyms.XK_O),
            Key("p", "P", X11KeySyms.XK_p, X11KeySyms.XK_P),
            Key("[", "{", X11KeySyms.XK_bracketleft, X11KeySyms.XK_braceleft),
            Key("]", "}", X11KeySyms.XK_bracketright, X11KeySyms.XK_braceright),
            Key("\\", "|", X11KeySyms.XK_backslash, X11KeySyms.XK_bar)
        )))

        // ASDF row
        addView(createRow(listOf(
            Key("Caps", "Caps", X11KeySyms.XK_Caps_Lock, X11KeySyms.XK_Caps_Lock, 1.5f, isModifier = true, modifierName = "caps"),
            Key("a", "A", X11KeySyms.XK_a, X11KeySyms.XK_A),
            Key("s", "S", X11KeySyms.XK_s, X11KeySyms.XK_S),
            Key("d", "D", X11KeySyms.XK_d, X11KeySyms.XK_D),
            Key("f", "F", X11KeySyms.XK_f, X11KeySyms.XK_F),
            Key("g", "G", X11KeySyms.XK_g, X11KeySyms.XK_G),
            Key("h", "H", X11KeySyms.XK_h, X11KeySyms.XK_H),
            Key("j", "J", X11KeySyms.XK_j, X11KeySyms.XK_J),
            Key("k", "K", X11KeySyms.XK_k, X11KeySyms.XK_K),
            Key("l", "L", X11KeySyms.XK_l, X11KeySyms.XK_L),
            Key(";", ":", X11KeySyms.XK_semicolon, X11KeySyms.XK_colon),
            Key("'", "\"", X11KeySyms.XK_apostrophe, X11KeySyms.XK_quotedbl),
            Key("Enter", "Enter", X11KeySyms.XK_Return, X11KeySyms.XK_Return, 1.5f)
        )))

        // ZXCV row
        addView(createRow(listOf(
            Key("Shift", "Shift", X11KeySyms.XK_Shift_L, X11KeySyms.XK_Shift_L, 2f, isModifier = true, modifierName = "shift"),
            Key("z", "Z", X11KeySyms.XK_z, X11KeySyms.XK_Z),
            Key("x", "X", X11KeySyms.XK_x, X11KeySyms.XK_X),
            Key("c", "C", X11KeySyms.XK_c, X11KeySyms.XK_C),
            Key("v", "V", X11KeySyms.XK_v, X11KeySyms.XK_V),
            Key("b", "B", X11KeySyms.XK_b, X11KeySyms.XK_B),
            Key("n", "N", X11KeySyms.XK_n, X11KeySyms.XK_N),
            Key("m", "M", X11KeySyms.XK_m, X11KeySyms.XK_M),
            Key(",", "<", X11KeySyms.XK_comma, X11KeySyms.XK_less),
            Key(".", ">", X11KeySyms.XK_period, X11KeySyms.XK_greater),
            Key("/", "?", X11KeySyms.XK_slash, X11KeySyms.XK_question),
            Key("Shift", "Shift", X11KeySyms.XK_Shift_R, X11KeySyms.XK_Shift_R, 2f, isModifier = true, modifierName = "shift")
        )))

        // Bottom row
        addView(createRow(listOf(
            Key("Ctrl", "Ctrl", X11KeySyms.XK_Control_L, X11KeySyms.XK_Control_L, 1.2f, isModifier = true, modifierName = "ctrl"),
            Key("Alt", "Alt", X11KeySyms.XK_Alt_L, X11KeySyms.XK_Alt_L, 1.2f, isModifier = true, modifierName = "alt"),
            Key("Space", "Space", X11KeySyms.XK_space, X11KeySyms.XK_space, 5f),
            Key("Alt", "Alt", X11KeySyms.XK_Alt_R, X11KeySyms.XK_Alt_R, 1.2f, isModifier = true, modifierName = "alt"),
            Key("←", "←", X11KeySyms.XK_Left, X11KeySyms.XK_Left),
            Key("↑", "↑", X11KeySyms.XK_Up, X11KeySyms.XK_Up),
            Key("↓", "↓", X11KeySyms.XK_Down, X11KeySyms.XK_Down),
            Key("→", "→", X11KeySyms.XK_Right, X11KeySyms.XK_Right)
        )))
    }

    private fun createFunctionRow(): View {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, 2.dp, 0, 2.dp)
        }

        val funcKeys = listOf(
            Key("Esc", "Esc", X11KeySyms.XK_Escape, X11KeySyms.XK_Escape),
            Key("F1", "F1", X11KeySyms.XK_F1, X11KeySyms.XK_F1),
            Key("F2", "F2", X11KeySyms.XK_F2, X11KeySyms.XK_F2),
            Key("F3", "F3", X11KeySyms.XK_F3, X11KeySyms.XK_F3),
            Key("F4", "F4", X11KeySyms.XK_F4, X11KeySyms.XK_F4),
            Key("F5", "F5", X11KeySyms.XK_F5, X11KeySyms.XK_F5),
            Key("F6", "F6", X11KeySyms.XK_F6, X11KeySyms.XK_F6),
            Key("F7", "F7", X11KeySyms.XK_F7, X11KeySyms.XK_F7),
            Key("F8", "F8", X11KeySyms.XK_F8, X11KeySyms.XK_F8),
            Key("F9", "F9", X11KeySyms.XK_F9, X11KeySyms.XK_F9),
            Key("F10", "F10", X11KeySyms.XK_F10, X11KeySyms.XK_F10),
            Key("F11", "F11", X11KeySyms.XK_F11, X11KeySyms.XK_F11),
            Key("F12", "F12", X11KeySyms.XK_F12, X11KeySyms.XK_F12),
            Key("Ins", "Ins", X11KeySyms.XK_Insert, X11KeySyms.XK_Insert),
            Key("Del", "Del", X11KeySyms.XK_Delete, X11KeySyms.XK_Delete),
            Key("Home", "Home", X11KeySyms.XK_Home, X11KeySyms.XK_Home),
            Key("End", "End", X11KeySyms.XK_End, X11KeySyms.XK_End),
            Key("PgUp", "PgUp", X11KeySyms.XK_Page_Up, X11KeySyms.XK_Page_Up),
            Key("PgDn", "PgDn", X11KeySyms.XK_Page_Down, X11KeySyms.XK_Page_Down)
        )

        funcKeys.forEach { key ->
            row.addView(createKeyButton(key, 48.dp))
        }

        scroll.addView(row)
        return scroll
    }

    private fun createRow(keys: List<Key>): LinearLayout {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 2.dp, 0, 2.dp)
            }

            keys.forEach { key ->
                addView(createKeyButton(key))
            }
        }
    }

    private fun createKeyButton(key: Key, fixedWidth: Int? = null): Button {
        return Button(context).apply {
            text = if (shiftPressed || capsLock) key.shiftLabel else key.label
            setTextColor(keyText)
            textSize = if (key.label.length > 2) 10f else 14f
            isAllCaps = false
            setPadding(4.dp, 8.dp, 4.dp, 8.dp)
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0

            background = createKeyBackground(if (key.isModifier) keyBgModifier else keyBg)

            layoutParams = if (fixedWidth != null) {
                LayoutParams(fixedWidth, 40.dp).apply {
                    setMargins(2.dp, 0, 2.dp, 0)
                }
            } else {
                LayoutParams(0, 40.dp, key.weight).apply {
                    setMargins(2.dp, 0, 2.dp, 0)
                }
            }

            if (key.isModifier && key.modifierName != null) {
                modifierButtons[key.modifierName] = this
            }

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (key.isModifier) {
                            handleModifierPress(key)
                        } else {
                            val keySym = if (shiftPressed || capsLock) key.shiftKeySym else key.keySym
                            sendKey(keySym, true)
                        }
                        background = createKeyBackground(keyBgPressed)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!key.isModifier) {
                            val keySym = if (shiftPressed || capsLock) key.shiftKeySym else key.keySym
                            sendKey(keySym, false)
                            // Release shift after key press (unless caps lock)
                            if (shiftPressed && !capsLock) {
                                releaseShift()
                            }
                        }
                        background = createKeyBackground(
                            when {
                                key.isModifier && isModifierActive(key.modifierName) -> keyBgModifierActive
                                key.isModifier -> keyBgModifier
                                else -> keyBg
                            }
                        )
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun handleModifierPress(key: Key) {
        when (key.modifierName) {
            "shift" -> {
                shiftPressed = !shiftPressed
                sendKey(key.keySym, shiftPressed)
                updateModifierButtons()
                updateKeyLabels()
            }
            "ctrl" -> {
                ctrlPressed = !ctrlPressed
                sendKey(key.keySym, ctrlPressed)
                updateModifierButtons()
            }
            "alt" -> {
                altPressed = !altPressed
                sendKey(key.keySym, altPressed)
                updateModifierButtons()
            }
            "caps" -> {
                capsLock = !capsLock
                sendKey(key.keySym, true)
                sendKey(key.keySym, false)
                updateModifierButtons()
                updateKeyLabels()
            }
        }
    }

    private fun releaseShift() {
        if (shiftPressed) {
            shiftPressed = false
            sendKey(X11KeySyms.XK_Shift_L, false)
            updateModifierButtons()
            updateKeyLabels()
        }
    }

    private fun isModifierActive(name: String?): Boolean {
        return when (name) {
            "shift" -> shiftPressed
            "ctrl" -> ctrlPressed
            "alt" -> altPressed
            "caps" -> capsLock
            else -> false
        }
    }

    private fun updateModifierButtons() {
        modifierButtons.forEach { (name, button) ->
            button.background = createKeyBackground(
                if (isModifierActive(name)) keyBgModifierActive else keyBgModifier
            )
        }
    }

    private fun updateKeyLabels() {
        // This would update all key labels - simplified for now
    }

    private fun createKeyBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6f * resources.displayMetrics.density
            setColor(color)
        }
    }

    private val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()

    data class Key(
        val label: String,
        val shiftLabel: String,
        val keySym: Int,
        val shiftKeySym: Int,
        val weight: Float = 1f,
        val isModifier: Boolean = false,
        val modifierName: String? = null
    )
}

