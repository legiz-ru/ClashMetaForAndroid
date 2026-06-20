package com.github.kr328.clash.design.compose.screen

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.github.kr328.clash.service.model.AccessControlMode
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
    var rev by remember { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE") val r = rev
    val bump = { rev++ }

    var showTunStack by remember { mutableStateOf(false) }
    var showAclMode by remember { mutableStateOf(false) }

    val vpnEnabled = !running
    val depsEnabled = uiStore.enableVpn && !running

    val tunStackOptions = listOf(
        "system" to stringResource(R.string.tun_stack_system),
        "gvisor" to stringResource(R.string.tun_stack_gvisor),
        "mixed" to stringResource(R.string.tun_stack_mixed),
    )
    val aclOptions = listOf(
        AccessControlMode.AcceptAll to stringResource(R.string.allow_all_apps),
        AccessControlMode.AcceptSelected to stringResource(R.string.allow_selected_apps),
        AccessControlMode.DenySelected to stringResource(R.string.deny_selected_apps),
    )

    PreferenceScaffold(title = stringResource(R.string.network), onBack = onBack) {
        item {
            SwitchPreference(
                title = stringResource(R.string.route_system_traffic),
                summary = stringResource(R.string.routing_via_vpn_service),
                icon = R.drawable.ic_baseline_vpn_lock,
                checked = uiStore.enableVpn,
                enabled = vpnEnabled,
                onCheckedChange = { uiStore.enableVpn = it; bump() },
            )
        }

        item { SettingsCategory(stringResource(R.string.vpn_service_options)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.bypass_private_network),
                summary = stringResource(R.string.bypass_private_network_summary),
                checked = srvStore.bypassPrivateNetwork,
                enabled = depsEnabled,
                onCheckedChange = { srvStore.bypassPrivateNetwork = it; bump() },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.dns_hijacking),
                summary = stringResource(R.string.dns_hijacking_summary),
                checked = srvStore.dnsHijacking,
                enabled = depsEnabled,
                onCheckedChange = { srvStore.dnsHijacking = it; bump() },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.allow_bypass),
                summary = stringResource(R.string.allow_bypass_summary),
                checked = srvStore.allowBypass,
                enabled = depsEnabled,
                onCheckedChange = { srvStore.allowBypass = it; bump() },
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.allow_ipv6),
                summary = stringResource(R.string.allow_ipv6_summary),
                checked = srvStore.allowIpv6,
                enabled = depsEnabled,
                onCheckedChange = { srvStore.allowIpv6 = it; bump() },
            )
        }
        if (Build.VERSION.SDK_INT >= 29) {
            item {
                SwitchPreference(
                    title = stringResource(R.string.system_proxy),
                    summary = stringResource(R.string.system_proxy_summary),
                    checked = srvStore.systemProxy,
                    enabled = depsEnabled,
                    onCheckedChange = { srvStore.systemProxy = it; bump() },
                )
            }
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.tun_stack_mode),
                summary = tunStackOptions.firstOrNull { it.first == srvStore.tunStackMode }?.second,
                enabled = depsEnabled,
                onClick = { showTunStack = true },
            )
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.access_control_mode),
                summary = aclOptions.firstOrNull { it.first == srvStore.accessControlMode }?.second,
                enabled = depsEnabled,
                onClick = { showAclMode = true },
            )
        }
        item {
            PreferenceRow(
                title = stringResource(R.string.access_control_packages),
                summary = stringResource(R.string.access_control_packages_summary),
                onClick = onAccessControlPackages,
            )
        }
    }

    if (showTunStack) {
        SingleChoiceDialog(
            title = stringResource(R.string.tun_stack_mode),
            options = tunStackOptions,
            selected = srvStore.tunStackMode,
            onSelect = { srvStore.tunStackMode = it; bump() },
            onDismiss = { showTunStack = false },
        )
    }
    if (showAclMode) {
        SingleChoiceDialog(
            title = stringResource(R.string.access_control_mode),
            options = aclOptions,
            selected = srvStore.accessControlMode,
            onSelect = { srvStore.accessControlMode = it; bump() },
            onDismiss = { showAclMode = false },
        )
    }
}
