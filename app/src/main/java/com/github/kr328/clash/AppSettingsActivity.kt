package com.github.kr328.clash

import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.ShortcutManagerCompat
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.design.AppSettingsDesign
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.store.UiStore.Companion.mainActivityAlias
import com.github.kr328.clash.service.TemplateManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.update.UpdateChecker
import com.github.kr328.clash.util.ApplicationObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class AppSettingsActivity : BaseActivity<AppSettingsDesign>(), Behavior {
    override suspend fun main() {
        val design = AppSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            this,
            clashRunning,
            ::onHideIconChange,
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                            recreate()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        AppSettingsDesign.Request.ReCreateAllActivities -> {
                            ApplicationObserver.createdActivities.forEach { activity ->
                                activity.recreate()
                            }
                        }
                        AppSettingsDesign.Request.SelectCustomTemplate -> {
                            val uri = startActivityForResult(
                                ActivityResultContracts.GetContent(), "*/*"
                            )
                            if (uri != null) {
                                val content = withContext(Dispatchers.IO) {
                                    contentResolver.openInputStream(uri)
                                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                                }
                                if (!content.isNullOrBlank()) {
                                    val customFile = filesDir.resolve("custom_template.yaml")
                                    withContext(Dispatchers.IO) {
                                        customFile.writeText(content, Charsets.UTF_8)
                                    }
                                    TemplateManager.setCustomTemplatePath(
                                        this@AppSettingsActivity, customFile.absolutePath
                                    )
                                }
                            }
                        }
                        AppSettingsDesign.Request.ShowAbout ->
                            design.showAbout(queryAppVersionName())
                        AppSettingsDesign.Request.CheckUpdate -> {
                            val pending = design.consumePendingUpdate()
                            if (pending != null) {
                                UpdateChecker.startDownload(this@AppSettingsActivity, pending.second, pending.first)
                            } else {
                                launch { runUpdateCheck(design) }
                            }
                        }
                    }
                }
            }
        }
    }

    override var autoRestart: Boolean
        get() {
            val status = packageManager.getComponentEnabledSetting(
                RestartReceiver::class.componentName
            )

            return status == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        set(value) {
            val status = if (value)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            packageManager.setComponentEnabledSetting(
                RestartReceiver::class.componentName,
                status,
                PackageManager.DONT_KILL_APP,
            )
        }

    private fun onHideIconChange(hide: Boolean) {
        val newState = if (hide) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        if (hide) {
            // Prevent launcher activity not found
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
        }
        packageManager.setComponentEnabledSetting(
            mainActivityAlias,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" + Bridge.nativeCoreVersion().replace("_", "-")
        }
    }

    private suspend fun runUpdateCheck(design: AppSettingsDesign) {
        when (val result = UpdateChecker.check(this)) {
            is UpdateChecker.CheckResult.UpdateAvailable ->
                design.showUpdateAvailableDialog(result.tagName, result.downloadUrl)
            is UpdateChecker.CheckResult.UpToDate ->
                design.showUpdateNotFoundDialog()
            is UpdateChecker.CheckResult.Error ->
                design.showUpdateErrorDialog(result.message)
        }
    }
}