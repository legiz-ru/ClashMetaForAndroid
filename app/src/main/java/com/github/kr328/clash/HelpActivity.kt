package com.github.kr328.clash

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.setContent
import com.github.kr328.clash.design.compose.screen.HelpScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import kotlinx.coroutines.isActive

class HelpActivity : BaseActivity() {
    override suspend fun main() {
        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                HelpScreen(
                    onBack = { finish() },
                    onOpenLink = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                    },
                )
            }
        }

        while (isActive) {
            events.receive()
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
