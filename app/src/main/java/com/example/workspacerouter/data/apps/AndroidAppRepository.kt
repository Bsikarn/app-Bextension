package com.example.workspacerouter.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.workspacerouter.domain.model.LaunchableApp
import com.example.workspacerouter.domain.repository.AppRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

class AndroidAppRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppRepository {

    private val packageManager = context.applicationContext.packageManager

    override suspend fun getLaunchableApps(): List<LaunchableApp> = withContext(ioDispatcher) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val collator = Collator.getInstance()

        packageManager.queryLauncherActivities(launcherIntent)
            .asSequence()
            .filter { it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled }
            .map { info ->
                LaunchableApp(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = info.activityInfo.packageName,
                    activityClassName = info.activityInfo.name,
                )
            }
            .distinctBy(LaunchableApp::packageName)
            .sortedWith { left, right -> collator.compare(left.label, right.label) }
            .toList()
    }

    private fun PackageManager.queryLauncherActivities(intent: Intent) =
        if (Build.VERSION.SDK_INT >= 33) {
            queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(
                    PackageManager.MATCH_DISABLED_COMPONENTS.toLong(),
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, PackageManager.MATCH_DISABLED_COMPONENTS)
        }
}
