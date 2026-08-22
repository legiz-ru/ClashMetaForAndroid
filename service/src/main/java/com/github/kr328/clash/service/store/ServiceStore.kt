package com.github.kr328.clash.service.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.service.PreferenceProvider
import com.github.kr328.clash.service.model.AccessControlMode
import java.util.*

class ServiceStore(context: Context) {
    private val store = Store(
        PreferenceProvider
            .createSharedPreferencesFromContext(context)
            .asStoreProvider()
    )

    var activeProfile: UUID? by store.typedString(
        key = "active_profile",
        from = { if (it.isBlank()) null else UUID.fromString(it) },
        to = { it?.toString() ?: "" }
    )

    var bypassPrivateNetwork: Boolean by store.boolean(
        key = "bypass_private_network",
        defaultValue = true
    )

    var accessControlMode: AccessControlMode by store.enum(
        key = "access_control_mode",
        defaultValue = AccessControlMode.AcceptAll,
        values = AccessControlMode.values()
    )

    var accessControlPackages by store.stringSet(
        key = "access_control_packages",
        defaultValue = emptySet()
    )

    var dnsHijacking by store.boolean(
        key = "dns_hijacking",
        defaultValue = true
    )

    var systemProxy by store.boolean(
        key = "system_proxy",
        defaultValue = true
    )

    var allowBypass by store.boolean(
        key = "allow_bypass",
        defaultValue = false
    )

    var allowIpv6 by store.boolean(
        key = "allow_ipv6",
        defaultValue = false
    )

    /**
     * Whether live connections should be dropped on a network change
     * (Wi-Fi <-> LTE and back).
     *
     * A connection opened over a vanished interface is dead either way: the only
     * difference is whether the app learns about it right away or after the OS
     * timeout — a minute and more. All that time the user sees "the internet is
     * there but nothing loads".
     *
     * On by default. Turning it off makes sense for someone downloading large
     * files in an app without resume support: there a break is more noticeable
     * than the wait. The interface cache and the DNS connections are reset in
     * any case — that tears nothing down for the user, and without it names stop
     * resolving.
     */
    var resetConnectionsOnNetworkChange by store.boolean(
        key = "reset_connections_on_network_change",
        defaultValue = true
    )

    var tunStackMode by store.string(
        key = "tun_stack_mode",
        defaultValue = "system"
    )

    var dynamicNotification by store.boolean(
        key = "dynamic_notification",
        defaultValue = true
    )

    /**
     * The transient "Updating <profile>" notification shown while a
     * subscription update is in flight (service/clash/module unrelated —
     * posted directly by ProfileWorker, not tied to any foreground-service
     * contract, so unlike the tunnel status notification this one really can
     * be turned off).
     */
    var notifySubscriptionProgress by store.boolean(
        key = "notify_subscription_progress",
        defaultValue = true
    )

    /**
     * The "Update failed" notification, posted when a scheduled or manual
     * subscription update errors out. On by default: it is the only channel
     * through which a silently-broken subscription (expired key, dead URL,
     * network change) becomes visible to the user at all.
     */
    var notifySubscriptionErrors by store.boolean(
        key = "notify_subscription_errors",
        defaultValue = true
    )

    /**
     * Expiry and traffic reminders — "expires in N days", "expired",
     * "N% of traffic used" — thresholds set by the panel via the
     * `notify-expire-days`/`notify-traffic-percent` headers (see
     * SubscriptionAlerts). One switch, not three: all three are the same
     * underlying thing — the subscription's state needs attention — and the
     * panel already controls which of them fire at all by what thresholds it
     * sends (or "off" for none). Splitting the local switch further on top of
     * that would be control over a distinction nobody asked for.
     */
    var notifySubscriptionAlerts by store.boolean(
        key = "notify_subscription_alerts",
        defaultValue = true
    )
}