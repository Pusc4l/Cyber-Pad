package com.cyberpad.client.input

import kotlin.math.roundToInt

/** Bitmask Buttons_Low (byte 0x01 pada frame 12-byte). */
object ButtonsLow {
    const val A = 0x01
    const val B = 0x02
    const val X = 0x04
    const val Y = 0x08
    const val LB = 0x10
    const val RB = 0x20
    const val BACK = 0x40
    const val START = 0x80
}

/** Bitmask Buttons_High (byte 0x02 pada frame 12-byte). */
object ButtonsHigh {
    const val L3 = 0x01
    const val R3 = 0x02
    const val DPAD_UP = 0x04
    const val DPAD_DOWN = 0x08
    const val DPAD_LEFT = 0x10
    const val DPAD_RIGHT = 0x20
    const val DPAD_MASK = DPAD_UP or DPAD_DOWN or DPAD_LEFT or DPAD_RIGHT
}

/**
 * State gamepad bersama. Ditulis HANYA oleh UI thread (pointerInput), dibaca oleh thread
 * pengirim UDP (Dispatchers.IO). Semua field @Volatile sehingga pembaca selalu melihat nilai
 * terbaru tanpa lock dan tanpa alokasi objek.
 *
 * Konvensi sumbu mengikuti server (XInput): stick ke ATAS = Y positif.
 */
class GamepadState {
    @Volatile var buttonsLow: Int = 0
        private set
    @Volatile var buttonsHigh: Int = 0
        private set
    @Volatile var thumbLX: Int = 0
        private set
    @Volatile var thumbLY: Int = 0
        private set
    @Volatile var thumbRX: Int = 0
        private set
    @Volatile var thumbRY: Int = 0
        private set
    @Volatile var triggerL: Int = 0
        private set
    @Volatile var triggerR: Int = 0
        private set

    fun setLow(mask: Int, pressed: Boolean) {
        buttonsLow = if (pressed) buttonsLow or mask else buttonsLow and mask.inv()
    }

    fun setHigh(mask: Int, pressed: Boolean) {
        buttonsHigh = if (pressed) buttonsHigh or mask else buttonsHigh and mask.inv()
    }

    fun setDpad(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        var v = buttonsHigh and ButtonsHigh.DPAD_MASK.inv()
        if (up) v = v or ButtonsHigh.DPAD_UP
        if (down) v = v or ButtonsHigh.DPAD_DOWN
        if (left) v = v or ButtonsHigh.DPAD_LEFT
        if (right) v = v or ButtonsHigh.DPAD_RIGHT
        buttonsHigh = v
    }

    /** x, y dalam -1..1 (y positif = atas). */
    fun setLeftStick(x: Float, y: Float) {
        thumbLX = toAxis(x)
        thumbLY = toAxis(y)
    }

    fun setRightStick(x: Float, y: Float) {
        thumbRX = toAxis(x)
        thumbRY = toAxis(y)
    }

    /** Level 0..15 (4-bit). */
    fun setTriggerLeft(level: Int) {
        triggerL = level.coerceIn(0, 15)
    }

    fun setTriggerRight(level: Int) {
        triggerR = level.coerceIn(0, 15)
    }

    fun reset() {
        buttonsLow = 0
        buttonsHigh = 0
        thumbLX = 0
        thumbLY = 0
        thumbRX = 0
        thumbRY = 0
        triggerL = 0
        triggerR = 0
    }

    private fun toAxis(v: Float): Int = (v.coerceIn(-1f, 1f) * 32767f).roundToInt()
}
