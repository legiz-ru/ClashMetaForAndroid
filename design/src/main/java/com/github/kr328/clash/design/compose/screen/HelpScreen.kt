package com.github.kr328.clash.design.compose.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory

private const val URL_CHANGELOG = "https://github.com/legiz-ru/Prizrak-Box/releases"
private const val URL_GITHUB = "https://github.com/legiz-ru"
private const val URL_TELEGRAM = "https://t.me/legiz_trashbag"
private const val URL_LICENSE = "https://github.com/legiz-ru/Prizrak-Box/blob/main/LICENSE"

/**
 * About screen (June-inspired). A hero header with the app logo, name and
 * version, quick update/changelog actions, a developer card, the upstream
 * source links and the license.
 */
@Composable
fun HelpScreen(
    appVersion: String,
    coreVersion: String,
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    PreferenceScaffold(title = stringResource(R.string.about), onBack = onBack) {
        item { Hero(appVersion = appVersion, coreVersion = coreVersion) }
        item {
            ActionButtons(
                onCheckUpdate = onCheckUpdate,
                onChangelog = { onOpenLink(URL_CHANGELOG) },
            )
        }

        item { SettingsCategory(stringResource(R.string.about_developer)) }
        item {
            DeveloperCard(
                onGithub = { onOpenLink(URL_GITHUB) },
                onTelegram = { onOpenLink(URL_TELEGRAM) },
            )
        }

        item { SettingsCategory(stringResource(R.string.document)) }
        item { LinkRow(R.string.clash_meta_wiki, R.string.clash_meta_wiki_url, onOpenLink) }

        item { SettingsCategory(stringResource(R.string.sources)) }
        item { LinkRow(R.string.clash_meta_core, R.string.clash_meta_core_url, onOpenLink) }
        item { LinkRow(R.string.clash_meta_for_android, R.string.meta_github_url, onOpenLink) }
        item { LinkRow(R.string.moshen_core, R.string.moshen_core_url, onOpenLink) }
        item { LinkRow(R.string.mihomo_smart_core, R.string.mihomo_smart_core_url, onOpenLink) }
        item { LinkRow(R.string.moshen_fork_core, R.string.moshen_fork_core_url, onOpenLink) }
        item { LinkRow(R.string.cmfa_origin, R.string.cmfa_origin_url, onOpenLink) }

        item { SettingsCategory(stringResource(R.string.about_license)) }
        item {
            PreferenceRow(
                title = stringResource(R.string.about_license),
                summary = "GPL-3.0",
                onClick = { onOpenLink(URL_LICENSE) },
            )
        }
    }
}

@Composable
private fun Hero(appVersion: String, coreVersion: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = scheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_clash),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(60.dp),
                )
            }
        }
        Text(
            text = "Prizrak-Box",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Surface(
            shape = CircleShape,
            color = scheme.surfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = "v$appVersion",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Text(
            text = "core $coreVersion",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ActionButtons(onCheckUpdate: () -> Unit, onChangelog: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(R.drawable.ic_mdi_update),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.check_for_updates), maxLines = 1)
        }
        FilledTonalButton(onClick = onChangelog, modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_assignment),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.about_changelog), maxLines = 1)
        }
    }
}

@Composable
private fun DeveloperCard(onGithub: () -> Unit, onTelegram: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Placeholder avatar (ghost) until the real image is dropped in.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_dev_avatar),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(CircleShape),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "legiz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.about_developer),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onGithub, label = { Text("GitHub") })
                AssistChip(onClick = onTelegram, label = { Text("Telegram") })
            }
        }
    }
}

@Composable
private fun LinkRow(
    @StringRes titleRes: Int,
    @StringRes urlRes: Int,
    onOpenLink: (String) -> Unit,
) {
    val url = stringResource(urlRes)
    PreferenceRow(
        title = stringResource(titleRes),
        summary = url,
        onClick = { onOpenLink(url) },
    )
}
