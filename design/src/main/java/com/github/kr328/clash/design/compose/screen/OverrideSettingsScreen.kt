package com.github.kr328.clash.design.compose.screen

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var rev by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r = rev
    val bump = { rev++ }

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
    val dnsEnabled = configuration.dns.enable != false

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

        item { PortPreference(stringResource(R.string.http_port), configuration.httpPort) { configuration.httpPort = it; bump() } }
        item { PortPreference(stringResource(R.string.socks_port), configuration.socksPort) { configuration.socksPort = it; bump() } }
        item { PortPreference(stringResource(R.string.redirect_port), configuration.redirectPort) { configuration.redirectPort = it; bump() } }
        item { PortPreference(stringResource(R.string.tproxy_port), configuration.tproxyPort) { configuration.tproxyPort = it; bump() } }
        item { PortPreference(stringResource(R.string.mixed_port), configuration.mixedPort) { configuration.mixedPort = it; bump() } }

        item {
            EditableListPreference(
                title = stringResource(R.string.authentication),
                value = configuration.authentication,
                adapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                onValueChange = { configuration.authentication = it; bump() },
            )
        }

        item { SelectablePreference(stringResource(R.string.allow_lan), booleanOptions, configuration.allowLan) { configuration.allowLan = it; bump() } }
        item { SelectablePreference(stringResource(R.string.ipv6), booleanOptions, configuration.ipv6) { configuration.ipv6 = it; bump() } }

        item { StringPreference(stringResource(R.string.bind_address), configuration.bindAddress, stringResource(R.string.default_)) { configuration.bindAddress = it; bump() } }
        item { StringPreference(stringResource(R.string.external_controller), configuration.externalController, stringResource(R.string.default_)) { configuration.externalController = it; bump() } }
        item { StringPreference(stringResource(R.string.external_controller_tls), configuration.externalControllerTLS, stringResource(R.string.default_)) { configuration.externalControllerTLS = it; bump() } }

        item {
            EditableListPreference(
                title = stringResource(R.string.allow_origins),
                value = configuration.externalControllerCors.allowOrigins,
                adapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                onValueChange = { configuration.externalControllerCors.allowOrigins = it; bump() },
            )
        }
        item {
            SelectablePreference(stringResource(R.string.allow_private_network), booleanOptions, configuration.externalControllerCors.allowPrivateNetwork) {
                configuration.externalControllerCors.allowPrivateNetwork = it; bump()
            }
        }

        item { StringPreference(stringResource(R.string.secret), configuration.secret, stringResource(R.string.default_)) { configuration.secret = it; bump() } }

        item { SelectablePreference(stringResource(R.string.mode), modeOptions, configuration.mode) { configuration.mode = it; bump() } }

        item { SelectablePreference(stringResource(R.string.log_level), logLevelOptions, configuration.logLevel) { configuration.logLevel = it; bump() } }

        item {
            EditableMapPreference(
                title = stringResource(R.string.hosts),
                value = configuration.hosts,
                keyAdapter = TextAdapter.String,
                valueAdapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                onValueChange = { configuration.hosts = it; bump() },
            )
        }

        item { SettingsCategory(stringResource(R.string.dns)) }

        item { SelectablePreference(stringResource(R.string.strategy), dnsStrategyOptions, configuration.dns.enable) { configuration.dns.enable = it; bump() } }

        item { SelectablePreference(stringResource(R.string.prefer_h3), booleanOptions, configuration.dns.preferH3, dnsEnabled) { configuration.dns.preferH3 = it; bump() } }
        item { StringPreference(stringResource(R.string.listen), configuration.dns.listen, stringResource(R.string.disabled), dnsEnabled) { configuration.dns.listen = it; bump() } }
        item { SelectablePreference(stringResource(R.string.append_system_dns), booleanOptions, configuration.app.appendSystemDns, dnsEnabled) { configuration.app.appendSystemDns = it; bump() } }
        item { SelectablePreference(stringResource(R.string.ipv6), booleanOptions, configuration.dns.ipv6, dnsEnabled) { configuration.dns.ipv6 = it; bump() } }
        item { SelectablePreference(stringResource(R.string.use_hosts), booleanOptions, configuration.dns.useHosts, dnsEnabled) { configuration.dns.useHosts = it; bump() } }

        item { SelectablePreference(stringResource(R.string.enhanced_mode), enhancedModeOptions, configuration.dns.enhancedMode, dnsEnabled) { configuration.dns.enhancedMode = it; bump() } }

        item { DnsListPreference(stringResource(R.string.name_server), configuration.dns.nameServer, dnsEnabled) { configuration.dns.nameServer = it; bump() } }
        item { DnsListPreference(stringResource(R.string.fallback), configuration.dns.fallback, dnsEnabled) { configuration.dns.fallback = it; bump() } }
        item { DnsListPreference(stringResource(R.string.default_name_server), configuration.dns.defaultServer, dnsEnabled) { configuration.dns.defaultServer = it; bump() } }
        item { DnsListPreference(stringResource(R.string.fakeip_filter), configuration.dns.fakeIpFilter, dnsEnabled) { configuration.dns.fakeIpFilter = it; bump() } }

        item { SelectablePreference(stringResource(R.string.fakeip_filter_mode), filterModeOptions, configuration.dns.fakeIPFilterMode, dnsEnabled) { configuration.dns.fakeIPFilterMode = it; bump() } }

        item { SelectablePreference(stringResource(R.string.geoip_fallback), booleanOptions, configuration.dns.fallbackFilter.geoIp, dnsEnabled) { configuration.dns.fallbackFilter.geoIp = it; bump() } }
        item { StringPreference(stringResource(R.string.geoip_fallback_code), configuration.dns.fallbackFilter.geoIpCode, stringResource(R.string.raw_cn), dnsEnabled) { configuration.dns.fallbackFilter.geoIpCode = it; bump() } }
        item { DnsListPreference(stringResource(R.string.domain_fallback), configuration.dns.fallbackFilter.domain, dnsEnabled) { configuration.dns.fallbackFilter.domain = it; bump() } }
        item { DnsListPreference(stringResource(R.string.ipcidr_fallback), configuration.dns.fallbackFilter.ipcidr, dnsEnabled) { configuration.dns.fallbackFilter.ipcidr = it; bump() } }

        item {
            EditableMapPreference(
                title = stringResource(R.string.name_server_policy),
                value = configuration.dns.nameserverPolicy,
                keyAdapter = TextAdapter.String,
                valueAdapter = TextAdapter.String,
                placeholder = stringResource(R.string.dont_modify),
                enabled = dnsEnabled,
                onValueChange = { configuration.dns.nameserverPolicy = it; bump() },
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
    PreferenceRow(
        title = title,
        summary = options.firstOrNull { it.first == selected }?.second,
        enabled = enabled,
        onClick = { show = true },
    )
    if (show) {
        SingleChoiceDialog(
            title = title,
            options = options,
            selected = selected,
            onSelect = onSelect,
            onDismiss = { show = false },
        )
    }
}
