package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsItem
import com.github.kr328.clash.service.model.Profile

/**
 * Profile editor (1:1 with PropertiesDesign, restyled to MD3E). All text-input
 * dialogs, template selection, QR scanning and committing stay in
 * PropertiesActivity; this screen only renders the fields and emits intents.
 */
@Composable
fun PropertiesScreen(
    profile: Profile,
    proxyLinks: List<String>,
    processing: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onEditName: () -> Unit,
    onEditUrl: () -> Unit,
    onEditInterval: () -> Unit,
    onEditAgeKey: () -> Unit,
    onBrowseFiles: () -> Unit,
    onSelectTemplate: () -> Unit,
    onAddProxyLinks: () -> Unit,
    onEditProxyLink: (Int, String) -> Unit,
    onDeleteProxyLink: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    val nameEnabled = profile.profileTitle.isEmpty()
    val showUrl = profile.type != Profile.Type.Converted
    val urlEnabled = profile.type != Profile.Type.File
    val intervalEnabled = profile.type != Profile.Type.File && profile.profileUpdateInterval == 0
    val showProxy = profile.type == Profile.Type.Converted
    val showTemplate = profile.type == Profile.Type.Converted && profile.allowTemplateSelection
    val showHwid = profile.hwidActive
    val showAgeKey = profile.type == Profile.Type.File || profile.type == Profile.Type.Url
    val ageKeyActive = profile.ageSecretKey.isNotEmpty()

    PreferenceScaffold(
        title = stringResource(R.string.properties),
        onBack = onBack,
        actions = {
            if (showAgeKey) {
                IconButton(onClick = onEditAgeKey) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_key),
                        contentDescription = stringResource(R.string.age_secret_key),
                        tint = if (ageKeyActive) scheme.primary else scheme.onSurfaceVariant,
                    )
                }
            }
            if (processing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onSave) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_save),
                        contentDescription = stringResource(R.string.save),
                    )
                }
            }
        },
    ) {
        item {
            PreferenceRow(
                title = stringResource(R.string.name),
                summary = profile.name.ifEmpty { stringResource(R.string.profile_name) },
                icon = R.drawable.ic_outline_label,
                enabled = nameEnabled,
                onClick = onEditName,
            )
        }

        if (showUrl) {
            item {
                PreferenceRow(
                    title = stringResource(R.string.url),
                    summary = profile.source.ifEmpty { stringResource(R.string.accept_http_content) },
                    icon = R.drawable.ic_outline_inbox,
                    enabled = urlEnabled,
                    onClick = onEditUrl,
                )
            }
        }

        if (showProxy) {
            itemsIndexed(proxyLinks) { index, link ->
                ProxyLinkRow(
                    link = link,
                    onClick = { onEditProxyLink(index, link) },
                    onDelete = { onDeleteProxyLink(index) },
                )
            }
            item {
                SettingsItem(
                    icon = R.drawable.ic_baseline_add,
                    title = stringResource(R.string.add_proxy_links),
                    subtitle = stringResource(R.string.add_proxy_links_desc),
                    showChevron = false,
                    onClick = onAddProxyLinks,
                )
            }
        }

        item {
            PreferenceRow(
                title = stringResource(R.string.auto_update),
                summary = if (profile.interval == 0L) {
                    stringResource(R.string.disabled)
                } else {
                    stringResource(R.string.format_minutes, profile.interval / 1000 / 60)
                },
                icon = R.drawable.ic_outline_update,
                enabled = intervalEnabled,
                onClick = onEditInterval,
            )
        }

        item {
            SettingsItem(
                icon = R.drawable.ic_outline_folder,
                title = stringResource(R.string.browse_files),
                subtitle = stringResource(R.string.browse_configuration_providers),
                onClick = onBrowseFiles,
            )
        }

        if (showTemplate) {
            item {
                SettingsItem(
                    icon = R.drawable.ic_outline_article,
                    title = stringResource(R.string.select_template),
                    subtitle = stringResource(R.string.select_template_desc),
                    onClick = onSelectTemplate,
                )
            }
        }

        if (showHwid) {
            item {
                SettingsItem(
                    icon = R.drawable.ic_outline_info,
                    title = stringResource(R.string.hwid_active_title),
                    subtitle = stringResource(R.string.hwid_active_desc),
                    showChevron = false,
                    onClick = {},
                )
            }
        }
    }
}

/**
 * Display label for a proxy link: the URL-decoded text after `#` (the remark),
 * falling back to the host, then the raw link. Mirrors the old ProxyLinkAdapter.
 */
private fun proxyLinkAlias(url: String): String {
    val afterHash = url.substringAfterLast("#", "").trim()
    if (afterHash.isNotBlank()) {
        return runCatching { java.net.URLDecoder.decode(afterHash, "UTF-8") }.getOrDefault(afterHash)
    }
    return runCatching {
        url.substringAfter("://").substringBefore("?").substringBefore("/").ifBlank { url }
    }.getOrDefault(url)
}

@Composable
private fun ProxyLinkRow(
    link: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_outline_inbox),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = proxyLinkAlias(link),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_outline_delete),
                contentDescription = stringResource(R.string.delete),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}
