package com.github.kr328.clash

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.setContent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.HelpScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.update.UpdateChecker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HelpActivity : BaseActivity() {
    override suspend fun main() {
        val appVersion = withContext(Dispatchers.IO) {
            runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        }
        val coreVersion = BuildConfig.CORE_VERSION

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                HelpScreen(
                    appVersion = appVersion,
                    coreVersion = coreVersion,
                    onBack = { finish() },
                    onCheckUpdate = { launch { runUpdateCheck() } },
                    onOpenLink = {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        }
                    },
                )
            }
        }

        while (isActive) {
            events.receive()
        }
    }

    private suspend fun runUpdateCheck() {
        when (val result = UpdateChecker.check(this)) {
            is UpdateChecker.CheckResult.UpdateAvailable ->
                showUpdateAvailableDialog(result.tagName, result.downloadUrl)
            is UpdateChecker.CheckResult.UpToDate ->
                simpleDialog(R.string.update_not_found_title, getString(R.string.update_not_found_message))
            is UpdateChecker.CheckResult.Error ->
                simpleDialog(R.string.update_error_title, result.message)
        }
    }

    private fun showUpdateAvailableDialog(tagName: String, downloadUrl: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_message, tagName))
            .setPositiveButton(R.string.update_download) { _, _ ->
                UpdateChecker.startDownload(this, downloadUrl, tagName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun simpleDialog(titleRes: Int, message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
