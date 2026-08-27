package com.github.kr328.clash.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.ExternalControlActivity
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.design.R as DesignR

private val SHORTCUT_IDS = listOf("toggle_clash", "start_clash", "stop_clash")

/**
 * Applies (or tears down) the launcher's dynamic app shortcuts.
 *
 * [hide] mirrors "hide app icon": removing the dynamic shortcuts alone left
 * copies the user had dragged onto their home screen (pinned shortcuts)
 * working — those aren't dynamic, so `removeAllDynamicShortcuts` never
 * touched them. `disableShortcuts`/`enableShortcuts` cover pinned shortcuts
 * too, and are what actually needs undoing when the icon comes back.
 */
fun Context.applyDynamicShortcuts(hide: Boolean) {
    if (hide) {
        ShortcutManagerCompat.removeAllDynamicShortcuts(this)

        runCatching {
            ShortcutManagerCompat.disableShortcuts(
                this,
                SHORTCUT_IDS,
                getString(DesignR.string.shortcut_disabled),
            )
        }

        return
    }

    val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
        Intent.FLAG_ACTIVITY_NO_ANIMATION

    val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
        .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
        .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
        .setIcon(IconCompat.createWithResource(this, DesignR.drawable.ic_toggle_all))
        .setIntent(
            Intent(Intents.ACTION_TOGGLE_CLASH)
                .setClassName(this, ExternalControlActivity::class.java.name)
                .addFlags(flags)
        )
        .setRank(0)
        .build()

    val start = ShortcutInfoCompat.Builder(this, "start_clash")
        .setShortLabel(getString(DesignR.string.shortcut_start_short))
        .setLongLabel(getString(DesignR.string.shortcut_start_long))
        .setIcon(IconCompat.createWithResource(this, DesignR.drawable.ic_toggle_on))
        .setIntent(
            Intent(Intents.ACTION_START_CLASH)
                .setClassName(this, ExternalControlActivity::class.java.name)
                .addFlags(flags)
        )
        .setRank(1)
        .build()

    val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
        .setShortLabel(getString(DesignR.string.shortcut_stop_short))
        .setLongLabel(getString(DesignR.string.shortcut_stop_long))
        .setIcon(IconCompat.createWithResource(this, DesignR.drawable.ic_toggle_off))
        .setIntent(
            Intent(Intents.ACTION_STOP_CLASH)
                .setClassName(this, ExternalControlActivity::class.java.name)
                .addFlags(flags)
        )
        .setRank(2)
        .build()

    val shortcuts = listOf(toggle, start, stop)

    ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts)

    runCatching { ShortcutManagerCompat.enableShortcuts(this, shortcuts) }
    runCatching { ShortcutManagerCompat.updateShortcuts(this, shortcuts) }
}
