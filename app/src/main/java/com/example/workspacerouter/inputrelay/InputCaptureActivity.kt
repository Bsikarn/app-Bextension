package com.example.workspacerouter.inputrelay

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.WindowManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Owns the focused window used for pointer capture on Display 0.
 *
 * HyperOS moves the globally focused display to the target display while an accessibility
 * gesture is dispatched. Bringing this activity forward after each gesture gives pointer
 * capture back to Display 0 without requiring another physical mouse click.
 */
class InputCaptureActivity : Activity() {
    private lateinit var captureView: InputCaptureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        captureView = InputCaptureView(this)
        setContentView(captureView)
        InputCaptureBridge.attach(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureView.scheduleCapture()
    }

    override fun onResume() {
        super.onResume()
        captureView.scheduleCapture()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) captureView.scheduleCapture()
    }

    override fun onDestroy() {
        captureView.releasePointerCapture()
        InputCaptureBridge.detach(this)
        super.onDestroy()
    }

    internal fun reclaimFocus() {
        if (isFinishing || isDestroyed) return
        if (!InputCaptureBridge.pointerCaptureEnabled) return
        if (hasWindowFocus()) {
            captureView.scheduleCapture()
        } else {
            launch(this)
        }
    }

    internal fun finishCapture() {
        captureView.releasePointerCapture()
        finishAndRemoveTask()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, InputCaptureActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            }
            val options = ActivityOptions.makeBasic().apply {
                launchDisplayId = Display.DEFAULT_DISPLAY
            }
            context.startActivity(intent, options.toBundle())
        }
    }
}

private class InputCaptureView(context: Context) : View(context) {
    private var captureRequested = false
    private var capturedOnce = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.TRANSPARENT)
        pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
        setOnCapturedPointerListener { _, event -> InputCaptureBridge.dispatchMotion(event) }
    }

    override fun onResolvePointerIcon(event: MotionEvent?, pointerIndex: Int): PointerIcon =
        PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)

    fun scheduleCapture() {
        if (!InputCaptureBridge.pointerCaptureEnabled) return
        post(::requestCaptureWhenReady)
    }

    private fun requestCaptureWhenReady() {
        if (!isAttachedToWindow || !hasWindowFocus() || hasPointerCapture() || captureRequested) {
            return
        }
        captureRequested = true
        requestFocus()
        requestPointerCapture()
        postDelayed({
            if (!isAttachedToWindow || hasPointerCapture()) return@postDelayed
            captureRequested = false
            if (!capturedOnce) InputCaptureBridge.captureFailed()
        }, POINTER_CAPTURE_TIMEOUT_MS)
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        captureRequested = false
        if (hasCapture) capturedOnce = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_DOWN) {
            InputCaptureBridge.release()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        const val POINTER_CAPTURE_TIMEOUT_MS = 1_500L
    }
}

internal object InputCaptureBridge {
    private var activity: InputCaptureActivity? = null
    private var onMotion: ((MotionEvent) -> Boolean)? = null
    private var onRelease: (() -> Unit)? = null
    private var onCaptureFailed: (() -> Unit)? = null
    private var stopping = false
    var pointerCaptureEnabled = true
        private set

    fun start(
        onMotion: (MotionEvent) -> Boolean,
        onRelease: () -> Unit,
        onCaptureFailed: () -> Unit,
        pointerCaptureEnabled: Boolean,
    ) {
        stopping = false
        this.pointerCaptureEnabled = pointerCaptureEnabled
        this.onMotion = onMotion
        this.onRelease = onRelease
        this.onCaptureFailed = onCaptureFailed
    }

    fun attach(activity: InputCaptureActivity) {
        this.activity = activity
    }

    fun detach(activity: InputCaptureActivity) {
        if (this.activity === activity) this.activity = null
    }

    fun dispatchMotion(event: MotionEvent): Boolean = onMotion?.invoke(event) ?: false

    fun reclaimFocus() {
        if (!stopping) activity?.reclaimFocus()
    }

    fun captureFailed() {
        if (!stopping) onCaptureFailed?.invoke()
    }

    fun release() {
        if (!stopping) onRelease?.invoke()
    }

    fun stop() {
        if (stopping) return
        stopping = true
        val captureActivity = activity
        activity = null
        onMotion = null
        onRelease = null
        onCaptureFailed = null
        captureActivity?.finishCapture()
    }
}

/**
 * Android 14 keeps its compositor cursor alive even when Accessibility consumes SOURCE_MOUSE.
 * Xiaomi's Android 14 InputManager still exposes the legacy pointer-icon switch internally, so
 * use it as a guarded best-effort. Every call is reversible and failure falls back to the
 * transparent TYPE_NULL capture view without crashing the service.
 */
internal object SystemPointerIconController {
    private const val INPUT_SERVICE_CLASS = "android.hardware.input.InputManager"
    private const val SET_POINTER_ICON_TYPE = "setPointerIconType"

    fun hide(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            setIcon(context, PointerIcon.TYPE_NULL)
        } else {
            false
        }

    fun show(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            setIcon(context, PointerIcon.TYPE_ARROW)
        } else {
            false
        }

    @android.annotation.TargetApi(android.os.Build.VERSION_CODES.P)
    private fun setIcon(context: Context, iconType: Int): Boolean = runCatching {
        val inputManagerClass = Class.forName(INPUT_SERVICE_CLASS)
        val inputManager = context.getSystemService(Context.INPUT_SERVICE)
            ?: return@runCatching false
        HiddenApiBypass.invoke(
            inputManagerClass,
            inputManager,
            SET_POINTER_ICON_TYPE,
            iconType,
        )
        true
    }.getOrDefault(false)
}
