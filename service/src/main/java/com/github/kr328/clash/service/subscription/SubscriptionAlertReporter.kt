package com.github.kr328.clash.service.subscription

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.github.kr328.clash.common.compat.getColorCompat
import com.github.kr328.clash.common.compat.pendingIntentFlags
import com.github.kr328.clash.common.constants.Components
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.service.ProfileProcessor
import com.github.kr328.clash.service.R
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val STATE_FILE = "alerts.json"

const val SUBSCRIPTION_ALERT_CHANNEL = "subscription_alert_channel"

/**
 * One channel for all three subscription-alert kinds — "expiring soon",
 * "expired", "traffic used" — matching the one switch that gates all three
 * (see ServiceStore.notifySubscriptionAlerts): they're the same underlying
 * thing to the user ("the subscription needs attention"), so one place to
 * tune their sound/vibration in system settings is the right granularity,
 * same as the one place to turn them off in-app.
 *
 * Created eagerly at app startup (see MainApplication), not lazily on first
 * alert, so the "open channel settings" shortcut in the notification
 * settings screen always has a channel to point at.
 */
fun Context.createSubscriptionAlertChannels() {
    NotificationManagerCompat.from(this).createNotificationChannelsCompat(
        listOf(
            NotificationChannelCompat.Builder(
                SUBSCRIPTION_ALERT_CHANNEL,
                NotificationManagerCompat.IMPORTANCE_DEFAULT,
            ).setName(getString(R.string.subscription_alert_channel)).build(),
        ),
    )
}

/**
 * Evaluates and, where due, posts subscription expiry/traffic reminders for
 * one profile.
 *
 * Called from two places: after every subscription update — successful or
 * not, since the numbers this reads (expire/total/used, and the device-clock
 * correction) come from disk, not from the network request that just ran —
 * and when the main screen opens, because the update interval can be set to
 * manual, in which case nothing else ever calls this at all. There is no
 * dedicated alarm for it: waking the device just to check a date is not worth
 * it, and both existing triggers already happen often enough.
 */
suspend fun Context.reportSubscriptionAlerts(uuid: UUID) {
    // A blocked system permission is a hard stop, checked BEFORE any state is
    // touched: if we recorded a threshold as "shown" while nothing could
    // actually be shown, the real alert would never fire once permission comes
    // back — the notification the user never saw would already look answered.
    if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

    val imported = ImportedDao().queryByUUID(uuid) ?: return

    val profileDir = importedDir.resolve(uuid.toString())
    val headers = ProfileProcessor.readProfileHeaders(profileDir)

    // null means the panel sent no notify-* header at all for this kind of
    // reminder — not "use our own defaults", but "this panel doesn't opt in
    // to it". SubscriptionAlerts.DEFAULT_EXPIRE_DAYS/DEFAULT_TRAFFIC_PERCENT
    // only fill in the *thresholds* when the panel opted in with a bare
    // `notification-subs-expire: true` (see ProfileProcessor.saveProfileHeaders);
    // they are never a substitute for the header being absent entirely.
    val expireDays = headers.notifyExpireDays ?: emptyList()
    val trafficPercent = headers.notifyTrafficPercent ?: emptyList()

    if (expireDays.isEmpty() && trafficPercent.isEmpty()) {
        // The panel sent no reminder headers (or explicitly turned both off) —
        // nothing to track, and a leftover state file would just be dead weight.
        stateFile(profileDir).delete()

        return
    }

    val previous = readState(profileDir)

    val outcome = SubscriptionAlerts.evaluate(
        snapshot = SubscriptionAlerts.Snapshot(
            expireAt = imported.expire,
            total = imported.total,
            used = imported.upload + imported.download,
            expireDays = expireDays,
            trafficPercent = trafficPercent,
            notified = previous,
        ),
        nowMillis = System.currentTimeMillis() + headers.clockSkewMillis(),
    )

    if (outcome.notified != previous) {
        writeState(profileDir, outcome.notified)
    }

    if (outcome.alerts.isEmpty()) return

    // The toggle is applied here, AFTER evaluate() rather than by feeding it
    // empty threshold lists: the calculation and the state write above always
    // run, so a threshold crossed while the toggle was off is still marked
    // "shown" and will not flood the user the moment they turn it back on.
    if (!ServiceStore(this).notifySubscriptionAlerts) return

    outcome.alerts.forEach { notifyAlert(imported.uuid, it, imported.name) }
}

