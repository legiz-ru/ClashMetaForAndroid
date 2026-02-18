package com.github.kr328.clash

import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class SettingsActivity : BaseActivity<SettingsDesign>() {
    override suspend fun main() {
        val design = SettingsDesign(this)

        setContentDesign(design)

        design.setClashRunning(clashRunning)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop -> {
                            design.setClashRunning(clashRunning)
                        }
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
                        SettingsDesign.Request.StartAbout -> {
                            startActivity(HelpActivity::class.intent)
                        }
                        SettingsDesign.Request.GoHome -> {
                            startActivity(
                                MainActivity::class.intent
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            )
                            finish()
                            overridePendingTransition(0, 0)
                        }
                        SettingsDesign.Request.OpenProfiles ->
                            startActivity(ProfilesActivity::class.intent)
                        SettingsDesign.Request.ToggleStatus -> {
                            if (clashRunning) {
                                stopClashService()
                            } else {
                                toggleClashOn(design)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun toggleClashOn(design: SettingsDesign) {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            design.showToast(com.github.kr328.clash.design.R.string.no_profile_selected, ToastDuration.Long)
            return
        }
        val vpnRequest = startClashService()
        try {
            if (vpnRequest != null) {
                val result = startActivityForResult(
                    ActivityResultContracts.StartActivityForResult(),
                    vpnRequest
                )
                if (result.resultCode == RESULT_OK)
                    startClashService()
            }
        } catch (e: Exception) {
            design.showToast(com.github.kr328.clash.design.R.string.unable_to_start_vpn, ToastDuration.Long)
        }
    }
}
