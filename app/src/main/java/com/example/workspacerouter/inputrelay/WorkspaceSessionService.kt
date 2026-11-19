package com.example.workspacerouter.inputrelay

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Session state is intentionally in memory: reboot or process death never restores overlays. */
internal object WorkspaceSession {
    var active = false
        private set
    var onChanged: ((Boolean) -> Unit)? = null

    fun start() {
        active = true
        onChanged?.invoke(true)
    }

    fun stop() {
        active = false
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
