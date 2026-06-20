package com.github.kr328.clash

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.componentName
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.AppSettingsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.store.UiStore.Companion.mainActivityAlias
import com.github.kr328.clash.service.TemplateManager
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.GetContentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSettingsActivity : BaseActivity(), Behavior {
    private val srvStore by lazy { ServiceStore(this) }

    private val autoRestartFlow = MutableStateFlow(false)
    private val darkModeFlow = MutableStateFlow(DarkMode.Auto)
    private val delayDotsFlow = MutableStateFlow(false)
    private val hideIconFlow = MutableStateFlow(false)
    private val hideRecentsFlow = MutableStateFlow(false)
    private val dynamicNotificationFlow = MutableStateFlow(false)
    private val sendHwidFlow = MutableStateFlow(false)
    private val languageSummaryFlow = MutableStateFlow("")

    override suspend fun main() {
        autoRestartFlow.value = autoRestart
        darkModeFlow.value = uiStore.darkMode
        delayDotsFlow.value = uiStore.delayDisplayDots
        hideIconFlow.value = uiStore.hideAppIcon
        hideRecentsFlow.value = uiStore.hideFromRecents
        dynamicNotificationFlow.value = srvStore.dynamicNotification
        sendHwidFlow.value = uiStore.sendHwid
        languageSummaryFlow.value = getLanguageDisplayName()

        setContent {
            val autoRestartValue by autoRestartFlow.collectAsStateWithLifecycle()
            val darkMode by darkModeFlow.collectAsStateWithLifecycle()
            val delayDots by delayDotsFlow.collectAsStateWithLifecycle()
            val hideIcon by hideIconFlow.collectAsStateWithLifecycle()
            val hideRecents by hideRecentsFlow.collectAsStateWithLifecycle()
            val dynamicNotification by dynamicNotificationFlow.collectAsStateWithLifecycle()
            val sendHwid by sendHwidFlow.collectAsStateWithLifecycle()
            val languageSummary by languageSummaryFlow.collectAsStateWithLifecycle()

            val darkOptions = if (uiStore.summerModeUnlocked) {
                listOf(
                    DarkMode.Auto to androidx.compose.ui.res.stringResource(R.string.follow_system_android_10),
                    DarkMode.ForceLight to androidx.compose.ui.res.stringResource(R.string.always_light),
                    DarkMode.ForceDark to androidx.compose.ui.res.stringResource(R.string.always_dark),
                    DarkMode.AlwaysSummer to androidx.compose.ui.res.stringResource(R.string.always_summer),
                )
            } else {
                listOf(
                    DarkMode.Auto to androidx.compose.ui.res.stringResource(R.string.follow_system_android_10),
                    DarkMode.ForceLight to androidx.compose.ui.res.stringResource(R.string.always_light),
                    DarkMode.ForceDark to androidx.compose.ui.res.stringResource(R.string.always_dark),
                )
            }

            ClashTheme(variant = currentThemeVariant()) {
                AppSettingsScreen(
                    onBack = { finish() },
                    autoRestart = autoRestartValue,
                    onAutoRestart = { autoRestart = it; autoRestartFlow.value = it },
                    darkMode = darkMode,
                    darkModeOptions = darkOptions,
                    onDarkMode = ::setDarkMode,
                    languageSummary = languageSummary,
                    onLanguage = ::openLanguageSettings,
                    delayDots = delayDots,
                    onDelayDots = { uiStore.delayDisplayDots = it; delayDotsFlow.value = it },
                    hideIcon = hideIcon,
                    onHideIcon = {
                        uiStore.hideAppIcon = it
                        hideIconFlow.value = it
                        onHideIconChange(it)
                    },
                    hideRecents = hideRecents,
                    onHideRecents = {
                        uiStore.hideFromRecents = it
                        hideRecentsFlow.value = it
                        recreateAll()
                    },
                    dynamicNotification = dynamicNotification,
                    dynamicNotificationEnabled = !clashRunning,
                    onDynamicNotification = {
                        srvStore.dynamicNotification = it
                        dynamicNotificationFlow.value = it
                    },
                    sendHwid = sendHwid,
                    onSendHwid = { uiStore.sendHwid = it; sendHwidFlow.value = it },
                    onCustomTemplate = { launch { selectCustomTemplate() } },
                )
            }
        }

        while (isActive) {
            when (events.receive()) {
                Event.ClashStart, Event.ClashStop, Event.ServiceRecreated -> recreate()
                Event.ActivityStart -> languageSummaryFlow.value = getLanguageDisplayName()
                else -> Unit
            }
        }
    }

    private fun setDarkMode(mode: DarkMode) {
        uiStore.darkMode = mode
        darkModeFlow.value = mode
        recreateAll()
    }

    private fun recreateAll() {
        ApplicationObserver.createdActivities.forEach { it.recreate() }
    }

    private suspend fun selectCustomTemplate() {
        val uri = startActivityForResult(GetContentCompat(), "*/*") ?: return
        val content = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        if (!content.isNullOrBlank()) {
            val customFile = filesDir.resolve("custom_template.yaml")
            withContext(Dispatchers.IO) {
                customFile.writeText(content, Charsets.UTF_8)
            }
            TemplateManager.setCustomTemplatePath(this, customFile.absolutePath)
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
            ShortcutManagerCompat.removeAllDynamicShortcuts(this)
        }
        packageManager.setComponentEnabledSetting(
            mainActivityAlias,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun getLanguageDisplayName(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales == LocaleListCompat.getEmptyLocaleList() || locales.isEmpty) {
            return getString(R.string.language_follow_system)
        }
        val locale = locales[0] ?: return getString(R.string.language_follow_system)
        return locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
    }

    private fun openLanguageSettings() {
        val uri = Uri.fromParts("package", packageName, null)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, uri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
        }
        startActivity(intent)
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
