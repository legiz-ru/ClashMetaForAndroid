package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.Provider
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.type

/** Stable UI model for a single provider row. */
data class ProviderItem(
    val provider: Provider,
    val updatedAt: Long,
    val updating: Boolean,
)

/**
 * Proxy/rule providers list (1:1 with ProvidersDesign, restyled to MD3E).
 * Rows open the provider detail; the toolbar's sync action updates all
 * non-inline providers. The [tick] key forces the "updated N ago" subtitles
 * to recompute on the activity's one-minute timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    providers: List<ProviderItem>,
    tick: Int,
    onBack: () -> Unit,
    onOpen: (Provider) -> Unit,
    onUpdateAll: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.providers)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onUpdateAll) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_sync),
                                contentDescription = stringResource(R.string.update_all),
                            )
                        }
                    },
                )
            },
        ) { inner ->
            if (providers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                ) {
                    items(items = providers, key = { "${it.provider.type}:${it.provider.name}" }) { item ->
                        Box(modifier = Modifier.animateItem()) {
                            ProviderRow(item = item, tick = tick, onClick = { onOpen(item.provider) })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderRow(item: ProviderItem, tick: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val subtitle = remember(item.provider, item.updatedAt, tick) {
        val typeStr = item.provider.type(context)
        if (item.provider.vehicleType == Provider.VehicleType.Inline) {
            typeStr
        } else {
            val elapsed = (System.currentTimeMillis() - item.updatedAt).elapsedIntervalString(context)
            "$typeStr • $elapsed"
        }
    }
    ListItem(
        headlineContent = { Text(item.provider.name) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            if (item.updating) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
