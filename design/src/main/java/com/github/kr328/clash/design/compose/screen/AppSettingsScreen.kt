package com.github.kr328.clash.design.compose.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SingleChoiceDialog
import com.github.kr328.clash.design.compose.component.SwitchPreference
import com.github.kr328.clash.design.model.DarkMode

/**
 * App settings screen (1:1 with AppSettingsDesign, restyled to MD3 Expressive).
 */
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    autoRestart: Boolean,
    onAutoRestart: (Boolean) -> Unit,
    darkMode: DarkMode,
    darkModeOptions: List<Pair<DarkMode, String>>,
    onDarkMode: (DarkMode) -> Unit,
    languageSummary: String,
    onLanguage: () -> Unit,
    delayDots: Boolean,
    onDelayDots: (Boolean) -> Unit,
    hideIcon: Boolean,
    onHideIcon: (Boolean) -> Unit,
    hideRecents: Boolean,
    onHideRecents: (Boolean) -> Unit,
    dynamicNotification: Boolean,
    dynamicNotificationEnabled: Boolean,
    onDynamicNotification: (Boolean) -> Unit,
    sendHwid: Boolean,
    onSendHwid: (Boolean) -> Unit,
    onCustomTemplate: () -> Unit,
) {
    var showDarkDialog by remember { mutableStateOf(false) }

    PreferenceScaffold(title = stringResource(R.string.app), onBack = onBack) {
        item { SettingsCategory(stringResource(R.string.behavior)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.auto_restart),
                summary = stringResource(R.string.allow_clash_auto_restart),
                icon = R.drawable.ic_baseline_restore,
                checked = autoRestart,
                onCheckedChange = onAutoRestart,
            )
        }

        item { SettingsCategory(stringResource(R.string.interface_)) }
        item {
            PreferenceRow(
                title = stringResource(R.string.dark_mode),
                summary = darkModeOptions.firstOrNull { it.first == darkMode }?.second,
                icon = R.drawable.ic_baseline_brightness_4,
                onClick = { showDarkDialog = true },
            )
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.language),
                summary = languageSummary,
                icon = R.drawable.ic_baseline_language,
                onClick = onLanguage,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.delay_display),
                summary = stringResource(R.string.delay_display_dots),
                icon = R.drawable.ic_baseline_speed,
                checked = delayDots,
                onCheckedChange = onDelayDots,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.hide_app_icon_title),
                summary = stringResource(R.string.hide_app_icon_desc),
                icon = R.drawable.ic_baseline_hide,
                checked = hideIcon,
                onCheckedChange = onHideIcon,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.hide_from_recents_title),
                summary = stringResource(R.string.hide_from_recents_desc),
                icon = R.drawable.ic_baseline_stack,
                checked = hideRecents,
                onCheckedChange = onHideRecents,
            )
        }

        item { SettingsCategory(stringResource(R.string.service)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.show_traffic),
                summary = stringResource(R.string.show_traffic_summary),
                icon = R.drawable.ic_baseline_domain,
                checked = dynamicNotification,
                enabled = dynamicNotificationEnabled,
                onCheckedChange = onDynamicNotification,
            )
        }

        item { SettingsCategory(stringResource(R.string.privacy)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.send_hwid_title),
                summary = stringResource(R.string.send_hwid_desc),
                icon = R.drawable.ic_baseline_assignment,
                checked = sendHwid,
                onCheckedChange = onSendHwid,
            )
        }

        item { SettingsCategory(stringResource(R.string.templates)) }
        item {
            PreferenceRow(
                title = stringResource(R.string.custom_template),
                summary = stringResource(R.string.custom_template_desc),
                onClick = onCustomTemplate,
            )
        }
    }

    if (showDarkDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.dark_mode),
            options = darkModeOptions,
            selected = darkMode,
            onSelect = onDarkMode,
            onDismiss = { showDarkDialog = false },
        )
    }
}
