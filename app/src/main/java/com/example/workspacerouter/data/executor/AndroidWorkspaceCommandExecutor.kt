package com.example.workspacerouter.data.executor

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.example.workspacerouter.domain.executor.WorkspaceCommandExecutor
import com.example.workspacerouter.presentation.blackout.SecondaryBlackoutController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidWorkspaceCommandExecutor(
    context: Context,
    private val blackoutController: SecondaryBlackoutController,
) : WorkspaceCommandExecutor {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)

    override suspend fun launchApp(packageName: String, displayId: Int): Result<Unit> =
        withContext(Dispatchers.Main.immediate) {
            runCatching {
                val display = requireNotNull(displayManager.getDisplay(displayId)) {
                    "Display $displayId is no longer connected"
                }
                require(display.isValid) { "Display $displayId is not valid" }

                if (displayId != Display.DEFAULT_DISPLAY) {
                    require(
                        packageManager.hasSystemFeature(
                            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS,
                        ),
                    ) { "This device does not support activities on secondary displays" }
                }

                val launchIntent = requireNotNull(
                    packageManager.getLaunchIntentForPackage(packageName),
                ) { "$packageName has no launchable activity" }

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK,
                )

                val targetBounds = displayLaunchBounds(display)
                val options = ActivityOptions.makeBasic().apply {
                    launchDisplayId = displayId
                    launchBounds = targetBounds
                }
                appContext.startActivity(launchIntent, options.toBundle())
            }
        }

    private fun displayLaunchBounds(display: Display): Rect {
        val displayContext = appContext.createDisplayContext(display)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect(
                displayContext
                    .getSystemService(WindowManager::class.java)
                    .maximumWindowMetrics
                    .bounds,
            )
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also(display::getRealMetrics)
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    override suspend fun setSecondaryBlackout(
        displayId: Int,
        enabled: Boolean,
    ): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        runCatching {
            require(displayId != Display.DEFAULT_DISPLAY) {
                "Display 0 blackout is managed by the main Activity"
            }
            if (enabled) {
                blackoutController.show(displayId)
            } else {
                blackoutController.dismiss(displayId)
            }
        }
    }
}
