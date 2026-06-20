package com.github.kr328.clash.design.compose.screen

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.EditableListPreference
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.TextAdapter

/**
 * Meta feature settings screen (1:1 with MetaFeatureSettingsDesign, restyled to MD3E).
 * Edits the [configuration] object in place; the Activity persists it on exit.
 */
@Composable
fun MetaFeatureSettingsScreen(
    configuration: ConfigurationOverride,
    onBack: () -> Unit,
    onReset: () -> Unit,
    onImportGeoIp: () -> Unit,
    onImportGeoSite: () -> Unit,
    onImportCountry: () -> Unit,
    onImportASN: () -> Unit,
    onGenerateAgeKeyPair: () -> Unit,
) {
    var rev by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r = rev
    val bump = { rev++ }

    val booleanOptions: List<Pair<Boolean?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        true to stringResource(R.string.enabled),
        false to stringResource(R.string.disabled),
    )
    val findProcessOptions: List<Pair<ConfigurationOverride.FindProcessMode?, String>> = listOf(
        null to stringResource(R.string.dont_modify),
        ConfigurationOverride.FindProcessMode.Off to stringResource(R.string.off),
        ConfigurationOverride.FindProcessMode.Strict to stringResource(R.string.strict),
        ConfigurationOverride.FindProcessMode.Always to stringResource(R.string.always),
    )
    val snifferEnabled = configuration.sniffer.enable != false

    PreferenceScaffold(
        title = stringResource(R.string.meta_features),
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
        item { SettingsCategory(stringResource(R.string.settings)) }

        item { SelectablePreference(stringResource(R.string.unified_delay), booleanOptions, configuration.unifiedDelay) { configuration.unifiedDelay = it; bump() } }
        item { SelectablePreference(stringResource(R.string.geodata_mode), booleanOptions, configuration.geodataMode) { configuration.geodataMode = it; bump() } }
        item { SelectablePreference(stringResource(R.string.tcp_concurrent), booleanOptions, configuration.tcpConcurrent) { configuration.tcpConcurrent = it; bump() } }

        item { SelectablePreference(stringResource(R.string.find_process_mode), findProcessOptions, configuration.findProcessMode) { configuration.findProcessMode = it; bump() } }

        item { SettingsCategory(stringResource(R.string.sniffer_setting)) }

        item { SelectablePreference(stringResource(R.string.strategy), booleanOptions, configuration.sniffer.enable) { configuration.sniffer.enable = it; bump() } }

        item { SnifferList(stringResource(R.string.sniff_http_ports), configuration.sniffer.sniff.http.ports, snifferEnabled) { configuration.sniffer.sniff.http.ports = it; bump() } }
        item { SelectablePreference(stringResource(R.string.sniff_http_override_destination), booleanOptions, configuration.sniffer.sniff.http.overrideDestination, snifferEnabled) { configuration.sniffer.sniff.http.overrideDestination = it; bump() } }
        item { SnifferList(stringResource(R.string.sniff_tls_ports), configuration.sniffer.sniff.tls.ports, snifferEnabled) { configuration.sniffer.sniff.tls.ports = it; bump() } }
        item { SelectablePreference(stringResource(R.string.sniff_tls_override_destination), booleanOptions, configuration.sniffer.sniff.tls.overrideDestination, snifferEnabled) { configuration.sniffer.sniff.tls.overrideDestination = it; bump() } }
        item { SnifferList(stringResource(R.string.sniff_quic_ports), configuration.sniffer.sniff.quic.ports, snifferEnabled) { configuration.sniffer.sniff.quic.ports = it; bump() } }
        item { SelectablePreference(stringResource(R.string.sniff_quic_override_destination), booleanOptions, configuration.sniffer.sniff.quic.overrideDestination, snifferEnabled) { configuration.sniffer.sniff.quic.overrideDestination = it; bump() } }

        item { SelectablePreference(stringResource(R.string.force_dns_mapping), booleanOptions, configuration.sniffer.forceDnsMapping, snifferEnabled) { configuration.sniffer.forceDnsMapping = it; bump() } }
        item { SelectablePreference(stringResource(R.string.parse_pure_ip), booleanOptions, configuration.sniffer.parsePureIp, snifferEnabled) { configuration.sniffer.parsePureIp = it; bump() } }
        item { SelectablePreference(stringResource(R.string.override_destination), booleanOptions, configuration.sniffer.overrideDestination, snifferEnabled) { configuration.sniffer.overrideDestination = it; bump() } }

        item { SnifferList(stringResource(R.string.force_domain), configuration.sniffer.forceDomain, snifferEnabled) { configuration.sniffer.forceDomain = it; bump() } }
        item { SnifferList(stringResource(R.string.skip_domain), configuration.sniffer.skipDomain, snifferEnabled) { configuration.sniffer.skipDomain = it; bump() } }
        item { SnifferList(stringResource(R.string.skip_src_address), configuration.sniffer.skipSrcAddress, snifferEnabled) { configuration.sniffer.skipSrcAddress = it; bump() } }
        item { SnifferList(stringResource(R.string.skip_dst_address), configuration.sniffer.skipDstAddress, snifferEnabled) { configuration.sniffer.skipDstAddress = it; bump() } }

        item { SettingsCategory(stringResource(R.string.geox_files)) }
        item { PreferenceRow(stringResource(R.string.import_geoip_file), summary = stringResource(R.string.press_to_import), onClick = onImportGeoIp) }
        item { PreferenceRow(stringResource(R.string.import_geosite_file), summary = stringResource(R.string.press_to_import), onClick = onImportGeoSite) }
        item { PreferenceRow(stringResource(R.string.import_country_file), summary = stringResource(R.string.press_to_import), onClick = onImportCountry) }
        item { PreferenceRow(stringResource(R.string.import_asn_file), summary = stringResource(R.string.press_to_import), onClick = onImportASN) }

        item { SettingsCategory(stringResource(R.string.age_secret_key)) }
        item { PreferenceRow(stringResource(R.string.age_keypair_generate), summary = stringResource(R.string.age_keypair_generate_desc), onClick = onGenerateAgeKeyPair) }
    }
}

@Composable
private fun SnifferList(
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
