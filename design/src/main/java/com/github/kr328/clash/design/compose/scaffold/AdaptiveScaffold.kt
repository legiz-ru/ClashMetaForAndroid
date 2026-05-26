package com.github.kr328.clash.design.compose.scaffold

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class NavDestination(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Single adaptive navigation shell used across form factors:
 *  - Compact width (phones)         -> bottom [NavigationBar]
 *  - Medium/Expanded (tablets, TV)  -> side [NavigationRail]
 *
 * Navigation items are standard Material 3 components, which are focusable, so D-pad
 * navigation on Android TV works without a separate leanback layout.
 */
@Composable
fun AdaptiveScaffold(
    widthSizeClass: WindowWidthSizeClass,
    destinations: List<NavDestination>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    if (useRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                destinations.forEach { dest ->
                    NavigationRailItem(
                        selected = dest.key == selectedKey,
                        onClick = { onSelect(dest.key) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) { content(Modifier.fillMaxSize()) }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            content(Modifier.weight(1f).fillMaxWidth())
            NavigationBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = dest.key == selectedKey,
                        onClick = { onSelect(dest.key) },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                    )
                }
            }
        }
    }
}
