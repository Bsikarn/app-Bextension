package com.example.workspacerouter.inputrelay

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Session state is intentionally in memory: reboot or process death never restores overlays. */
internal object WorkspaceSession {
    private val mutableActive = MutableStateFlow(false)
    val state = mutableActive.asStateFlow()
    val active: Boolean get() = mutableActive.value
    var onChanged: ((Boolean) -> Unit)? = null

    fun start() {
        mutableActive.value = true
        onChanged?.invoke(true)
    }

    fun stop() {
        mutableActive.value = false
        onChanged?.invoke(false)
    }
}

class WorkspaceSessionService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        WorkspaceSession.start()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        WorkspaceSession.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        WorkspaceSession.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
