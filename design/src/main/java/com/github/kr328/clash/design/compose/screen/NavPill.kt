package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

/**
 * Floating Home/Settings navigation pill shared by the home and settings
 * screens (June-style). The selected entry shows its icon and label on a filled
 * primary chip; the other shows just its icon.
 */
@Composable
fun NavPill(
    selected: SettingsNavTarget,
    onNavigate: (SettingsNavTarget) -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavPillItem(
                selected = selected == SettingsNavTarget.Home,
                icon = R.drawable.ic_mdi_home,
                label = stringResource(R.string.home),
                onClick = { onNavigate(SettingsNavTarget.Home) },
            )
            NavPillItem(
                selected = selected == SettingsNavTarget.Settings,
                icon = R.drawable.ic_mdi_cog,
                label = stringResource(R.string.settings),
                onClick = { onNavigate(SettingsNavTarget.Settings) },
            )
        }
    }
}

@Composable
private fun NavPillItem(
    selected: Boolean,
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (selected) scheme.primary else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
        if (selected) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}
