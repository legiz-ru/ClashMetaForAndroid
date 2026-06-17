package com.github.kr328.clash

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.setContent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.ApkBrokenScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import kotlinx.coroutines.isActive

class ApkBrokenActivity : BaseActivity() {
    override suspend fun main() {
        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                ApkBrokenScreen(
                    onBack = { finish() },
                    onOpenReleases = {
                        startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setData(Uri.parse(getString(R.string.meta_github_url)))
                        )
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
