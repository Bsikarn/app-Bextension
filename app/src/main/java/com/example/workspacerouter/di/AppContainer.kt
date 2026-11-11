package com.example.workspacerouter.di

import android.content.Context
import com.example.workspacerouter.data.apps.AndroidAppRepository
import com.example.workspacerouter.data.display.AndroidDisplayRepository
import com.example.workspacerouter.data.executor.AndroidWorkspaceCommandExecutor
import com.example.workspacerouter.domain.executor.WorkspaceCommandExecutor
import com.example.workspacerouter.domain.repository.AppRepository
import com.example.workspacerouter.domain.repository.DisplayRepository
import com.example.workspacerouter.presentation.blackout.SecondaryBlackoutController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val blackoutController = SecondaryBlackoutController(context)

    val displayRepository: DisplayRepository =
        AndroidDisplayRepository(context, applicationScope)
    val appRepository: AppRepository = AndroidAppRepository(context)
    val commandExecutor: WorkspaceCommandExecutor =
        AndroidWorkspaceCommandExecutor(context, blackoutController)

    fun emergencyStop() {
        blackoutController.dismissAll()
    }
}
