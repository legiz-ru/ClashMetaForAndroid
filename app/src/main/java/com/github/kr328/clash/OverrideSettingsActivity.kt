package com.github.kr328.clash

import android.content.res.Configuration
import androidx.activity.compose.setContent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.OverrideSettingsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class OverrideSettingsActivity : BaseActivity() {
    private val resetRequests = Channel<Unit>(Channel.CONFLATED)

    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                OverrideSettingsScreen(
                    configuration = configuration,
                    onBack = { finish() },
                    onReset = { resetRequests.trySend(Unit) },
                )
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                resetRequests.onReceive {
                    if (requestResetConfirm()) {
                        defer {
                            withClash {
                                clearOverride(Clash.OverrideSlot.Persist)
                            }
                        }
                        finish()
                    }
                }
            }
        }
    }

    private suspend fun requestResetConfirm(): Boolean {
        return suspendCancellableCoroutine { ctx ->
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_override_settings)
                .setMessage(R.string.reset_override_settings_message)
                .setPositiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .show()

            dialog.setOnDismissListener {
                if (!ctx.isCompleted) ctx.resume(false)
            }

            ctx.invokeOnCancellation { dialog.dismiss() }
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
