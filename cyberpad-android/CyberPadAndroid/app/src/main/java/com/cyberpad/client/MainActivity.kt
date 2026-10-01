package com.cyberpad.client

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.cyberpad.client.input.GamepadState
import com.cyberpad.client.network.ControllerSession
import com.cyberpad.client.network.UdpClientManager
import com.cyberpad.client.ui.screens.ControllerScreen
import com.cyberpad.client.ui.screens.QrScannerScreen
import com.cyberpad.client.ui.theme.CyberpunkTheme

class MainActivity : ComponentActivity() {

    private val gamepad = GamepadState()
    private lateinit var udp: UdpClientManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        udp = UdpClientManager(applicationContext, gamepad)

        setContent {
            CyberpunkTheme {
                val session = remember { mutableStateOf<ControllerSession?>(null) }
                val current = session.value
                if (current == null) {
                    QrScannerScreen(
                        onConnect = { info, playerId ->
                            session.value = ControllerSession(info, playerId)
                        }
                    )
                } else {
                    ControllerScreen(
                        session = current,
                        gamepad = gamepad,
                        udp = udp,
                        onDisconnect = { session.value = null }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        udp.shutdown()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
