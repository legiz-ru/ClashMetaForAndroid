package com.github.kr328.clash.design.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.getSystemService
import com.github.kr328.clash.design.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// ─── Shared pop-up menus ──────────────────────────────────────────────────────

fun showIpMenu(context: Context, ip: String) {
    MaterialAlertDialogBuilder(context)
        .setItems(arrayOf(
            context.getString(R.string.copy_address),
            context.getString(R.string.open_ip_info),
        )) { _, which ->
            when (which) {
                0 -> copyToClipboard(context, ip)
                1 -> context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://ipinfo.io/$ip"))
                )
            }
        }
        .show()
}

fun showHostMenu(context: Context, host: String) {
    MaterialAlertDialogBuilder(context)
        .setItems(arrayOf(
            context.getString(R.string.copy_host),
            context.getString(R.string.open_in_browser),
        )) { _, which ->
            when (which) {
                0 -> copyToClipboard(context, host)
                1 -> context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://$host"))
                )
            }
        }
        .show()
}

fun showProcessMenu(context: Context, packageName: String) {
    MaterialAlertDialogBuilder(context)
        .setItems(arrayOf(
            context.getString(R.string.copy_process_name),
            context.getString(R.string.open_app),
            context.getString(R.string.open_in_store),
        )) { _, which ->
            when (which) {
                0 -> copyToClipboard(context, packageName)
                1 -> openApp(context, packageName)
                2 -> openInStore(context, packageName)
            }
        }
        .show()
}

private fun copyToClipboard(context: Context, text: String) {
    context.getSystemService<ClipboardManager>()
        ?.setPrimaryClip(ClipData.newPlainText("value", text))
}

private fun openApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) context.startActivity(intent)
}

private fun openInStore(context: Context, packageName: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
    }
}
