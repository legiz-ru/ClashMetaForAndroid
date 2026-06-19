package com.github.kr328.clash.design.compose.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SettingsItem
import com.github.kr328.clash.design.compose.theme.ClashTheme

/** Sub-screens reachable from the settings hub. */
enum class SettingsDestination {
    App, Network, Override, MetaFeature,
    Rules, Providers, Connections,
    Logs, About,
}

/** Top-level navigation targets (bottom bar on phones, side rail on tablets/TV). */
enum class SettingsNavTarget { Home, Profiles, Settings }

/**
 * Settings hub. Sections that are only meaningful while a profile is running
 * (Rules / Providers / Connections) are grouped under the "Active profile" header
 * and shown only when [clashRunning].
 *
 * @param expanded when true (tablet/TV) a side navigation rail is shown instead of
 *   the bottom navigation bar — matching the current TvNavigationDrawer behaviour.
 */
@Composable
fun SettingsScreen(
    expanded: Boolean,
    clashRunning: Boolean,
    onOpen: (SettingsDestination) -> Unit,
    onNavigate: (SettingsNavTarget) -> Unit,
    onToggleStatus: () -> Unit,
    modifier: Modifier = Modifier,
    hasProfiles: Boolean = true,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                SettingsRail(
                    clashRunning = clashRunning,
                    onNavigate = onNavigate,
                    onToggleStatus = onToggleStatus,
                    hasProfiles = hasProfiles,
                )
                SettingsList(
                    clashRunning = clashRunning,
                    onOpen = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        } else {
            Scaffold(
                bottomBar = {
                    SettingsBottomBar(onNavigate = onNavigate)
                },
            ) { inner ->
                SettingsList(
                    clashRunning = clashRunning,
                    onOpen = onOpen,
                    modifier = Modifier.padding(inner),
                )
            }
        }
    }
}

@Composable
private fun SettingsList(
    clashRunning: Boolean,
    onOpen: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        SettingsCategory(stringResource(R.string.settings_general))
        SettingsItem(R.drawable.ic_baseline_settings, stringResource(R.string.app)) {
            onOpen(SettingsDestination.App)
        }
        SettingsItem(R.drawable.ic_baseline_info, stringResource(R.string.about)) {
            onOpen(SettingsDestination.About)
        }

        SettingsCategory(stringResource(R.string.settings_core))
        SettingsItem(R.drawable.ic_baseline_dns, stringResource(R.string.network)) {
            onOpen(SettingsDestination.Network)
        }
        SettingsItem(R.drawable.ic_baseline_extension, stringResource(R.string.override)) {
            onOpen(SettingsDestination.Override)
        }
        SettingsItem(R.drawable.ic_baseline_meta, stringResource(R.string.meta_features)) {
            onOpen(SettingsDestination.MetaFeature)
        }
        SettingsItem(R.drawable.ic_baseline_assignment, stringResource(R.string.logs)) {
            onOpen(SettingsDestination.Logs)
        }

        // Only available while a profile is running.
        AnimatedVisibility(visible = clashRunning) {
            Column {
                SettingsCategory(stringResource(R.string.settings_active_profile))
                SettingsItem(R.drawable.ic_arrow_decision_outline, stringResource(R.string.rules)) {
                    onOpen(SettingsDestination.Rules)
                }
                SettingsItem(
                    R.drawable.ic_baseline_swap_vertical_circle,
                    stringResource(R.string.providers),
                ) {
                    onOpen(SettingsDestination.Providers)
                }
                SettingsItem(R.drawable.ic_baseline_view_list, stringResource(R.string.connections)) {
                    onOpen(SettingsDestination.Connections)
                }
            }
        }
    }
}

@Composable
private fun SettingsBottomBar(onNavigate: (SettingsNavTarget) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        NavPill(selected = SettingsNavTarget.Settings, onNavigate = onNavigate)
    }
}

@Composable
private fun SettingsRail(
    clashRunning: Boolean,
    onNavigate: (SettingsNavTarget) -> Unit,
    onToggleStatus: () -> Unit,
    hasProfiles: Boolean,
) {
    NavigationRail(
        header = {
            if (hasProfiles || clashRunning) {
                FilledIconButton(
                    onClick = onToggleStatus,
                    colors = if (clashRunning) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        IconButtonDefaults.filledIconButtonColors()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mdi_power),
                        contentDescription = stringResource(
                            if (clashRunning) R.string.stop else R.string.start,
                        ),
                    )
                }
            }
        },
    ) {
        NavigationRailItem(
            selected = false,
            onClick = { onNavigate(SettingsNavTarget.Home) },
            icon = {
                Icon(painter = painterResource(R.drawable.ic_mdi_home), contentDescription = null)
            },
            label = { Text(stringResource(R.string.home)) },
        )
        NavigationRailItem(
            selected = false,
            onClick = { onNavigate(SettingsNavTarget.Profiles) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_mdi_arrange_bring_forward),
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.profiles)) },
        )
        NavigationRailItem(
            selected = true,
            onClick = { onNavigate(SettingsNavTarget.Settings) },
            icon = {
                Icon(painter = painterResource(R.drawable.ic_mdi_cog), contentDescription = null)
            },
            label = { Text(stringResource(R.string.settings)) },
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPhonePreview() {
    ClashTheme(darkTheme = false) {
        SettingsScreen(
            expanded = false,
            clashRunning = true,
            onOpen = {},
            onNavigate = {},
            onToggleStatus = {},
        )
    }
}

@Preview(widthDp = 720, heightDp = 420)
@Composable
private fun SettingsScreenExpandedPreview() {
    ClashTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                expanded = true,
                clashRunning = false,
                onOpen = {},
                onNavigate = {},
                onToggleStatus = {},
            )
        }
    }
}
