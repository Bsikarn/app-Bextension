package com.example.workspacerouter.data.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.example.workspacerouter.domain.model.DisplayActionMode
import com.example.workspacerouter.domain.model.DisplayAssignment
import com.example.workspacerouter.domain.model.DisplayDescriptor
import com.example.workspacerouter.domain.model.RoutedDisplay
import com.example.workspacerouter.domain.repository.DisplayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class AndroidDisplayRepository(
    context: Context,
    applicationScope: CoroutineScope,
) : DisplayRepository, AutoCloseable {

    private val displayManager =
        context.applicationContext.getSystemService(DisplayManager::class.java)

    private val detectedDisplays = MutableStateFlow(readAllDisplays())
    private val assignments = MutableStateFlow<Map<Int, DisplayAssignment>>(emptyMap())

    override val displays: StateFlow<List<RoutedDisplay>> =
        combine(detectedDisplays, assignments) { detected, assigned ->
            detected.map { descriptor ->
                RoutedDisplay(
                    display = descriptor,
                    assignment = assigned[descriptor.id] ?: DisplayAssignment(),
                )
            }
        }.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = detectedDisplays.value.map { RoutedDisplay(it, DisplayAssignment()) },
        )

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
    }

    init {
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
    }

    override fun refresh() {
        val snapshot = readAllDisplays()
        detectedDisplays.value = snapshot
        val connectedIds = snapshot.asSequence().map { it.id }.toSet()
        assignments.update { current -> current.filterKeys(connectedIds::contains) }
    }

    override fun setMode(displayId: Int, mode: DisplayActionMode) {
        mutateConnectedAssignment(displayId) { it.copy(mode = mode) }
    }

    override fun selectApp(displayId: Int, packageName: String?) {
        mutateConnectedAssignment(displayId) { it.copy(packageName = packageName) }
    }

    private fun mutateConnectedAssignment(
        displayId: Int,
        transform: (DisplayAssignment) -> DisplayAssignment,
    ) {
        if (detectedDisplays.value.none { it.id == displayId }) return
        assignments.update { current ->
            val previous = current[displayId] ?: DisplayAssignment()
            current + (displayId to transform(previous))
        }
    }

    private fun readAllDisplays(): List<DisplayDescriptor> =
        displayManager.displays
            .asSequence()
            .filter(Display::isValid)
            .map(::toDescriptor)
            .sortedBy(DisplayDescriptor::id)
            .toList()

    private fun toDescriptor(display: Display): DisplayDescriptor {
        val mode = display.mode
        return DisplayDescriptor(
            id = display.displayId,
            name = if (display.displayId == Display.DEFAULT_DISPLAY) {
                "Tablet Screen"
            } else {
                display.name.ifBlank { "External Display ${display.displayId}" }
            },
            physicalWidthPx = mode.physicalWidth,
            physicalHeightPx = mode.physicalHeight,
            refreshRateHz = mode.refreshRate,
            isDefault = display.displayId == Display.DEFAULT_DISPLAY,
            state = display.state,
        )
    }

    override fun close() {
        displayManager.unregisterDisplayListener(listener)
    }
}
