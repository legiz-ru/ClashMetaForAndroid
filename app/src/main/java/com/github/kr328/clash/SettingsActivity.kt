package com.github.kr328.clash

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.compose.screen.SettingsDestination
import com.github.kr328.clash.design.compose.screen.SettingsNavTarget
import com.github.kr328.clash.design.compose.screen.SettingsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SettingsActivity : BaseActivity() {
    private val running = MutableStateFlow(clashRunning)
    private val hasProfilesFlow = MutableStateFlow(true)

    override suspend fun main() {
        hasProfilesFlow.value = withProfile { queryAll().isNotEmpty() }

        setContent {
            val isRunning by running.collectAsStateWithLifecycle()
            val hasProfiles by hasProfilesFlow.collectAsStateWithLifecycle()

            ClashTheme(variant = currentThemeVariant()) {
                SettingsScreen(
                    expanded = useDrawerNav(),
                    clashRunning = isRunning,
                    onOpen = ::openDestination,
                    onNavigate = ::navigate,
                    onToggleStatus = { toggleStatus() },
                    hasProfiles = hasProfiles,
                )
            }
        }

        while (isActive) {
            when (events.receive()) {
                Event.ClashStart, Event.ClashStop -> running.value = clashRunning
                Event.ProfileChanged, Event.ProfileLoaded, Event.ActivityStart ->
                    hasProfilesFlow.value = withProfile { queryAll().isNotEmpty() }
                else -> Unit
            }
        }
    }

    private fun openDestination(destination: SettingsDestination) {
        val target = when (destination) {
            SettingsDestination.App -> AppSettingsActivity::class.intent
            SettingsDestination.Network -> NetworkSettingsActivity::class.intent
            SettingsDestination.Override -> OverrideSettingsActivity::class.intent
            SettingsDestination.MetaFeature -> MetaFeatureSettingsActivity::class.intent
            SettingsDestination.Rules -> RulesActivity::class.intent
            SettingsDestination.Providers -> ProvidersActivity::class.intent
            SettingsDestination.Connections -> ConnectionsActivity::class.intent
            SettingsDestination.Logs ->
                if (LogcatService.running) LogcatActivity::class.intent else LogsActivity::class.intent
            SettingsDestination.About -> HelpActivity::class.intent
        }
        startActivity(target)
    }

    private fun navigate(target: SettingsNavTarget) {
        when (target) {
            SettingsNavTarget.Home -> {
                startActivity(
                    MainActivity::class.intent
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                )
                finish()
                overridePendingTransition(0, 0)
            }
            SettingsNavTarget.Profiles -> startActivity(ProfilesActivity::class.intent)
            SettingsNavTarget.Settings -> Unit // already here
        }
    }

    private fun toggleStatus() {
        if (clashRunning) {
            stopClashService()
        } else {
            launch { toggleClashOn() }
        }
    }

    private suspend fun toggleClashOn() {
        val active = withProfile { queryActive() }
        if (active == null || !active.imported) {
            Toast.makeText(
                this,
                com.github.kr328.clash.design.R.string.no_profile_selected,
                Toast.LENGTH_LONG,
            ).show()
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
            Toast.makeText(
                this,
                com.github.kr328.clash.design.R.string.unable_to_start_vpn,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Mirrors the side-drawer condition used across the View-based screens. */
    private fun useDrawerNav(): Boolean {
        if (TvUtils.isTv(this)) return true
        val cfg = resources.configuration
        return cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
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
