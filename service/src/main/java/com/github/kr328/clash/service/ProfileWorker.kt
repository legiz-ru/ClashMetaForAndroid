package com.github.kr328.clash.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.compat.startForegroundCompat
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.id.UndefinedIds
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.subscription.reportSubscriptionAlerts
import com.github.kr328.clash.service.util.sendProfileUpdateCompleted
import com.github.kr328.clash.service.util.sendProfileUpdateFailed
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Channels for [ProfileWorker]'s non-foreground notifications, created eagerly
 * at app startup (see MainApplication) rather than lazily on first use: the
 * per-channel "open settings" shortcut in the notification settings screen
 * uses `ACTION_CHANNEL_NOTIFICATION_SETTINGS`, which needs the channel to
 * already exist — someone opening that screen before ever updating a
 * subscription would otherwise get a blank or missing settings page.
 */
fun Context.createProfileWorkerChannels() {
    NotificationManagerCompat.from(this).createNotificationChannelsCompat(
        listOf(
            NotificationChannelCompat.Builder(
                ProfileWorker.SERVICE_CHANNEL,
                NotificationManagerCompat.IMPORTANCE_LOW
            ).setName(getString(R.string.profile_service_status)).build(),
            NotificationChannelCompat.Builder(
                ProfileWorker.STATUS_CHANNEL,
                NotificationManagerCompat.IMPORTANCE_LOW
            ).setName(getString(R.string.profile_process_status)).build(),
            NotificationChannelCompat.Builder(
                ProfileWorker.RESULT_CHANNEL,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            ).setName(getString(R.string.profile_process_result)).build()
        )
    )
}

class ProfileWorker : BaseService() {
    private val service: ProfileWorker
        get() = this

    private val jobs = mutableListOf<Job>()

    override fun onCreate() {
        super.onCreate()

        createProfileWorkerChannels()

        foreground()

        launch {
            delay(TimeUnit.SECONDS.toMillis(10))

            while (true) {
                jobs.removeFirstOrNull()?.join() ?: break
            }

            stopSelf()
        }
    }

    override fun onDestroy() {
        stopForeground(true)

        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            Intents.ACTION_PROFILE_REQUEST_UPDATE -> {
                intent.uuid?.also {
                    val job = launch {
                        run(it)
                    }

                    jobs.add(job)
                }
            }
            Intents.ACTION_PROFILE_SCHEDULE_UPDATES -> {
                val job = launch {
                    ProfileReceiver.rescheduleAll(service)

                    delay(TimeUnit.SECONDS.toMillis(30))
                }

                jobs.add(job)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun run(uuid: UUID) {
        val imported = ImportedDao().queryByUUID(uuid) ?: return

        try {
            processing(imported.name) {
                ProfileProcessor.update(this, imported.uuid, null)
            }

            completed(imported.uuid, imported.name)

            ProfileReceiver.scheduleNext(this, imported)
        } catch (e: Exception) {
            failed(imported.uuid, imported.name, e.message ?: "Unknown")
        }

        // Expiry/traffic thresholds are evaluated against numbers already on
        // disk and the device clock (corrected for the panel's), not against
        // anything this update fetched — so this runs whether the update above
        // just succeeded or failed. A failing update is often exactly the
        // moment a subscription ran out, and that's the one time this must not
        // stay silent.
        reportSubscriptionAlerts(uuid)
    }

    private fun foreground() {
        val notification = NotificationCompat.Builder(this, SERVICE_CHANNEL)
            .setContentTitle(getString(R.string.profile_updater))
            .setContentText(getString(R.string.service_running))
            .setColor(getColorCompat(R.color.color_clash))
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        startForegroundCompat(R.id.nf_profile_worker, notification)
    }

    private suspend inline fun processing(name: String, block: () -> Unit) {
        // Purely cosmetic — unlike the SERVICE_CHANNEL notification posted in
        // foreground(), this one is a plain notify(), not tied to the foreground
        // service contract, so it is fine to skip posting it altogether.
        val showProgress = ServiceStore(applicationContext).notifySubscriptionProgress
        val id = if (showProgress) UndefinedIds.next() else 0

        if (showProgress) {
            val notification = NotificationCompat.Builder(this, STATUS_CHANNEL)
                .setContentTitle(getString(R.string.profile_updating))
                .setContentText(name)
                .setColor(getColorCompat(R.color.color_clash))
                .setSmallIcon(R.drawable.ic_logo_service)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setGroup(STATUS_CHANNEL)
                .build()

            NotificationManagerCompat.from(applicationContext)
                .notify(id, notification)
        }
        try {
            block()
        } finally {
            if (showProgress) {
                withContext(NonCancellable) {
                    NotificationManagerCompat.from(applicationContext)
                        .cancel(id)
                }
            }
        }
    }

    private fun resultBuilder(id: Int, uuid: UUID): NotificationCompat.Builder {
        val intent = PendingIntent.getActivity(
            this,
            id,
            Intent().setComponent(Components.PROPERTIES_ACTIVITY).setUUID(uuid),
            pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        return NotificationCompat.Builder(this, RESULT_CHANNEL)
            .setColor(getColorCompat(R.color.color_clash))
            .setSmallIcon(R.drawable.ic_logo_service)
            .setOnlyAlertOnce(true)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setGroup(RESULT_CHANNEL)
    }

    private fun completed(uuid: UUID, name: String) {
        sendProfileUpdateCompleted(uuid)
    }

    private fun failed(uuid: UUID, name: String, reason: String) {
        if (ServiceStore(this).notifySubscriptionErrors) {
            val id = UndefinedIds.next()

            val content = getString(
                R.string.format_update_failure,
                name,
                ProfileProcessor.describeFetchFailureReason(this, reason)
            )

            val notification = resultBuilder(id, uuid)
                .setContentTitle(getString(R.string.update_failure))
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .build()

            NotificationManagerCompat.from(this)
                .notify(id, notification)
        }

        sendProfileUpdateFailed(uuid, reason)
    }

    companion object {
        const val SERVICE_CHANNEL = "profile_service_channel"
        const val STATUS_CHANNEL = "profile_status_channel"
        const val RESULT_CHANNEL = "profile_result_channel"
    }

    override fun onBind(intent: Intent?): IBinder {
        return Binder()
    }
}