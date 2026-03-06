package com.github.kr328.clash.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.github.kr328.clash.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val GITHUB_API =
        "https://api.github.com/repos/legiz-ru/Prizrak-Box/releases/latest"

    private const val PREF_NAME = "prizrak_update"
    private const val PREF_LAST_CHECK = "last_check_ms"
    private const val PREF_DOWNLOAD_ID = "download_id"
    private const val APK_FILE_NAME = "prizrak-box-update.apk"

    const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours
    const val CHANNEL_ID = "prizrak_update_channel"
    const val NOTIFICATION_ID = 9001

    const val ACTION_SHOW_UPDATE = "com.github.kr328.clash.action.SHOW_UPDATE"
    const val EXTRA_TAG = "update_tag"
    const val EXTRA_URL = "update_url"

    sealed class CheckResult {
        data class UpdateAvailable(val tagName: String, val downloadUrl: String) : CheckResult()
        object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    fun shouldCheck(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS
    }

    suspend fun check(context: Context): CheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext CheckResult.Error("HTTP ${conn.responseCode}")
            }

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val obj = JSONObject(json)
            val tagName = obj.getString("tag_name")
            val assets = obj.getJSONArray("assets")

            val preferredName = preferredApkName()
            var downloadUrl = ""

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name") == preferredName) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }
            // Fallback to universal if exact ABI match not found
            if (downloadUrl.isEmpty()) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").contains("universal")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
            }

            // Record check time
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_CHECK, System.currentTimeMillis())
                .apply()

            @Suppress("DEPRECATION")
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0).versionName ?: "0"

            if (downloadUrl.isNotEmpty() && isNewer(tagName, currentVersion)) {
                CheckResult.UpdateAvailable(tagName, downloadUrl)
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: "Unknown error")
        }
    }

    fun startDownload(context: Context, downloadUrl: String, tagName: String) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Remove previous download if any
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val prevId = prefs.getLong(PREF_DOWNLOAD_ID, -1L)
        if (prevId != -1L) {
            runCatching { dm.remove(prevId) }
        }

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Prizrak-Box $tagName")
            .setDescription("Загрузка обновления...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val id = dm.enqueue(request)
        prefs.edit().putLong(PREF_DOWNLOAD_ID, id).apply()
    }

    fun installDownloadedApk(context: Context) {
        val file = apkFile(context)
        if (!file.exists()) return

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.update.provider",
                file
            )
        } else {
            @Suppress("DEPRECATION")
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    fun showUpdateNotification(context: Context, tagName: String, downloadUrl: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_SHOW_UPDATE
            putExtra(EXTRA_TAG, tagName)
            putExtra(EXTRA_URL, downloadUrl)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Доступно обновление Prizrak-Box")
            .setContentText("Версия $tagName готова к загрузке")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Обновления приложения",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления об обновлениях Prizrak-Box"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun apkFile(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)

    private fun preferredApkName(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        return when {
            abi.contains("arm64") -> "prizrak-box-arm64-v8a-release.apk"
            abi.contains("x86_64") -> "prizrak-box-x86_64-release.apk"
            abi.contains("armeabi") -> "prizrak-box-armeabi-v7a-release.apk"
            abi.contains("x86") -> "prizrak-box-x86-release.apk"
            else -> "prizrak-box-universal-release.apk"
        }
    }

    private fun isNewer(tag: String, current: String): Boolean {
        val remote = tag.trimStart('v', 'V').split('.').mapNotNull { it.toIntOrNull() }
        val local = current.trimStart('v', 'V').split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
