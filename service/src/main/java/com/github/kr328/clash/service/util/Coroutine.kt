package com.github.kr328.clash.service.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

const val STOP_JOIN_TIMEOUT_MILLIS = 3000L

/**
 * Cancels this scope's job and blocks until it finishes, up to [timeoutMillis].
 *
 * The join is time-boxed rather than unbounded: a revoked VPN fd (another app
 * took over as the system VPN) can leave the runtime's coroutines blocked on a
 * native read/write that never returns, which would otherwise hang onDestroy()
 * — and with it VpnService teardown — indefinitely.
 */
fun CoroutineScope.cancelAndJoinBlocking(timeoutMillis: Long = STOP_JOIN_TIMEOUT_MILLIS) {
    val scope = this

    runBlocking {
        scope.coroutineContext.job.cancel()

        withTimeoutOrNull(timeoutMillis) {
            scope.coroutineContext.job.join()
        }
    }
}
