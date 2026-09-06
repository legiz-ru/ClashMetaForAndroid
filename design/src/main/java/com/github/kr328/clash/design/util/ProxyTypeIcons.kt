package com.github.kr328.clash.design.util

import android.content.Context
import com.github.kr328.clash.design.R

/**
 * Maps a proxy adapter type ([com.github.kr328.clash.core.model.Proxy.type], the core's
 * `AdapterType.String()`, e.g. "Vless", "Selector", "Direct") to the icon and tooltip shown
 * next to a node in the proxy list.
 *
 * The row used to print the type as text (`proxy.subtitle.ifEmpty { proxy.type }`), but that
 * slot is shared with the `ui-subtitle-pattern`-extracted description: when the pattern
 * matches something in the node's name, the extracted text replaces subtitle entirely and the
 * type disappears from the row. The icon now always carries the type; the text slot is free
 * to show only the pattern-extracted description, when there is one.
 *
 * Three families share the slot, because a group's members can themselves be groups:
 *  - group types    Selector, URLTest, Fallback, LoadBalance, Relay, Smart
 *  - service types  Direct, Reject, RejectDrop, Pass, PassRule, Rematch, Dns, Compatible
 *  - protocols      Vless, Trojan, Hysteria2, ...
 *
 * Icons are MDI glyphs except for protocols with a usable brand mark (`ic_proto_*`).
 */
object ProxyTypeIcons {
    private val GROUP_ICONS: Map<String, Int> = mapOf(
        "selector" to R.drawable.ic_mdi_gesture_tap,
        "urltest" to R.drawable.ic_mdi_clock_fast,
        "fallback" to R.drawable.ic_mdi_backup_restore,
        "loadbalance" to R.drawable.ic_mdi_scale_balance,
        "relay" to R.drawable.ic_mdi_transit_connection_variant,
        "smart" to R.drawable.ic_mdi_brain,
    )

    private val SERVICE_ICONS: Map<String, Int> = mapOf(
        "direct" to R.drawable.ic_mdi_arrow_right_bold_outline,
        "reject" to R.drawable.ic_mdi_cancel,
        "rejectdrop" to R.drawable.ic_mdi_close_octagon_outline,
        "pass" to R.drawable.ic_mdi_forward,
        "passrule" to R.drawable.ic_mdi_debug_step_over,
        "rematch" to R.drawable.ic_mdi_sync,
        "dns" to R.drawable.ic_mdi_dns_outline,
        "compatible" to R.drawable.ic_mdi_cog,
    )

    // Only protocols with a non-arbitrary mapping are named; the rest (including any type
    // the core gains later, e.g. Snell/Mieru/ShadowQuic/ZeroTier today) fall through to the
    // protocol default below rather than getting an invented glyph.
    private val PROTOCOL_ICONS: Map<String, Int> = mapOf(
        "vless" to R.drawable.ic_proto_xray,
        "trojan" to R.drawable.ic_proto_trojan,
        "trusttunnel" to R.drawable.ic_proto_trusttunnel,
        "vmess" to R.drawable.ic_mdi_alpha_v_box_outline,
        "shadowsocks" to R.drawable.ic_mdi_airplane,
        "shadowsocksr" to R.drawable.ic_mdi_airplane,
        "hysteria" to R.drawable.ic_mdi_lightning_bolt_outline,
        "hysteria2" to R.drawable.ic_mdi_lightning_bolt_outline,
        "tuic" to R.drawable.ic_mdi_rocket_launch_outline,
        "wireguard" to R.drawable.ic_proto_wireguard,
        "tailscale" to R.drawable.ic_proto_tailscale,
        "openvpn" to R.drawable.ic_proto_openvpn,
        "http" to R.drawable.ic_mdi_web,
        "socks5" to R.drawable.ic_mdi_network_outline,
        "ssh" to R.drawable.ic_mdi_console,
        // A domino mask and a semantic-web glyph aren't literal depictions of either
        // protocol — chosen deliberately over a brand-mark search, since neither Masque
        // nor AnyTLS has one that reduces to a legible monochrome badge.
        "masque" to R.drawable.ic_mdi_domino_mask,
        "anytls" to R.drawable.ic_mdi_semantic_web,
        "sudoku" to R.drawable.ic_proto_sudoku,
        "gostrelay" to R.drawable.ic_proto_gost_relay,
    )

    enum class Kind { GROUP, SERVICE, PROTOCOL, UNKNOWN }

    /**
     * Classifies a node by the shape of its data rather than a hardcoded list of type names:
     * anything the core reports as a group (has an `all`/members list, surfaced here via
     * [isGroup]) lands in the group branch even if this build has never heard of its type.
     */
    fun kind(type: String?, isGroup: Boolean): Kind {
        val key = type.orEmpty().lowercase()
        return when {
            key.isEmpty() || key == "unknown" -> Kind.UNKNOWN
            isGroup || key in GROUP_ICONS -> Kind.GROUP
            key in SERVICE_ICONS -> Kind.SERVICE
            else -> Kind.PROTOCOL
        }
    }

    /** The icon for a type, falling back per family so a type this build doesn't know stays sensible. */
    fun icon(type: String?, isGroup: Boolean): Int {
        val key = type.orEmpty().lowercase()
        return when (kind(type, isGroup)) {
            Kind.GROUP -> GROUP_ICONS[key] ?: R.drawable.ic_mdi_source_branch
            Kind.SERVICE -> SERVICE_ICONS[key] ?: R.drawable.ic_mdi_cog
            Kind.PROTOCOL -> PROTOCOL_ICONS[key] ?: R.drawable.ic_mdi_shield_lock_outline
            Kind.UNKNOWN -> R.drawable.ic_mdi_help_circle_outline
        }
    }

    /**
     * Tooltip text for a type. Group and service types get a written explanation of what
     * they do; protocols get the name, since there is nothing to explain.
     */
    fun tooltip(context: Context, type: String?, isGroup: Boolean): String {
        val raw = type.orEmpty().trim()
        val key = raw.lowercase()

        return when (kind(raw, isGroup)) {
            Kind.UNKNOWN -> context.getString(R.string.proxies_type_unknown)
            Kind.GROUP -> when (key) {
                "selector" -> context.getString(R.string.proxies_type_selector)
                "urltest" -> context.getString(R.string.proxies_type_urltest)
                "fallback" -> context.getString(R.string.proxies_type_fallback)
                "loadbalance" -> context.getString(R.string.proxies_type_loadbalance)
                "relay" -> context.getString(R.string.proxies_type_relay)
                "smart" -> context.getString(R.string.proxies_type_smart)
                else -> raw
            }
            Kind.SERVICE -> when (key) {
                "direct" -> context.getString(R.string.proxies_type_direct)
                "reject" -> context.getString(R.string.proxies_type_reject)
                "rejectdrop" -> context.getString(R.string.proxies_type_rejectdrop)
                "pass" -> context.getString(R.string.proxies_type_pass)
                "passrule" -> context.getString(R.string.proxies_type_passrule)
                "rematch" -> context.getString(R.string.proxies_type_rematch)
                "dns" -> context.getString(R.string.proxies_type_dns)
                "compatible" -> context.getString(R.string.proxies_type_compatible)
                else -> raw
            }
            Kind.PROTOCOL -> raw
        }
    }
}
