package com.github.kr328.clash

import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.compose.screen.NotificationPermissionStatus
import com.github.kr328.clash.design.compose.screen.NotificationSettingsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.subscription.EXPIRED_CHANNEL
import com.github.kr328.clash.service.subscription.EXPIRING_SOON_CHANNEL
import com.github.kr328.clash.service.subscription.TRAFFIC_CHANNEL
import com.github.kr328.clash.util.notificationPermissionStatus
import com.github.kr328.clash.util.openSystemNotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive

/**
 * Everything the app can notify the user about, in one place: the permission
 * itself, then one switch per notification kind — including two the panel
 * drives (expiry/traffic thresholds), see SubscriptionAlertReporter.
 */
class NotificationSettingsActivity : BaseActivity() {
    private val srvStore by lazy { ServiceStore(this) }

    private val permissionStatusFlow = MutableStateFlow(NotificationPermissionStatus.Granted)
    private val dynamicNotificationFlow = MutableStateFlow(false)
    private val notifySubscriptionProgressFlow = MutableStateFlow(true)
    private val notifySubscriptionErrorsFlow = MutableStateFlow(true)
    private val notifyExpiringSoonFlow = MutableStateFlow(true)
    private val notifyExpiredFlow = MutableStateFlow(true)
    private val notifyTrafficUsedFlow = MutableStateFlow(true)

    override suspend fun main() {
        refreshPermissionStatus()
        dynamicNotificationFlow.value = srvStore.dynamicNotification
        notifySubscriptionProgressFlow.value = srvStore.notifySubscriptionProgress
        notifySubscriptionErrorsFlow.value = srvStore.notifySubscriptionErrors
        notifyExpiringSoonFlow.value = srvStore.notifyExpiringSoon
        notifyExpiredFlow.value = srvStore.notifyExpired
        notifyTrafficUsedFlow.value = srvStore.notifyTrafficUsed

        setContent {
            val permissionStatus by permissionStatusFlow.collectAsStateWithLifecycle()
            val dynamicNotification by dynamicNotificationFlow.collectAsStateWithLifecycle()
            val notifySubscriptionProgress by notifySubscriptionProgressFlow.collectAsStateWithLifecycle()
            val notifySubscriptionErrors by notifySubscriptionErrorsFlow.collectAsStateWithLifecycle()
            val notifyExpiringSoon by notifyExpiringSoonFlow.collectAsStateWithLifecycle()
            val notifyExpired by notifyExpiredFlow.collectAsStateWithLifecycle()
            val notifyTrafficUsed by notifyTrafficUsedFlow.collectAsStateWithLifecycle()

            ClashTheme(variant = currentThemeVariant()) {
                NotificationSettingsScreen(
                    onBack = { finish() },
                    permissionStatus = permissionStatus,
                    onRequestPermission = ::requestPermission,
                    onOpenSystemNotificationSettings = { openSystemNotificationSettings() },
                    dynamicNotification = dynamicNotification,
                    dynamicNotificationEnabled = !clashRunning,
                    onDynamicNotification = {
                        srvStore.dynamicNotification = it
                        dynamicNotificationFlow.value = it
                    },
                    notifySubscriptionProgress = notifySubscriptionProgress,
                    onNotifySubscriptionProgress = {
                        srvStore.notifySubscriptionProgress = it
                        notifySubscriptionProgressFlow.value = it
                    },
                    onOpenProgressChannelSettings = {
                        openSystemNotificationSettings(ProfileWorker.STATUS_CHANNEL)
                    },
                    notifySubscriptionErrors = notifySubscriptionErrors,
                    onNotifySubscriptionErrors = {
                        srvStore.notifySubscriptionErrors = it
                        notifySubscriptionErrorsFlow.value = it
                    },
                    onOpenErrorsChannelSettings = {
                        openSystemNotificationSettings(ProfileWorker.RESULT_CHANNEL)
                    },
                    notifyExpiringSoon = notifyExpiringSoon,
                    onNotifyExpiringSoon = {
                        srvStore.notifyExpiringSoon = it
                        notifyExpiringSoonFlow.value = it
                    },
                    onOpenExpiringSoonChannelSettings = {
                        openSystemNotificationSettings(EXPIRING_SOON_CHANNEL)
                    },
                    notifyExpired = notifyExpired,
                    onNotifyExpired = {
                        srvStore.notifyExpired = it
                        notifyExpiredFlow.value = it
                    },
                    onOpenExpiredChannelSettings = {
                        openSystemNotificationSettings(EXPIRED_CHANNEL)
                    },
                    notifyTrafficUsed = notifyTrafficUsed,
                    onNotifyTrafficUsed = {
                        srvStore.notifyTrafficUsed = it
                        notifyTrafficUsedFlow.value = it
                    },
                    onOpenTrafficChannelSettings = {
                        openSystemNotificationSettings(TRAFFIC_CHANNEL)
                    },
                )
            }
        }

        while (isActive) {
            when (events.receive()) {
                Event.ClashStart, Event.ClashStop -> Unit
                // Covers coming back from the system permission/channel settings
                // screens this activity itself launches.
                Event.ActivityStart -> refreshPermissionStatus()
                else -> Unit
            }
        }
    }

    private suspend fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                startActivityForResult(
                    RequestPermission(),
                    android.Manifest.permission.POST_NOTIFICATIONS,
                )
            } catch (e: Exception) {
                com.github.kr328.clash.common.log.Log.w("Request notifications: $e", e)
            }
        }

        uiStore.notificationsAsked = true

        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        permissionStatusFlow.value = notificationPermissionStatus()
    }

    private fun currentThemeVariant(): ClashThemeVariant {
        val cfg = resources.configuration
        return when (uiStore.darkMode) {
            DarkMode.Auto ->
                if (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ClashThemeVariant.Dark
                } else {
                    ClashThemeVariant.Light
                }
            DarkMode.ForceLight -> ClashThemeVariant.Light
            DarkMode.ForceDark -> ClashThemeVariant.Dark
            DarkMode.AlwaysSummer -> ClashThemeVariant.Summer
        }
    }
}
