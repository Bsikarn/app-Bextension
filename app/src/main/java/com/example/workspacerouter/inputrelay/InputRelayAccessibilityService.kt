package com.example.workspacerouter.inputrelay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.workspacerouter.BextensionApp
import kotlin.math.abs

class InputRelayAccessibilityService : AccessibilityService(), DisplayManager.DisplayListener {

    private lateinit var displayManager: DisplayManager
    private var overlayController: DisplayOverlayController? = null
    private var screenOffReceiverRegistered = false
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return

            overlayController?.close()
            overlayController = null
            (application as? BextensionApp)?.container?.emergencyStop()
            disableSelf()
            stopSelf()
            Handler(Looper.getMainLooper()).post {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager.registerDisplayListener(this, null)
        overlayController = DisplayOverlayController(this, displayManager).also { it.refresh() }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOffReceiverRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString().orEmpty()

        if (packageName in REMOTE_APP_PACKAGES && className.endsWith(".Game")) {
            val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                event.displayId
            } else {
                overlayController?.currentSelectedDisplayId() ?: Display.DEFAULT_DISPLAY
            }
            overlayController?.enterDirectHandoff(displayId)
        }
    }

    override fun onInterrupt() = Unit

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMotionEvent(event: MotionEvent) {
        overlayController?.handleGlobalMotion(event)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val reclaimShortcut = event.keyCode == KeyEvent.KEYCODE_SPACE &&
            event.isCtrlPressed && event.isAltPressed
        if (!reclaimShortcut) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            overlayController?.activateVirtualControl()
        }
        return true
    }

    override fun onDisplayAdded(displayId: Int) {
        overlayController?.refresh()
    }

    override fun onDisplayChanged(displayId: Int) {
        overlayController?.refresh()
    }

    override fun onDisplayRemoved(displayId: Int) {
        overlayController?.refresh()
    }

    override fun onDestroy() {
        if (::displayManager.isInitialized) displayManager.unregisterDisplayListener(this)
        if (screenOffReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffReceiverRegistered = false
        }
        overlayController?.close()
        overlayController = null
        super.onDestroy()
    }

    internal fun injectTap(
        displayId: Int,
        x: Float,
        y: Float,
        durationMs: Long = 70L,
        onFinished: () -> Unit,
    ) {
        injectStroke(displayId, x, y, x, y, durationMs, onFinished)
    }

    internal fun injectLongPress(displayId: Int, x: Float, y: Float, onFinished: () -> Unit) {
        injectStroke(displayId, x, y, x, y, 650L, onFinished)
    }

    internal fun injectDrag(
        displayId: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 280L,
        onFinished: () -> Unit,
    ) {
        injectStroke(displayId, startX, startY, endX, endY, durationMs, onFinished)
    }

    internal fun setGlobalMouseCaptureEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val updatedInfo = serviceInfo
        updatedInfo.setMotionEventSources(
            if (enabled) {
                InputDevice.SOURCE_MOUSE or
                    InputDevice.SOURCE_MOUSE_RELATIVE or
                    InputDevice.SOURCE_TOUCHPAD
            } else {
                0
            },
        )
        setServiceInfo(updatedInfo)
    }

    internal fun dispatchStroke(
        displayId: Int,
        stroke: GestureDescription.StrokeDescription,
        onFinished: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFinished()
            return
        }
        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(stroke)
            .build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onFinished()

                override fun onCancelled(gestureDescription: GestureDescription?) = onFinished()
            },
            null,
        )
        if (!accepted) onFinished()
    }

    private fun injectStroke(
        displayId: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        onFinished: () -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onFinished()
            return
        }
        val path = Path().apply {
            moveTo(startX, startY)
            if (startX != endX || startY != endY) lineTo(endX, endY)
        }
        dispatchStroke(
            displayId,
            GestureDescription.StrokeDescription(path, 0L, durationMs),
            onFinished,
        )
    }

    private companion object {
        val REMOTE_APP_PACKAGES = setOf(
            "com.limelight",
            "com.limelight.root",
            "com.limelight.debug",
        )
    }
}

