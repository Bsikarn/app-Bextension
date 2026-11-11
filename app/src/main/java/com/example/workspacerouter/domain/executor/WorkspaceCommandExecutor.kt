package com.example.workspacerouter.domain.executor

interface WorkspaceCommandExecutor {
    suspend fun launchApp(packageName: String, displayId: Int): Result<Unit>
    suspend fun setSecondaryBlackout(displayId: Int, enabled: Boolean): Result<Unit>
}
