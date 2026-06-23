package com.github.kr328.clash.design.dialog

import android.content.Context
import android.widget.TextView
import com.github.kr328.clash.core.model.FetchStatus
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.layoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ModelProgressBarConfigure {
    var isIndeterminate: Boolean
    var text: String?
    var progress: Int
    var max: Int
}

/**
 * Maps a fetch [status] reported during profile commit onto the progress dialog,
 * so callers (import flow, properties screen) show live per-provider progress
 * instead of a static indeterminate message.
 */
fun ModelProgressBarConfigure.applyFetchStatus(context: Context, status: FetchStatus) {
    when (status.action) {
        FetchStatus.Action.FetchConfiguration -> {
            text = context.getString(R.string.format_fetching_configuration, status.args[0])
            isIndeterminate = true
        }
        FetchStatus.Action.FetchProviders -> {
            text = context.getString(R.string.format_fetching_provider, status.args[0])
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
        FetchStatus.Action.FetchIcons -> {
            text = context.getString(R.string.fetching_icons)
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
        FetchStatus.Action.Verifying -> {
            text = context.getString(R.string.verifying)
            isIndeterminate = false
            max = status.max
            progress = status.progress
        }
    }
}

interface ModelProgressBarScope {
    suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit)
}

suspend fun Context.withModelProgressBar(block: suspend ModelProgressBarScope.() -> Unit) {
    val view = layoutInflater.inflate(R.layout.dialog_fetch_status, null)
    val progressIndicator = view.findViewById<LinearProgressIndicator>(R.id.progress_indicator)
    val textView = view.findViewById<TextView>(R.id.text)

    val dialog = MaterialAlertDialogBuilder(this)
        .setCancelable(false)
        .setView(view)
        .show()

    val configureImpl = object : ModelProgressBarConfigure {
        override var isIndeterminate: Boolean
            get() = progressIndicator.isIndeterminate
            set(value) {
                progressIndicator.isIndeterminate = value
            }
        override var text: String?
            get() = textView.text?.toString()
            set(value) {
                textView.text = value
            }
        override var progress: Int
            get() = progressIndicator.progress
            set(value) {
                progressIndicator.setProgressCompat(value, true)
            }
        override var max: Int
            get() = progressIndicator.max
            set(value) {
                progressIndicator.max = value
            }
    }

    val scopeImpl = object : ModelProgressBarScope {
        override suspend fun configure(block: suspend ModelProgressBarConfigure.() -> Unit) {
            withContext(Dispatchers.Main) {
                configureImpl.block()
            }
        }
    }

    try {
        scopeImpl.block()
    } finally {
        dialog.dismiss()
    }
}
