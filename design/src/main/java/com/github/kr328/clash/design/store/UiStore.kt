package com.github.kr328.clash.design.store

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.design.model.AppInfoSort
import com.github.kr328.clash.design.model.DarkMode

class UiStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var enableVpn: Boolean by store.boolean(
        key = "enable_vpn",
        defaultValue = true
    )

    var darkMode: DarkMode by store.enum(
        key = "dark_mode",
        defaultValue = DarkMode.Auto,
        values = DarkMode.values()
    )

    var hideAppIcon: Boolean by store.boolean(
        key = "hide_app_icon",
        defaultValue = with(Companion) {
            context.packageManager.getComponentEnabledSetting(
                context.mainActivityAlias
            ) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    )

    var hideFromRecents: Boolean by store.boolean(
        key = "hide_from_recents",
        defaultValue = false,
    )

    var proxyExcludeNotSelectable by store.boolean(
        key = "proxy_exclude_not_selectable",
        defaultValue = false,
    )

    var proxyLine: Int by store.int(
        key = "proxy_line",
        defaultValue = 2
    )

    var proxySort: ProxySort by store.enum(
        key = "proxy_sort",
        defaultValue = ProxySort.Default,
        values = ProxySort.values()
    )

    /**
     * Per-profile proxy sort. Remembered separately for each profile (keyed by its UUID)
     * so switching/restarting profiles keeps each profile's own sort. Falls back to the
     * legacy global [proxySort] when the profile has no stored value (smooth migration),
     * and to the global value entirely when [profileUuid] is empty (no active profile).
     */
    fun getProxySort(profileUuid: String): ProxySort {
        if (profileUuid.isEmpty()) return proxySort
        val name = store.provider.getString(proxySortKey(profileUuid), proxySort.name)
        return ProxySort.values().find { it.name == name } ?: ProxySort.Default
    }

    fun setProxySort(profileUuid: String, sort: ProxySort) {
        if (profileUuid.isEmpty()) {
            proxySort = sort
            return
        }
        store.provider.setString(proxySortKey(profileUuid), sort.name)
    }

    private fun proxySortKey(profileUuid: String) = "proxy_sort_$profileUuid"

    var proxyLastGroup: String by store.string(
        key = "proxy_last_group",
        defaultValue = ""
    )

    var accessControlSort: AppInfoSort by store.enum(
        key = "access_control_sort",
        defaultValue = AppInfoSort.Label,
        values = AppInfoSort.values(),
    )

    var accessControlReverse: Boolean by store.boolean(
        key = "access_control_reverse",
        defaultValue = false
    )

    var accessControlSystemApp: Boolean by store.boolean(
        key = "access_control_system_app",
        defaultValue = false,
    )

    var sendHwid: Boolean by store.boolean(
        key = "send_hwid",
        defaultValue = true,
    )

    var delayDisplayDots: Boolean by store.boolean(
        key = "delay_display_dots",
        defaultValue = true,
    )

    var summerModeUnlocked: Boolean by store.boolean(
        key = "summer_mode_unlocked",
        defaultValue = false,
    )

    companion object {
        private const val PREFERENCE_NAME = "ui"

        val Context.mainActivityAlias: ComponentName
            get() = ComponentName(this, "${packageName}.MainActivityAlias")
    }
}