/** Intent extras carried by a subscription-alert notification's tap target. */
const val EXTRA_SUBSCRIPTION_ALERT_UUID = "subscription_alert_uuid"
const val EXTRA_SUBSCRIPTION_ALERT_KIND = "subscription_alert_kind"

/** Same coded-string convention as the rest of this codebase's cross-boundary
 *  reasons (HwidNotSupportedException, FETCH_* — see ProfileProcessor): a
 *  plain Intent extra can't carry a sealed type, so the tap target on the
 *  other side (MainActivity) recovers the alert kind from this string. */
private fun SubscriptionAlert.toKindCode(): String = when (this) {
    is SubscriptionAlert.Expired -> "EXPIRED"
    is SubscriptionAlert.ExpiresIn -> "EXPIRES_IN:$days"
    is SubscriptionAlert.TrafficUsed -> "TRAFFIC_USED:$percent"
}

private fun stateFile(profileDir: File): File = profileDir.resolve(STATE_FILE)

private fun readState(profileDir: File): Map<String, Long> {
    val file = stateFile(profileDir)

    if (!file.isFile) return emptyMap()

    return try {
        val json = JSONObject(file.readText())
        val result = mutableMapOf<String, Long>()

        json.keys().forEach { key -> result[key] = json.getLong(key) }

        result
    } catch (e: Exception) {
        Log.w("Read $STATE_FILE of $profileDir: $e", e)

        emptyMap()
    }
}

private fun writeState(profileDir: File, value: Map<String, Long>) {
    val file = stateFile(profileDir)

    try {
        if (value.isEmpty()) {
            file.delete()
        } else {
            val json = JSONObject()
            value.forEach { (key, at) -> json.put(key, at) }
            file.writeText(json.toString())
        }
    } catch (e: Exception) {
        Log.w("Write $STATE_FILE of $profileDir: $e", e)
    }
}

private fun Context.notifyAlert(uuid: UUID, alert: SubscriptionAlert, profileName: String) {
    // One shared channel (see createSubscriptionAlertChannels), but still a
    // distinct notification id per kind — so a fresh "expires in 3 days"
    // replaces a stale "expires in 7 days" instead of stacking, while an
    // unrelated "traffic used" alert for the same profile stays a separate
    // notification rather than clobbering it.
    val id = when (alert) {
        is SubscriptionAlert.ExpiresIn -> R.id.nf_subscription_expiring
        is SubscriptionAlert.Expired -> R.id.nf_subscription_expired
        is SubscriptionAlert.TrafficUsed -> R.id.nf_subscription_traffic
    }

    val title = when (alert) {
        is SubscriptionAlert.Expired -> getString(R.string.subscription_expired)
        is SubscriptionAlert.ExpiresIn -> resources.getQuantityString(
            R.plurals.subscription_expires_in_days,
            alert.days,
            alert.days,
        )
        is SubscriptionAlert.TrafficUsed -> getString(R.string.subscription_traffic_used, alert.percent)
    }

    val intent = PendingIntent.getActivity(
        this,
        id,
        Intent().setComponent(Components.MAIN_ACTIVITY)
            .putExtra(EXTRA_SUBSCRIPTION_ALERT_UUID, uuid.toString())
            .putExtra(EXTRA_SUBSCRIPTION_ALERT_KIND, alert.toKindCode()),
        pendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT),
    )

    val notification = NotificationCompat.Builder(this, SUBSCRIPTION_ALERT_CHANNEL)
        .setColor(getColorCompat(R.color.color_clash))
        .setSmallIcon(R.drawable.ic_logo_service)
        .setContentTitle(title)
        .setContentText(profileName)
        .setContentIntent(intent)
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(this).notify(id, notification)
}
