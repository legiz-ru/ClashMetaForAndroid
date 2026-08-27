package com.github.kr328.clash.service.subscription

import java.util.concurrent.TimeUnit

sealed interface SubscriptionAlert {
    data object Expired : SubscriptionAlert

    data class ExpiresIn(val days: Int) : SubscriptionAlert

    data class TrafficUsed(val percent: Int) : SubscriptionAlert
}

/**
 * Decides which subscription-state alerts to show, from a plain snapshot of
 * the subscription and of what has already been shown. Knows nothing about
 * notifications, headers, or storage — those are [SubscriptionAlertReporter]'s
 * job, so this stays trivial to reason about and to test.
 */
object SubscriptionAlerts {
    val DEFAULT_EXPIRE_DAYS = listOf(1, 3, 7)

    val DEFAULT_TRAFFIC_PERCENT = listOf(80, 90, 100)

    private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)

    private const val EXPIRED_KEY = "expired"

    private const val EXPIRE_BASE_KEY = "expire_base"

    private fun expireKey(days: Int) = "expire_${days}d"

    private fun trafficKey(percent: Int) = "traffic_$percent"

    data class Snapshot(
        val expireAt: Long,
        val total: Long,
        val used: Long,
        val expireDays: List<Int>,
        val trafficPercent: List<Int>,
        val notified: Map<String, Long>,
    )

    data class Outcome(
        val alerts: List<SubscriptionAlert>,
        val notified: Map<String, Long>,
    )

    /**
     * [nowMillis] should already carry the panel clock-skew correction — this
     * function just compares numbers, it doesn't know or care where they came
     * from.
     */
    fun evaluate(snapshot: Snapshot, nowMillis: Long): Outcome {
        val map = snapshot.notified.toMutableMap()

        // Drop bookkeeping for thresholds that no longer apply — the panel
        // changed its mind about which days/percentages matter.
        val valid = { key: String ->
            key == EXPIRE_BASE_KEY ||
                (key == EXPIRED_KEY && snapshot.expireDays.isNotEmpty()) ||
                snapshot.expireDays.any { key == expireKey(it) } ||
                snapshot.trafficPercent.any { key == trafficKey(it) }
        }
        map.keys.retainAll { valid(it) }

        val alerts = mutableListOf<SubscriptionAlert>()

        if (snapshot.expireAt != 0L && snapshot.expireDays.isNotEmpty()) {
            // The expiry moment itself changed (renewal, panel clock fixed) —
            // every prior "shown" mark for this category is about a different
            // deadline and means nothing now.
            if (map[EXPIRE_BASE_KEY] != snapshot.expireAt) {
                map.keys.retainAll { !it.startsWith("expire_") && it != EXPIRED_KEY }
                map[EXPIRE_BASE_KEY] = snapshot.expireAt
            }

            val remaining = snapshot.expireAt - nowMillis

            // Time moved back across a threshold (renewal) — rearm it so the
            // next real crossing notifies again instead of staying "shown".
            if (remaining > 0) {
                map.remove(EXPIRED_KEY)
            }
            for (days in snapshot.expireDays) {
                if (remaining > days * DAY_MILLIS) {
                    map.remove(expireKey(days))
                }
            }

            val passed = mutableListOf<Pair<String, SubscriptionAlert?>>()
            if (remaining <= 0) {
                passed += EXPIRED_KEY to SubscriptionAlert.Expired
            }
            for (days in snapshot.expireDays.sorted()) {
                if (remaining > 0 && remaining <= days * DAY_MILLIS) {
                    passed += expireKey(days) to SubscriptionAlert.ExpiresIn(days)
                } else if (remaining <= 0) {
                    passed += expireKey(days) to null
                }
            }

            // At most one alert per category per pass: a subscription that sat
            // untouched past every threshold at once (first run after a long
            // gap) should say "expired" once, not "7 days / 3 days / 1 day /
            // expired" back to back.
            var shown = false
            for ((key, alert) in passed) {
                if (!map.containsKey(key)) {
                    if (!shown && alert != null) {
                        alerts += alert
                        shown = true
                    }
                    map[key] = nowMillis
                } else if (alert != null) {
                    shown = true
                }
            }
        } else if (snapshot.expireAt == 0L) {
            map.keys.retainAll {
                !it.startsWith("expire_") && it != EXPIRED_KEY && it != EXPIRE_BASE_KEY
            }
        }

        if (snapshot.total > 0 && snapshot.trafficPercent.isNotEmpty()) {
            val used = snapshot.used.coerceAtLeast(0)
            val reached = { threshold: Int -> percentReached(used, snapshot.total, threshold) }

            for (threshold in snapshot.trafficPercent) {
                if (!reached(threshold)) {
                    map.remove(trafficKey(threshold))
                }
            }

            var shown = false
            for (threshold in snapshot.trafficPercent.filter { reached(it) }.sortedDescending()) {
                val key = trafficKey(threshold)
                if (!map.containsKey(key)) {
                    if (!shown) {
                        alerts += SubscriptionAlert.TrafficUsed(threshold)
                        shown = true
                    }
                    map[key] = nowMillis
                } else {
                    shown = true
                }
            }
        }

        return Outcome(alerts = alerts, notified = map)
    }

    private fun percentReached(used: Long, total: Long, threshold: Int): Boolean {
        val whole = used / total * 100
        val rest = used % total * 100 / total

        return whole + rest >= threshold
    }
}
