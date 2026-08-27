package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.PreferenceScaffold
import com.github.kr328.clash.design.compose.component.SettingsCategory
import com.github.kr328.clash.design.compose.component.SwitchPreference

/** Whether the app currently has POST_NOTIFICATIONS and system-level notification access. */
sealed interface NotificationPermissionStatus {
    /** Nothing to show — either pre-Tiramisu (no runtime permission), or already granted. */
    data object Granted : NotificationPermissionStatus

    /** Tiramisu+, permission not granted, never asked (see UiStore.notificationsAsked). */
    data object NotAsked : NotificationPermissionStatus

    /**
     * Either the runtime permission was denied, or notifications are off for the
     * app at the OS level (a separate switch from the permission, available on
     * every Android version) — both mean the same thing to the user: nothing
     * below this card will actually show up.
     */
    data object Blocked : NotificationPermissionStatus
}

/**
 * All notification-related preferences on one screen: the permission itself,
 * the (mandatory) tunnel status notice, and every notification the app can
 * post, each with its own on/off switch and — where the notification has its
 * own channel — a shortcut into that channel's system sound/vibration
 * settings.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    permissionStatus: NotificationPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSystemNotificationSettings: () -> Unit,
    dynamicNotification: Boolean,
    dynamicNotificationEnabled: Boolean,
    onDynamicNotification: (Boolean) -> Unit,
    notifySubscriptionProgress: Boolean,
    onNotifySubscriptionProgress: (Boolean) -> Unit,
    onOpenProgressChannelSettings: () -> Unit,
    notifySubscriptionErrors: Boolean,
    onNotifySubscriptionErrors: (Boolean) -> Unit,
    onOpenErrorsChannelSettings: () -> Unit,
    notifySubscriptionAlerts: Boolean,
    onNotifySubscriptionAlerts: (Boolean) -> Unit,
    onOpenSubscriptionAlertsChannelSettings: () -> Unit,
) {
    // Individual toggles are disabled while permission is missing: leaving them
    // tappable would let the user "turn on" something that cannot possibly show
    // up yet, which reads as a bug rather than as the actual cause (no permission).
    val rowsEnabled = permissionStatus == NotificationPermissionStatus.Granted

    PreferenceScaffold(title = stringResource(R.string.notifications), onBack = onBack) {
        if (permissionStatus != NotificationPermissionStatus.Granted) {
            item {
                PermissionNotice(
                    status = permissionStatus,
                    onRequestPermission = onRequestPermission,
                    onOpenSettings = onOpenSystemNotificationSettings,
                )
            }
        }

        item { SettingsCategory(stringResource(R.string.notifications_tunnel_status)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.show_traffic),
                summary = stringResource(R.string.show_traffic_summary),
                icon = R.drawable.ic_baseline_domain,
                checked = dynamicNotification,
                enabled = dynamicNotificationEnabled && rowsEnabled,
                onCheckedChange = onDynamicNotification,
            )
        }
        item {
            Text(
                text = stringResource(R.string.notifications_tunnel_status_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }

        item { SettingsCategory(stringResource(R.string.notifications_subscription_updates)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.show_update_progress),
                summary = stringResource(R.string.show_update_progress_summary),
                icon = R.drawable.ic_baseline_restore,
                checked = notifySubscriptionProgress,
                enabled = rowsEnabled,
                onCheckedChange = onNotifySubscriptionProgress,
                onOpenChannelSettings = onOpenProgressChannelSettings,
            )
        }
        item {
            SwitchPreference(
                title = stringResource(R.string.show_update_errors),
                summary = stringResource(R.string.show_update_errors_summary),
                icon = R.drawable.ic_outline_info,
                checked = notifySubscriptionErrors,
                enabled = rowsEnabled,
                onCheckedChange = onNotifySubscriptionErrors,
                onOpenChannelSettings = onOpenErrorsChannelSettings,
            )
        }

        item { SettingsCategory(stringResource(R.string.notifications_subscription_alerts)) }
        item {
            SwitchPreference(
                title = stringResource(R.string.notify_subscription_alerts),
                summary = stringResource(R.string.notify_subscription_alerts_summary),
                icon = R.drawable.ic_baseline_notifications,
                checked = notifySubscriptionAlerts,
                enabled = rowsEnabled,
                onCheckedChange = onNotifySubscriptionAlerts,
                onOpenChannelSettings = onOpenSubscriptionAlertsChannelSettings,
            )
        }
        item {
            Text(
                text = stringResource(R.string.notifications_subscription_alerts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun PermissionNotice(
    status: NotificationPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val (text, buttonText, action) = when (status) {
        NotificationPermissionStatus.Blocked -> Triple(
            stringResource(R.string.notification_permission_blocked),
            stringResource(R.string.notification_open_settings),
            onOpenSettings,
        )
        else -> Triple(
            stringResource(R.string.notification_permission_ask_text),
            stringResource(R.string.notification_permission_ask_allow),
            onRequestPermission,
        )
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.padding(start = 6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = action) {
                Text(buttonText)
            }
        }
    }
}
