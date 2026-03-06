package com.github.kr328.clash.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives ACTION_DOWNLOAD_COMPLETE from the system DownloadManager.
 * When the APK download finishes successfully, launches the package installer.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val finishedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (finishedId == -1L) return

        val prefs = context.getSharedPreferences("prizrak_update", Context.MODE_PRIVATE)
        val trackedId = prefs.getLong("download_id", -1L)
        if (finishedId != trackedId) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(finishedId)
        val cursor = dm.query(query)
        val success = cursor.use { c ->
            if (c.moveToFirst()) {
                val statusCol = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                c.getInt(statusCol) == DownloadManager.STATUS_SUCCESSFUL
            } else false
        }

        if (success) {
            UpdateChecker.installDownloadedApk(context)
        }
    }
}
