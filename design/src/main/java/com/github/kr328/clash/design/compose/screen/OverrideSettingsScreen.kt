package com.github.kr328.clash.design.compose.screen

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.EditableListPreference
import com.github.kr328.clash.design.compose.component.EditableMapPreference
import com.github.kr328.clash.design.compose.component.EditableTextPreference
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SingleChoiceDialog
import com.github.kr328.clash.design.compose.component.NullableTextAdapter
import com.github.kr328.clash.design.compose.component.TextAdapter

/**
 * Override settings screen (1:1 with OverrideSettingsDesign, restyled to MD3E).
 * Edits the [configuration] object in place; the Activity persists it on exit.
 */
@Composable
fun OverrideSettingsScreen(
    configuration: ConfigurationOverride,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    // dns.enable as snapshot state so dependent DNS rows' enabled flag updates live when
    // the strategy row changes it (read as `dnsEnable != false` inside each item). Editable
    // values stay in sync inside each self-updating preference component — no rev/bump.
    var dnsEnable by remember { mutableStateOf(configuration.dns.enable) }

    val booleanOptions: List<Pair<Boolean?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        true to stringResource(R.string.enabled),
        false to stringResource(R.string.disabled),
    )
    val modeOptions: List<Pair<TunnelState.Mode?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        TunnelState.Mode.Direct to stringResource(R.string.direct_mode),
        TunnelState.Mode.Global to stringResource(R.string.global_mode),
        TunnelState.Mode.Rule to stringResource(R.string.rule_mode),
    )
    val logLevelOptions: List<Pair<LogMessage.Level?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        LogMessage.Level.Info to stringResource(R.string.info),
        LogMessage.Level.Warning to stringResource(R.string.warning),
        LogMessage.Level.Error to stringResource(R.string.error),
        LogMessage.Level.Debug to stringResource(R.string.debug),
        LogMessage.Level.Silent to stringResource(R.string.silent),
    )
    val dnsStrategyOptions: List<Pair<Boolean?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        true to stringResource(R.string.force_enable),
        false to stringResource(R.string.use_built_in),
    )
    val enhancedModeOptions: List<Pair<ConfigurationOverride.DnsEnhancedMode?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        ConfigurationOverride.DnsEnhancedMode.None to stringResource(R.string.disabled),
        ConfigurationOverride.DnsEnhancedMode.FakeIp to stringResource(R.string.fakeip),
        ConfigurationOverride.DnsEnhancedMode.Mapping to stringResource(R.string.mapping),
    )
    val filterModeOptions: List<Pair<ConfigurationOverride.FilterMode?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        ConfigurationOverride.FilterMode.BlackList to stringResource(R.string.blacklist),
        ConfigurationOverride.FilterMode.WhiteList to stringResource(R.string.whitelist),
        ConfigurationOverride.FilterMode.Rule to stringResource(R.string.rule),
    )

    PreferenceScaffold(
        title = stringResource(R.string.override),
        onBack = onBack,
        actions = {
            IconButton(onClick = onReset) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_clear_all),
                    contentDescription = stringResource(R.string.reset_override_settings),
                )
            }
        },
    ) {
        item { SettingsCategory(stringResource(R.string.general)) }

        item { PortPreference(stringResource(R.string.http_port), configuration.httpPort) { configuration.httpPort = it } }
        item { PortPreference(stringResource(R.string.socks_port), configuration.socksPort) { configuration.socksPort = it } }
        item { PortPreference(stringResource(R.string.redirect_port), configuration.redirectPort) { configuration.redirectPort = it } }
        item { PortPreference(stringResource(R.string.tproxy_port), configuration.tproxyPort) { configuration.tproxyPort = it } }
        item { PortPreference(stringResource(R.string.mixed_port), configuration.mixedPort) { configuration.mixedPort = it } }

        item {
            EditableListPreference(
                title = stringResource(R.string.authentication),
                value = configuration.authentication,
                adapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                onValueChange = { configuration.authentication = it },
            )
        }

        item { SelectablePreference(stringResource(R.string.allow_lan), booleanOptions, configuration.allowLan) { configuration.allowLan = it } }
        item { SelectablePreference(stringResource(R.string.ipv6), booleanOptions, configuration.ipv6) { configuration.ipv6 = it } }

        item { StringPreference(stringResource(R.string.bind_address), configuration.bindAddress, stringResource(R.string.default_)) { configuration.bindAddress = it } }
        item { StringPreference(stringResource(R.string.external_controller), configuration.externalController, stringResource(R.string.default_)) { configuration.externalController = it } }
        item { StringPreference(stringResource(R.string.external_controller_tls), configuration.externalControllerTLS, stringResource(R.string.default_)) { configuration.externalControllerTLS = it } }

        item {
            EditableListPreference(
                title = stringResource(R.string.allow_origins),
                value = configuration.externalControllerCors.allowOrigins,
                adapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                onValueChange = { configuration.externalControllerCors.allowOrigins = it },
            )
        }
        item {
            SelectablePreference(stringResource(R.string.allow_private_network), booleanOptions, configuration.externalControllerCors.allowPrivateNetwork) {
                configuration.externalControllerCors.allowPrivateNetwork = it
            }
        }

        item { StringPreference(stringResource(R.string.secret), configuration.secret, stringResource(R.string.default_)) { configuration.secret = it } }

        item { SelectablePreference(stringResource(R.string.mode), modeOptions, configuration.mode) { configuration.mode = it } }

        item { SelectablePreference(stringResource(R.string.log_level), logLevelOptions, configuration.logLevel) { configuration.logLevel = it } }

        item {
            EditableMapPreference(
                title = stringResource(R.string.hosts),
                value = configuration.hosts,
                keyAdapter = TextAdapter.String,
                valueAdapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                onValueChange = { configuration.hosts = it },
            )
        }

        item { SettingsCategory(stringResource(R.string.dns)) }

        item { SelectablePreference(stringResource(R.string.strategy), dnsStrategyOptions, configuration.dns.enable) { configuration.dns.enable = it; dnsEnable = it } }

        item { SelectablePreference(stringResource(R.string.prefer_h3), booleanOptions, configuration.dns.preferH3, dnsEnable != false) { configuration.dns.preferH3 = it } }
        item { StringPreference(stringResource(R.string.listen), configuration.dns.listen, stringResource(R.string.disabled), dnsEnable != false) { configuration.dns.listen = it } }
        item { SelectablePreference(stringResource(R.string.append_system_dns), booleanOptions, configuration.app.appendSystemDns, dnsEnable != false) { configuration.app.appendSystemDns = it } }
        item { SelectablePreference(stringResource(R.string.ipv6), booleanOptions, configuration.dns.ipv6, dnsEnable != false) { configuration.dns.ipv6 = it } }
        item { SelectablePreference(stringResource(R.string.use_hosts), booleanOptions, configuration.dns.useHosts, dnsEnable != false) { configuration.dns.useHosts = it } }

        item { SelectablePreference(stringResource(R.string.enhanced_mode), enhancedModeOptions, configuration.dns.enhancedMode, dnsEnable != false) { configuration.dns.enhancedMode = it } }

        item { DnsListPreference(stringResource(R.string.name_server), configuration.dns.nameServer, dnsEnable != false) { configuration.dns.nameServer = it } }
        item { DnsListPreference(stringResource(R.string.fallback), configuration.dns.fallback, dnsEnable != false) { configuration.dns.fallback = it } }
        item { DnsListPreference(stringResource(R.string.default_name_server), configuration.dns.defaultServer, dnsEnable != false) { configuration.dns.defaultServer = it } }
        item { DnsListPreference(stringResource(R.string.fakeip_filter), configuration.dns.fakeIpFilter, dnsEnable != false) { configuration.dns.fakeIpFilter = it } }

        item { SelectablePreference(stringResource(R.string.fakeip_filter_mode), filterModeOptions, configuration.dns.fakeIPFilterMode, dnsEnable != false) { configuration.dns.fakeIPFilterMode = it } }

        item { SelectablePreference(stringResource(R.string.geoip_fallback), booleanOptions, configuration.dns.fallbackFilter.geoIp, dnsEnable != false) { configuration.dns.fallbackFilter.geoIp = it } }
        item { StringPreference(stringResource(R.string.geoip_fallback_code), configuration.dns.fallbackFilter.geoIpCode, stringResource(R.string.raw_cn), dnsEnable != false) { configuration.dns.fallbackFilter.geoIpCode = it } }
        item { DnsListPreference(stringResource(R.string.domain_fallback), configuration.dns.fallbackFilter.domain, dnsEnable != false) { configuration.dns.fallbackFilter.domain = it } }
        item { DnsListPreference(stringResource(R.string.ipcidr_fallback), configuration.dns.fallbackFilter.ipcidr, dnsEnable != false) { configuration.dns.fallbackFilter.ipcidr = it } }

        item {
            EditableMapPreference(
                title = stringResource(R.string.name_server_policy),
                value = configuration.dns.nameserverPolicy,
                keyAdapter = TextAdapter.String,
                valueAdapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                enabled = dnsEnable != false,
                onValueChange = { configuration.dns.nameserverPolicy = it },
            )
        }
    }
}

