package com.example.workspacerouter.presentation

import android.view.Display
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.workspacerouter.domain.executor.WorkspaceCommandExecutor
import com.example.workspacerouter.domain.model.DisplayActionMode
import com.example.workspacerouter.domain.model.LaunchableApp
import com.example.workspacerouter.domain.model.RoutedDisplay
import com.example.workspacerouter.domain.repository.AppRepository
import com.example.workspacerouter.domain.repository.DisplayRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val displays: List<RoutedDisplay> = emptyList(),
    val apps: List<LaunchableApp> = emptyList(),
    val isLoadingApps: Boolean = true,
    val isMainDisplayBlackout: Boolean = false,
)

sealed interface MainUiEvent {
    data class ShowMessage(val message: String) : MainUiEvent
}

class MainViewModel(
    private val displayRepository: DisplayRepository,
    private val appRepository: AppRepository,
    private val commandExecutor: WorkspaceCommandExecutor,
) : ViewModel() {

    private val apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    private val loadingApps = MutableStateFlow(true)
    private val mainDisplayBlackout = MutableStateFlow(false)
    private val mutableEvents = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 8)

    val events = mutableEvents.asSharedFlow()

    val uiState: StateFlow<MainUiState> = combine(
        displayRepository.displays,
        apps,
        loadingApps,
        mainDisplayBlackout,
    ) { displays, installedApps, loading, blackout ->
        MainUiState(displays, installedApps, loading, blackout)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState(),
    )

    init {
        refreshApps()
    }

    fun refreshDisplays() = displayRepository.refresh()

    fun refreshApps() {
        viewModelScope.launch {
            loadingApps.value = true
            runCatching { appRepository.getLaunchableApps() }
                .onSuccess { apps.value = it }
                .onFailure { report("Could not load installed apps: ${it.userMessage()}") }
            loadingApps.value = false
        }
    }

    fun setMode(displayId: Int, mode: DisplayActionMode) {
        displayRepository.setMode(displayId, mode)
    }

    fun selectApp(displayId: Int, packageName: String?) {
        displayRepository.selectApp(displayId, packageName)
    }

    fun testDisplay(displayId: Int) {
        val route = uiState.value.displays.firstOrNull { it.display.id == displayId }
            ?: return report("Display $displayId is no longer connected")
        viewModelScope.launch { applyRoute(route) }
    }

    fun launchWorkspace() {
        val routes = uiState.value.displays
        viewModelScope.launch {
            routes.sortedBy { it.display.isDefault }.forEach { applyRoute(it) }
        }
    }

    fun wakeDashboard() {
        mainDisplayBlackout.value = false
    }

    private suspend fun applyRoute(route: RoutedDisplay) {
        val displayId = route.display.id
        when (route.assignment.mode) {
            DisplayActionMode.LAUNCH_APP -> {
                val packageName = route.assignment.packageName
                if (packageName == null) {
                    report("Choose an app for ${route.display.name}")
                    return
                }
                if (displayId != Display.DEFAULT_DISPLAY) {
                    commandExecutor.setSecondaryBlackout(displayId, false)
                } else {
                    mainDisplayBlackout.value = false
                }
                commandExecutor.launchApp(packageName, displayId)
                    .onFailure {
                        report("Could not launch $packageName on ${route.display.name}: ${it.userMessage()}")
                    }
            }

            DisplayActionMode.BLACK_OUT -> {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    mainDisplayBlackout.value = true
                } else {
                    commandExecutor.setSecondaryBlackout(displayId, true)
                        .onFailure {
                            report("Could not blank ${route.display.name}: ${it.userMessage()}")
                        }
                }
            }
        }
    }

    private fun report(message: String) {
        mutableEvents.tryEmit(MainUiEvent.ShowMessage(message))
    }

    private fun Throwable.userMessage(): String =
        localizedMessage?.takeIf(String::isNotBlank) ?: javaClass.simpleName
}

class MainViewModelFactory(
    private val displayRepository: DisplayRepository,
    private val appRepository: AppRepository,
    private val commandExecutor: WorkspaceCommandExecutor,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(displayRepository, appRepository, commandExecutor) as T
    }
}
