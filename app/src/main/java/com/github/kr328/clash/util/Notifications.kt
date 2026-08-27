package com.github.kr328.clash.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.kr328.clash.design.compose.screen.NotificationPermissionStatus
import com.github.kr328.clash.design.store.UiStore

/**
 * Where the app currently stands on notifications — combines the Tiramisu+
 * runtime permission with the separate, version-independent "notifications
 * enabled for this app" switch, since either one blocked means the same thing
 * to the user: nothing gets shown.
 */
fun Context.notificationPermissionStatus(): NotificationPermissionStatus {
    val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    if (!runtimePermissionGranted) {
        return if (UiStore(this).notificationsAsked) {
            NotificationPermissionStatus.Blocked
        } else {
            NotificationPermissionStatus.NotAsked
        }
    }

    return if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
        NotificationPermissionStatus.Granted
    } else {
        NotificationPermissionStatus.Blocked
    }
}

/**
 * Opens system notification settings — for one specific channel when
 * [channelId] is given (falls back to the app-wide screen if that intent
 * doesn't resolve, which happens if the channel hasn't been created yet), or
 * for the whole app otherwise.
 */
fun Context.openSystemNotificationSettings(channelId: String? = null) {
    val intents = buildList {
        if (channelId != null) {
            add(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            )
        }
        add(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
        )
    }

    for (intent in intents) {
        try {
            startActivity(intent)

            return
        } catch (_: Exception) {
        }
    }
}
