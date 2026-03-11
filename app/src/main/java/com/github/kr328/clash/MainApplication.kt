package com.github.kr328.clash

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.kr328.clash.common.Global
import com.github.kr328.clash.common.compat.currentProcessName
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.util.sendServiceRecreated
import com.github.kr328.clash.util.clashDir
import java.io.File
import java.io.FileOutputStream
import com.github.kr328.clash.update.UpdateChecker
import com.github.kr328.clash.design.R as DesignR


@Suppress("unused")
class MainApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        Global.init(this)
    }

    override fun onCreate() {
        super.onCreate()

        val processName = currentProcessName
        extractGeoFiles()

        Log.d("Process $processName started")

        if (processName == packageName) {
            Remote.launch()
            setupShortcuts()
            UpdateChecker.createNotificationChannel(this)
        } else {
            sendServiceRecreated()
        }
    }

    private fun setupShortcuts() {
        val icon = IconCompat.createWithResource(this, R.mipmap.ic_launcher)
        val qrIcon = IconCompat.createWithResource(this, DesignR.drawable.ic_shortcut_scan_qr)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                Intent.FLAG_ACTIVITY_NO_ANIMATION

        val scanQr = ShortcutInfoCompat.Builder(this, "scan_qr")
            .setShortLabel(getString(DesignR.string.shortcut_scan_qr_short))
            .setLongLabel(getString(DesignR.string.shortcut_scan_qr_long))
            .setIcon(qrIcon)
            .setIntent(
                Intent(Intent.ACTION_MAIN)
                    .setClassName(this, ProfilesActivity::class.java.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ProfilesActivity.EXTRA_SCAN_QR_ON_START, true)
            )
            .setRank(0)
            .build()

        val toggle = ShortcutInfoCompat.Builder(this, "toggle_clash")
            .setShortLabel(getString(DesignR.string.shortcut_toggle_short))
            .setLongLabel(getString(DesignR.string.shortcut_toggle_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_TOGGLE_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(1)
            .build()

        val start = ShortcutInfoCompat.Builder(this, "start_clash")
            .setShortLabel(getString(DesignR.string.shortcut_start_short))
            .setLongLabel(getString(DesignR.string.shortcut_start_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_START_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(2)
            .build()

        val stop = ShortcutInfoCompat.Builder(this, "stop_clash")
            .setShortLabel(getString(DesignR.string.shortcut_stop_short))
            .setLongLabel(getString(DesignR.string.shortcut_stop_long))
            .setIcon(icon)
            .setIntent(
                Intent(Intents.ACTION_STOP_CLASH)
                    .setClassName(this, ExternalControlActivity::class.java.name)
                    .addFlags(flags)
            )
            .setRank(3)
            .build()

        ShortcutManagerCompat.setDynamicShortcuts(this, listOf(scanQr, toggle, start, stop))
    }

    private fun extractGeoFiles() {
        clashDir.mkdirs()

        val updateDate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val geoipFile = File(clashDir, "geoip.metadb")
        if (geoipFile.exists() && geoipFile.lastModified() < updateDate) {
            geoipFile.delete()
        }
        if (!geoipFile.exists()) {
            FileOutputStream(geoipFile).use {
                assets.open("geoip.metadb").copyTo(it)
            }
        }

        val geositeFile = File(clashDir, "geosite.dat")
        if (geositeFile.exists() && geositeFile.lastModified() < updateDate) {
            geositeFile.delete()
        }
        if (!geositeFile.exists()) {
            FileOutputStream(geositeFile).use {
                assets.open("geosite.dat").copyTo(it)
            }
        }

        val asnFile = File(clashDir, "ASN.mmdb")
        if (asnFile.exists() && asnFile.lastModified() < updateDate) {
            asnFile.delete()
        }
        if (!asnFile.exists()) {
            FileOutputStream(asnFile).use {
                assets.open("ASN.mmdb").copyTo(it)
            }
        }
    }

    fun finalize() {
        Global.destroy()
    }
}
