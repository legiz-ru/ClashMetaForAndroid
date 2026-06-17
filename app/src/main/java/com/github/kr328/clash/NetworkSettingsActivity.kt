package com.github.kr328.clash

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.setContent
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.NetworkSettingsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.isActive

class NetworkSettingsActivity : BaseActivity() {
    override suspend fun main() {
        val srvStore = ServiceStore(this)

        if (clashRunning) {
            Toast.makeText(this, R.string.options_unavailable, Toast.LENGTH_LONG).show()
        }

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                NetworkSettingsScreen(
                    uiStore = uiStore,
                    srvStore = srvStore,
                    running = clashRunning,
                    onBack = { finish() },
                    onAccessControlPackages = {
                        startActivity(AccessControlActivity::class.intent)
                    },
                )
            }
        }

        while (isActive) {
            when (events.receive()) {
                Event.ClashStart, Event.ClashStop, Event.ServiceRecreated -> recreate()
                else -> Unit
            }
        }
    }

    private fun currentThemeVariant(): ClashThemeVariant {
        val cfg = resources.configuration
        return when (uiStore.darkMode) {
            DarkMode.Auto ->
                if (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ClashThemeVariant.Dark
                } else {
                    ClashThemeVariant.Light
                }
            DarkMode.ForceLight -> ClashThemeVariant.Light
            DarkMode.ForceDark -> ClashThemeVariant.Dark
            DarkMode.AlwaysSummer -> ClashThemeVariant.Summer
        }
    }
}
