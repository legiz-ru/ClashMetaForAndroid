package com.github.kr328.clash.design.compose.screen

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceRow
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SingleChoiceDialog
import com.github.kr328.clash.design.compose.component.SwitchPreference
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.service.store.ServiceStore

/**
 * Network settings screen (1:1 with NetworkSettingsDesign, restyled to MD3E).
 * Reads/writes the persistent stores directly; a revision counter drives recomposition.
 */
@Composable
fun NetworkSettingsScreen(
    uiStore: UiStore,
    srvStore: ServiceStore,
    running: Boolean,
    onBack: () -> Unit,
    onAccessControlPackages: () -> Unit,
) {
    var showTunStack by remember { mutableStateOf(false) }

    // Snapshot state mirrors of the persisted stores so rows reflect changes immediately
    // (read inside each item) without a rev/bump recompose trigger.
    var enableVpn by remember { mutableStateOf(uiStore.enableVpn) }
    var tunStackMode by remember { mutableStateOf(srvStore.tunStackMode) }
    var resetConnections by remember { mutableStateOf(srvStore.resetConnectionsOnNetworkChange) }

    val vpnEnabled = !running

    val tunStackOptions = listOf(
        "system" to stringResource(R.string.tun_stack_system),
        "gvisor" to stringResource(R.string.tun_stack_gvisor),
        "mixed" to stringResource(R.string.tun_stack_mixed),
    )

    PreferenceScaffold(title = stringResource(R.string.network), onBack = onBack) {
        item {
            SwitchPreference(
                title = stringResource(R.string.route_system_traffic),
                summary = stringResource(R.string.routing_via_vpn_service),
                icon = R.drawable.ic_baseline_vpn_lock,
                checked = enableVpn,
                enabled = vpnEnabled,
                onCheckedChange = { uiStore.enableVpn = it; enableVpn = it },
            )
        }

        item { SettingsCategory(stringResource(R.string.vpn_service_options)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.bypass_private_network),
                summary = stringResource(R.string.bypass_private_network_summary),
                checked = srvStore.bypassPrivateNetwork,
                enabled = enableVpn && !running,
                onCheckedChange = { srvStore.bypassPrivateNetwork = it },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.dns_hijacking),
                summary = stringResource(R.string.dns_hijacking_summary),
                checked = srvStore.dnsHijacking,
                enabled = enableVpn && !running,
                onCheckedChange = { srvStore.dnsHijacking = it },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.allow_bypass),
                summary = stringResource(R.string.allow_bypass_summary),
                checked = srvStore.allowBypass,
                enabled = enableVpn && !running,
                onCheckedChange = { srvStore.allowBypass = it },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.allow_ipv6),
                summary = stringResource(R.string.allow_ipv6_summary),
                checked = srvStore.allowIpv6,
                enabled = enableVpn && !running,
                onCheckedChange = { srvStore.allowIpv6 = it },
            )
        }
        if (Build.VERSION.SDK_INT >= 29) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.system_proxy),
                    summary = stringResource(R.string.system_proxy_summary),
                    checked = srvStore.systemProxy,
                    enabled = enableVpn && !running,
                    onCheckedChange = { srvStore.systemProxy = it },
                )
            }
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.tun_stack_mode),
                summary = tunStackOptions.firstOrNull { it.first == tunStackMode }?.second,
                enabled = enableVpn && !running,
                onClick = { showTunStack = true },
            )
        }
        // One entry, not two: the mode and the app list are the same setting,
        // and the mode moved onto the list screen where it applies. Not gated on
        // `running` — that screen restarts the tunnel itself when something
        // changed.
        item {
            PreferenceRow(
                title = stringResource(R.string.access_control_apps),
                summary = stringResource(R.string.access_control_apps_summary),
                onClick = onAccessControlPackages,
            )
        }

        // A section of its own and NOT gated on `running`: everything else on
        // this screen describes how to bring the tunnel up and only changes
        // while it is down. This one is read on the fly, at the moment of the
        // network change — so it can be flipped on the go too.
        item { SettingsCategory(stringResource(R.string.network_switch)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.reset_connections),
                summary = stringResource(R.string.reset_connections_summary),
                checked = resetConnections,
                onCheckedChange = { srvStore.resetConnectionsOnNetworkChange = it; resetConnections = it },
            )
        }
    }

    if (showTunStack) {
        SingleChoiceDialog(
            title = stringResource(R.string.tun_stack_mode),
            options = tunStackOptions,
            selected = tunStackMode,
            onSelect = { srvStore.tunStackMode = it; tunStackMode = it },
            onDismiss = { showTunStack = false },
        )
    }
}
