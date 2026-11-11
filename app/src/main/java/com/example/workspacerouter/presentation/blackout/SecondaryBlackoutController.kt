package com.example.workspacerouter.presentation.blackout

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class SecondaryBlackoutController(context: Context) {
    private val appContext = context.applicationContext
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)
    private val presentations = mutableMapOf<Int, BlackPresentation>()

    fun show(displayId: Int) {
        val display = requireNotNull(displayManager.getDisplay(displayId)) {
            "Display $displayId is no longer connected"
        }
        require(display.isValid) { "Display $displayId is not valid" }

        presentations.remove(displayId)?.dismiss()
        presentations[displayId] = BlackPresentation(appContext, display).also {
            it.setOnDismissListener { presentations.remove(displayId) }
            it.show()
        }
    }

    fun dismiss(displayId: Int) {
        presentations.remove(displayId)?.dismiss()
    }

    fun dismissAll() {
        presentations.values.toList().forEach(Presentation::dismiss)
        presentations.clear()
    }
}

private class BlackPresentation(
    context: Context,
    display: Display,
) : Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowInsetsControllerCompat(this, decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        setContentView(
            FrameLayout(context).apply {
                setBackgroundColor(Color.BLACK)
            },
        )
    }
}
