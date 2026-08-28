package com.github.kr328.clash.common

import android.app.Application
import com.github.kr328.clash.common.log.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

// A plain CoroutineScope(Dispatchers.IO) has a regular Job: one uncaught
// exception in anything launched here cancels the whole scope permanently,
// and every later Global.launch{} silently no-ops until the process
// restarts — the widget and broadcast receivers that depend on this scope
// would quietly stop working. SupervisorJob isolates failures to the child
// that threw; the handler logs it instead of letting it reach the default
// uncaught-exception path.
object Global : CoroutineScope by CoroutineScope(
    Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e ->
        Log.e("Global coroutine failed: $e", e)
    }
) {
    val application: Application
        get() = application_

    private lateinit var application_: Application

    fun init(application: Application) {
        this.application_ = application
    }

    fun destroy() {
        cancel()
    }
}