package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.bridge.Bridge
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class SettingsActivity : BaseActivity<SettingsDesign>() {
    override suspend fun main() {
        val design = SettingsDesign(this)

        setContentDesign(design)
        design.fetch()

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart,
                        Event.ServiceRecreated,
                        Event.ClashStop,
                        Event.ClashStart,
                        Event.ProfileLoaded,
                        Event.ProfileChanged -> design.fetch()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartOverride ->
                            startActivity(OverrideSettingsActivity::class.intent)
                        SettingsDesign.Request.StartMetaFeature ->
                            startActivity(MetaFeatureSettingsActivity::class.intent)
                        SettingsDesign.Request.StartProviders ->
                            startActivity(ProvidersActivity::class.intent)
                        SettingsDesign.Request.StartLogs -> {
                            if (LogcatService.running) {
                                startActivity(LogcatActivity::class.intent)
                            } else {
                                startActivity(LogsActivity::class.intent)
                            }
                        }
                        SettingsDesign.Request.StartHelp ->
                            startActivity(HelpActivity::class.intent)
                        SettingsDesign.Request.StartAbout ->
                            design.showAbout(queryAppVersionName())
                    }
                }
            }
        }
    }

    private suspend fun SettingsDesign.fetch() {
        setClashRunning(clashRunning)

        val providers = if (clashRunning) {
            withClash { queryProviders() }
        } else {
            emptyList()
        }

        setHasProviders(providers.isNotEmpty())
    }

    private suspend fun queryAppVersionName(): String {
        return withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0).versionName + "\n" +
                Bridge.nativeCoreVersion().replace("_", "-")
        }
    }
}
