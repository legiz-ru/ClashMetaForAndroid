package com.github.kr328.clash.design.compose.screen

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory

/**
 * About / help screen (1:1 with HelpDesign, restyled to MD3 Expressive).
 * Lists documentation and source links; tapping opens the URL.
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    PreferenceScaffold(title = stringResource(R.string.about), onBack = onBack) {
        SettingsCategory(stringResource(R.string.document))
        LinkRow(R.string.clash_meta_wiki, R.string.clash_meta_wiki_url, onOpenLink)

        SettingsCategory(stringResource(R.string.sources))
        LinkRow(R.string.clash_meta_core, R.string.clash_meta_core_url, onOpenLink)
        LinkRow(R.string.clash_meta_for_android, R.string.meta_github_url, onOpenLink)
        LinkRow(R.string.moshen_core, R.string.moshen_core_url, onOpenLink)
        LinkRow(R.string.mihomo_smart_core, R.string.mihomo_smart_core_url, onOpenLink)
        LinkRow(R.string.moshen_fork_core, R.string.moshen_fork_core_url, onOpenLink)
        LinkRow(R.string.cmfa_origin, R.string.cmfa_origin_url, onOpenLink)
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
