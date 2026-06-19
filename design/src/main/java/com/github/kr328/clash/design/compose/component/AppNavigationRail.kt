package com.github.kr328.clash.design.compose.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.SettingsNavTarget

/**
 * Side navigation rail for tablets/TV, mirroring the TvNavigationDrawer: a power
 * toggle in the header plus Home / Profiles / Settings destinations.
 */
@Composable
fun AppNavigationRail(
    selected: SettingsNavTarget,
    clashRunning: Boolean,
    onNavigate: (SettingsNavTarget) -> Unit,
    onToggleStatus: () -> Unit,
    hasProfiles: Boolean = true,
) {
    NavigationRail(
        header = {
            // Hide the power toggle until there's a profile to start; turn it red
            // while running so "disconnect" reads clearly.
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
            selected = selected == SettingsNavTarget.Home,
            onClick = { onNavigate(SettingsNavTarget.Home) },
            icon = { Icon(painterResource(R.drawable.ic_mdi_home), contentDescription = null) },
            label = { Text(stringResource(R.string.home)) },
        )
        NavigationRailItem(
            selected = selected == SettingsNavTarget.Profiles,
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
            selected = selected == SettingsNavTarget.Settings,
            onClick = { onNavigate(SettingsNavTarget.Settings) },
            icon = { Icon(painterResource(R.drawable.ic_mdi_cog), contentDescription = null) },
            label = { Text(stringResource(R.string.settings)) },
        )
    }
}