private class DisplayOverlayController(
    private val service: InputRelayAccessibilityService,
    private val displayManager: DisplayManager,
) : AutoCloseable {

    private data class OverlayWindow(
        val displayId: Int,
        val windowManager: WindowManager,
        val root: LinearLayout,
        var expanded: Boolean,
    )

    private val tabs = mutableMapOf<Int, OverlayWindow>()
    private var cursorWindow: Pair<WindowManager, CursorView>? = null
    private var usingGlobalMouseCapture = false
    private var selectedDisplayId: Int = displayManager.displays
        .firstOrNull { it.isValid && it.displayId != Display.DEFAULT_DISPLAY }
        ?.displayId
        ?: Display.DEFAULT_DISPLAY

    fun currentSelectedDisplayId(): Int = selectedDisplayId

    fun refresh() {
        val connected = displayManager.displays.filter(Display::isValid)
        val connectedIds = connected.map(Display::getDisplayId).toSet()

        tabs.keys.filterNot(connectedIds::contains).toList().forEach(::removeTab)
        connected.forEach { display ->
            if (display.displayId !in tabs) addTab(display)
        }

        val firstExternalDisplayId = connected
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?.displayId
        if (selectedDisplayId == Display.DEFAULT_DISPLAY && firstExternalDisplayId != null) {
            selectedDisplayId = firstExternalDisplayId
        } else if (selectedDisplayId !in connectedIds) {
            selectedDisplayId = connected
                .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
                ?.displayId
                ?: connected.firstOrNull()?.displayId
                ?: Display.DEFAULT_DISPLAY
            releaseControl()
        }
        rebuildTabs()
    }

    fun activateVirtualControl() {
        if (selectedDisplayId == Display.DEFAULT_DISPLAY) {
            Toast.makeText(service, "Connect an external display first.", Toast.LENGTH_SHORT).show()
            return
        }
        val target = displayManager.getDisplay(selectedDisplayId) ?: return
        releaseControl()
        addCursorWindow(target)
        usingGlobalMouseCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        startCaptureActivity()
        if (usingGlobalMouseCapture) {
            service.setGlobalMouseCaptureEnabled(true)
            SystemPointerIconController.hide(service)
        }
        collapseTabs()
    }

    fun enterDirectHandoff(displayId: Int) {
        if (displayManager.getDisplay(displayId) != null) selectedDisplayId = displayId
        releaseControl()
        collapseTabs()
    }

    override fun close() {
        releaseControl()
        tabs.keys.toList().forEach(::removeTab)
    }

    private fun addTab(display: Display) {
        val displayContext = service.createDisplayContext(display)
        val windowManager = displayContext.getSystemService(WindowManager::class.java)
        val root = LinearLayout(displayContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(1), dp(1), dp(1), dp(1))
            background = panelBackground()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = dp(72)
            title = "Bextension D${display.displayId}"
        }
        runCatching { windowManager.addView(root, params) }
            .onSuccess {
                tabs[display.displayId] = OverlayWindow(
                    displayId = display.displayId,
                    windowManager = windowManager,
                    root = root,
                    expanded = false,
                )
            }
    }

    private fun rebuildTabs() {
        tabs.values.forEach { overlay ->
            val root = overlay.root
            root.removeAllViews()
            val panelPadding = if (overlay.expanded) dp(8) else dp(1)
            root.setPadding(panelPadding, panelPadding, panelPadding, panelPadding)

            root.addView(TextView(root.context).apply {
                text = if (overlay.expanded) {
                    "Bextension • D${overlay.displayId}"
                } else {
                    "<"
                }
                setTextColor(Color.WHITE)
                textSize = if (overlay.expanded) 13f else 20f
                gravity = Gravity.CENTER
                setPadding(
                    if (overlay.expanded) dp(6) else dp(8),
                    if (overlay.expanded) dp(4) else dp(6),
                    if (overlay.expanded) dp(6) else dp(5),
                    if (overlay.expanded) dp(4) else dp(6),
                )
                setOnClickListener {
                    overlay.expanded = !overlay.expanded
                    rebuildTabs()
                }
            })

            if (!overlay.expanded) return@forEach

            val displayRow = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            displayManager.displays
                .filter { it.isValid && it.displayId != Display.DEFAULT_DISPLAY }
                .forEach { display ->
                displayRow.addView(Button(root.context).apply {
                    text = "D${display.displayId}"
                    isAllCaps = false
                    isEnabled = display.displayId != selectedDisplayId
                    setOnClickListener {
                        selectedDisplayId = display.displayId
                        rebuildTabs()
                    }
                })
            }
            root.addView(displayRow)

            root.addView(Button(root.context).apply {
                text = if (selectedDisplayId == Display.DEFAULT_DISPLAY) {
                    "Connect external display"
                } else {
                    "Virtual control D$selectedDisplayId"
                }
                isAllCaps = false
                isEnabled = selectedDisplayId != Display.DEFAULT_DISPLAY
                setOnClickListener { activateVirtualControl() }
            })
            root.addView(Button(root.context).apply {
                text = "Direct handoff"
                isAllCaps = false
                isEnabled = selectedDisplayId != Display.DEFAULT_DISPLAY
                setOnClickListener { enterDirectHandoff(selectedDisplayId) }
            })
            root.addView(TextView(root.context).apply {
                text = "Ctrl+Alt+Space: reclaim control"
                setTextColor(Color.LTGRAY)
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(3), dp(6), dp(3))
            })
        }
    }

    private fun addCursorWindow(display: Display) {
        val displayContext = service.createDisplayContext(display)
        val windowManager = displayContext.getSystemService(WindowManager::class.java)
        val cursor = CursorView(displayContext)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "Bextension Virtual Cursor"
        }
        runCatching { windowManager.addView(cursor, params) }
            .onSuccess { cursorWindow = windowManager to cursor }
    }

    private fun startCaptureActivity() {
        InputCaptureBridge.start(
            onMotion = ::handleCapturedMotion,
            onRelease = ::releaseControl,
            onCaptureFailed = {
                Toast.makeText(
                    service,
                    "Mouse capture failed. Control was released; tap Virtual control to retry.",
                    Toast.LENGTH_LONG,
                ).show()
                releaseControl()
            },
            pointerCaptureEnabled = !usingGlobalMouseCapture,
        )
        InputCaptureActivity.launch(service)
    }

    private var lastAbsoluteMouseX: Float? = null
    private var lastAbsoluteMouseY: Float? = null
    private var primaryPressStart: Pair<Float, Float>? = null
    private var activeDragStroke: GestureDescription.StrokeDescription? = null
    private var dragLastPoint: Pair<Float, Float>? = null
    private var pendingDragPoint: Pair<Float, Float>? = null
    private var dragReleaseRequested = false

    private fun handleCapturedMotion(event: MotionEvent): Boolean {
        val cursor = cursorWindow?.second ?: return false
        val relativeSource = event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        val relativeX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val relativeY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        val deltaX = when {
            relativeX != 0f -> relativeX
            relativeSource -> event.x
            lastAbsoluteMouseX != null -> event.x - lastAbsoluteMouseX!!
            else -> 0f
        } * MOUSE_SPEED_MULTIPLIER
        val deltaY = when {
            relativeY != 0f -> relativeY
            relativeSource -> event.y
            lastAbsoluteMouseY != null -> event.y - lastAbsoluteMouseY!!
            else -> 0f
        } * MOUSE_SPEED_MULTIPLIER
        if (!relativeSource) {
            lastAbsoluteMouseX = event.x
            lastAbsoluteMouseY = event.y
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE,
            MotionEvent.ACTION_HOVER_MOVE,
            -> {
                cursor.moveBy(deltaX, deltaY)
                if (primaryPressStart != null) {
                    val start = primaryPressStart!!
                    if (abs(start.first - cursor.cursorX) > DRAG_START_THRESHOLD_PX ||
                        abs(start.second - cursor.cursorY) > DRAG_START_THRESHOLD_PX
                    ) {
                        pendingDragPoint = cursor.cursorX to cursor.cursorY
                        pumpLiveDrag()
                    }
                    if (event.buttonState and MotionEvent.BUTTON_PRIMARY == 0 &&
                        event.actionMasked == MotionEvent.ACTION_MOVE
                    ) {
                        finishPrimaryPress(cursor)
                    }
                }
            }
            MotionEvent.ACTION_BUTTON_PRESS -> when (event.actionButton) {
                MotionEvent.BUTTON_PRIMARY -> beginPrimaryPress(cursor)
                MotionEvent.BUTTON_SECONDARY -> runGesture { finished ->
                    service.injectLongPress(
                        selectedDisplayId,
                        cursor.cursorX,
                        cursor.cursorY,
                        finished,
                    )
                }
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> if (event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                finishPrimaryPress(cursor)
            }
            MotionEvent.ACTION_DOWN -> if (
                event.buttonState and MotionEvent.BUTTON_PRIMARY != 0 && primaryPressStart == null
            ) beginPrimaryPress(cursor)
            MotionEvent.ACTION_UP -> if (primaryPressStart != null) finishPrimaryPress(cursor)
            MotionEvent.ACTION_SCROLL -> {
                requestScroll(cursor, event.getAxisValue(MotionEvent.AXIS_VSCROLL))
            }
        }
        return true
    }

    private fun beginPrimaryPress(cursor: CursorView) {
        if (primaryPressStart != null) return
        primaryPressStart = cursor.cursorX to cursor.cursorY
        dragLastPoint = primaryPressStart
        pendingDragPoint = null
        activeDragStroke = null
        dragReleaseRequested = false
    }

    private fun finishPrimaryPress(cursor: CursorView) {
        val start = primaryPressStart ?: return
        val wasDrag = activeDragStroke != null || pendingDragPoint != null ||
            abs(start.first - cursor.cursorX) > DRAG_START_THRESHOLD_PX ||
            abs(start.second - cursor.cursorY) > DRAG_START_THRESHOLD_PX
        if (!wasDrag) {
            resetLiveDrag()
            runGesture { finished ->
                service.injectTap(
                    selectedDisplayId,
                    cursor.cursorX,
                    cursor.cursorY,
                    onFinished = finished,
                )
            }
            return
        }

        dragReleaseRequested = true
        pendingDragPoint = cursor.cursorX to cursor.cursorY
        pumpLiveDrag()
    }

    private fun pumpLiveDrag() {
        if (gestureInFlight) return
        val start = dragLastPoint ?: primaryPressStart ?: return
        val end = pendingDragPoint ?: return
        val previousStroke = activeDragStroke
        val willContinue = !dragReleaseRequested
        val path = Path().apply {
            moveTo(start.first, start.second)
            if (start == end) {
                lineTo(end.first + 0.01f, end.second)
            } else {
                lineTo(end.first, end.second)
            }
        }
        val nextStroke = if (previousStroke == null) {
            GestureDescription.StrokeDescription(path, 0L, LIVE_DRAG_SEGMENT_MS, willContinue)
        } else {
            previousStroke.continueStroke(path, 0L, LIVE_DRAG_SEGMENT_MS, willContinue)
        }

        pendingDragPoint = null
        gestureInFlight = true
        service.dispatchStroke(selectedDisplayId, nextStroke) {
            gestureInFlight = false
            dragLastPoint = end
            activeDragStroke = if (willContinue) nextStroke else null
            if (!usingGlobalMouseCapture) InputCaptureBridge.reclaimFocus()
            if (!willContinue) {
                resetLiveDrag()
            } else if (pendingDragPoint != null || dragReleaseRequested) {
                if (dragReleaseRequested && pendingDragPoint == null) pendingDragPoint = end
                pumpLiveDrag()
            }
        }
    }

    private fun resetLiveDrag() {
        primaryPressStart = null
        activeDragStroke = null
        dragLastPoint = null
        pendingDragPoint = null
        dragReleaseRequested = false
    }

    fun handleGlobalMotion(event: MotionEvent) {
        if (usingGlobalMouseCapture) handleCapturedMotion(event)
    }

    private fun releaseControl() {
        gestureInFlight = false
        pendingScrollAmount = 0f
        resetLiveDrag()
        lastAbsoluteMouseX = null
        lastAbsoluteMouseY = null
        if (usingGlobalMouseCapture) {
            SystemPointerIconController.show(service)
            service.setGlobalMouseCaptureEnabled(false)
        }
        usingGlobalMouseCapture = false
        InputCaptureBridge.stop()

        cursorWindow?.let { (windowManager, view) ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        cursorWindow = null
    }

    private var gestureInFlight = false
    private var pendingScrollAmount = 0f

    private fun requestScroll(cursor: CursorView, amount: Float) {
        if (amount == 0f) return
        if (gestureInFlight) {
            pendingScrollAmount += amount
            return
        }
        val endY = (cursor.cursorY - amount * SCROLL_DISTANCE_PX)
            .coerceIn(0f, cursor.height.coerceAtLeast(1).toFloat())
        runGesture { finished ->
            service.injectDrag(
                selectedDisplayId,
                cursor.cursorX,
                cursor.cursorY,
                cursor.cursorX,
                endY,
                SCROLL_DURATION_MS,
                finished,
            )
        }
    }

    private fun runGesture(inject: ((() -> Unit) -> Unit)) {
        if (gestureInFlight) return
        gestureInFlight = true
        inject {
            gestureInFlight = false
            if (!usingGlobalMouseCapture) InputCaptureBridge.reclaimFocus()
            val queuedScroll = pendingScrollAmount
            pendingScrollAmount = 0f
            if (queuedScroll != 0f) {
                cursorWindow?.second?.post {
                    cursorWindow?.second?.let { requestScroll(it, queuedScroll) }
                }
            }
        }
    }

    private fun collapseTabs() {
        tabs.values.forEach { it.expanded = false }
        rebuildTabs()
    }

    private fun removeTab(displayId: Int) {
        tabs.remove(displayId)?.let { overlay ->
            runCatching { overlay.windowManager.removeViewImmediate(overlay.root) }
        }
    }

    private fun panelBackground() = GradientDrawable().apply {
        setColor(Color.argb(230, 35, 35, 45))
        cornerRadius = dp(12).toFloat()
        setStroke(dp(1), Color.argb(180, 170, 170, 255))
    }

    private fun dp(value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val MOUSE_SPEED_MULTIPLIER = 1.8f
        const val SCROLL_DISTANCE_PX = 280f
        const val SCROLL_DURATION_MS = 220L
        const val DRAG_START_THRESHOLD_PX = 4f
        const val LIVE_DRAG_SEGMENT_MS = 24L
    }
}

private class CursorView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private var pressStart: Pair<Float, Float>? = null

    var cursorX: Float = 0f
        private set
    var cursorY: Float = 0f
        private set

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (oldw == 0 || oldh == 0) {
            cursorX = w / 2f
            cursorY = h / 2f
        }
    }

    fun moveBy(deltaX: Float, deltaY: Float) {
        cursorX = (cursorX + deltaX).coerceIn(0f, width.coerceAtLeast(1).toFloat())
        cursorY = (cursorY + deltaY).coerceIn(0f, height.coerceAtLeast(1).toFloat())
        invalidate()
    }

    fun beginPrimaryPress() {
        pressStart = cursorX to cursorY
    }

    fun endPrimaryPress(): Pair<Float, Float>? = pressStart.also { pressStart = null }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pointer = Path().apply {
            moveTo(cursorX, cursorY)
            lineTo(cursorX + 11f, cursorY + 24f)
            lineTo(cursorX + 15f, cursorY + 15f)
            lineTo(cursorX + 24f, cursorY + 11f)
            close()
        }
        canvas.drawPath(pointer, fill)
        canvas.drawPath(pointer, stroke)
    }
}
