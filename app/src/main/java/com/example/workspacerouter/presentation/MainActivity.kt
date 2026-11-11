package com.example.workspacerouter.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workspacerouter.BextensionApp
import com.example.workspacerouter.presentation.commandcenter.CommandCenterScreen
import com.example.workspacerouter.ui.theme.BextensionTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val container = (application as BextensionApp).container
        MainViewModelFactory(
            container.displayRepository,
            container.appRepository,
            container.commandExecutor,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BextensionTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                DisposableEffect(state.isMainDisplayBlackout) {
                    val attributes = window.attributes
                    val previousBrightness = attributes.screenBrightness
                    attributes.screenBrightness = if (state.isMainDisplayBlackout) {
                        0f
                    } else {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                    window.attributes = attributes
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        if (state.isMainDisplayBlackout) hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        else show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }

                    onDispose {
                        window.attributes = window.attributes.apply {
                            screenBrightness = previousBrightness
                        }
                    }
                }

                if (state.isMainDisplayBlackout) {
                    BackHandler(onBack = viewModel::wakeDashboard)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { viewModel.wakeDashboard() })
                            },
                    )
                } else {
                    CommandCenterScreen(
                        state = state,
                        events = viewModel.events,
                        onSetMode = viewModel::setMode,
                        onSelectApp = viewModel::selectApp,
                        onTestDisplay = viewModel::testDisplay,
                        onLaunchWorkspace = viewModel::launchWorkspace,
                        onRefreshDisplays = viewModel::refreshDisplays,
                    )
                }
            }
        }
    }
}