@Composable
private fun PortPreference(title: String, value: Int?, onChange: (Int?) -> Unit) {
    EditableTextPreference(
        title = title,
        value = value,
        adapter = NullableTextAdapter.Port,
        placeholder = stringResource(R.string.dont_modify),
        empty = stringResource(R.string.disabled),
        numeric = true,
        onValueChange = onChange,
    )
}

@Composable
private fun StringPreference(
    title: String,
    value: String?,
    empty: String,
    enabled: Boolean = true,
    onChange: (String?) -> Unit,
) {
    EditableTextPreference(
        title = title,
        value = value,
        adapter = NullableTextAdapter.String,
        placeholder = stringResource(R.string.dont_modify),
        empty = empty,
        enabled = enabled,
        onValueChange = onChange,
    )
}

@Composable
private fun DnsListPreference(
    title: String,
    value: List<String>?,
    enabled: Boolean,
    onChange: (List<String>?) -> Unit,
) {
    EditableListPreference(
        title = title,
        value = value,
        adapter = TextAdapter.String,
        placeholder = stringResource(R.string.dont_modify),
        enabled = enabled,
        onValueChange = onChange,
    )
}

@Composable
internal fun <T> SelectablePreference(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    // Self-updating state so the row summary reflects the new selection immediately
    // (re-seeded if the caller passes a different value).
    var current by remember(selected) { mutableStateOf(selected) }
    PreferenceRow(
        title = title,
        summary = options.firstOrNull { it.first == current }?.second,
        enabled = enabled,
        onClick = { show = true },
    )
    if (show) {
        SingleChoiceDialog(
            title = title,
            options = options,
            selected = current,
            onSelect = { current = it; onSelect(it) },
            onDismiss = { show = false },
        )
    }
}
