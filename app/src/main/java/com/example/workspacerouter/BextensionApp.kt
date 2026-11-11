package com.example.workspacerouter

import android.app.Application
import com.example.workspacerouter.di.AppContainer

class BextensionApp : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
}
