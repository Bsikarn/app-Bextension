package com.example.workspacerouter.domain.model

enum class DisplayActionMode {
    LAUNCH_APP,
    BLACK_OUT,
}

data class DisplayAssignment(
    val mode: DisplayActionMode = DisplayActionMode.LAUNCH_APP,
    val packageName: String? = null,
)

data class DisplayDescriptor(
    val id: Int,
    val name: String,
    val physicalWidthPx: Int,
    val physicalHeightPx: Int,
    val refreshRateHz: Float,
    val isDefault: Boolean,
    val state: Int,
)

data class RoutedDisplay(
    val display: DisplayDescriptor,
    val assignment: DisplayAssignment,
)

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityClassName: String,
)
