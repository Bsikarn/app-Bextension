package com.example.workspacerouter.domain.repository

import com.example.workspacerouter.domain.model.DisplayActionMode
import com.example.workspacerouter.domain.model.LaunchableApp
import com.example.workspacerouter.domain.model.RoutedDisplay
import kotlinx.coroutines.flow.StateFlow

interface DisplayRepository {
    val displays: StateFlow<List<RoutedDisplay>>

    fun refresh()
    fun setMode(displayId: Int, mode: DisplayActionMode)
    fun selectApp(displayId: Int, packageName: String?)
}

interface AppRepository {
    suspend fun getLaunchableApps(): List<LaunchableApp>
}
