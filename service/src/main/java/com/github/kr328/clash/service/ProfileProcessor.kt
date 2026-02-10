package com.github.kr328.clash.service

import android.content.Context
import android.net.Uri
import android.os.Build
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.data.Imported
import com.github.kr328.clash.service.data.ImportedDao
import com.github.kr328.clash.service.data.Pending
import com.github.kr328.clash.service.data.PendingDao
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.remote.IFetchObserver
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.service.util.pendingDir
import com.github.kr328.clash.service.util.processingDir
import com.github.kr328.clash.service.util.sendProfileChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileProcessor {

    fun buildProfileRequest(context: Context, url: String): Request {
        val uiPrefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val sendHwid = uiPrefs.getBoolean("send_hwid", true)
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName

        val builder = Request.Builder().url(url)

        if (sendHwid) {
            builder.header("User-Agent", "prizrak-box/$versionName")
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            builder.header("x-hwid", deviceId)
            builder.header("x-device-os", "Android")
            builder.header("x-ver-os", Build.VERSION.RELEASE ?: "unknown")
            builder.header("x-device-model", Build.MODEL ?: "unknown")
        } else {
            builder.header("User-Agent", "ClashMetaForAndroid/$versionName")
        }

        return builder.build()
    }
    private val profileLock = Mutex()
    private val processLock = Mutex()

    private fun prefetchProfileConfig(context: Context, source: String, targetConfigFile: File): Boolean {
        return try {
            val request = buildProfileRequest(context, source)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false

                val body = response.body ?: return false
                targetConfigFile.parentFile?.mkdirs()
                targetConfigFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (_: IOException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun apply(context: Context, uuid: UUID, callback: IFetchObserver? = null) {
        withContext(NonCancellable) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val pending = PendingDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    pending.enforceFieldValid()

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.pendingDir.resolve(pending.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    pending
                }

                val prefetched = snapshot.type == Profile.Type.Url &&
                        (snapshot.source.startsWith("https://", true) || snapshot.source.startsWith("http://", true)) &&
                        prefetchProfileConfig(context, snapshot.source, context.processingDir.resolve("config.yaml"))

                val fetchSource = if (prefetched) {
                    context.processingDir.resolve("config.yaml").toURI().toString()
                } else {
                    snapshot.source
                }
                val force = if (prefetched) false else snapshot.type != Profile.Type.File
                var cb = callback

                Clash.fetchAndValid(context.processingDir, fetchSource, force) {
                    try {
                        cb?.updateStatus(it)
                    } catch (e: Exception) {
                        cb = null

                        Log.w("Report fetch status: $e", e)
                    }
                }.await()

                profileLock.withLock {
                    if (PendingDao().queryByUUID(snapshot.uuid) == snapshot) {
                        context.importedDir.resolve(snapshot.uuid.toString())
                            .deleteRecursively()
                        context.processingDir
                            .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        val old = ImportedDao().queryByUUID(snapshot.uuid)
                        var upload: Long = 0
                        var download: Long = 0
                        var total: Long = 0
                        var expire: Long = 0
                        if (snapshot?.type == Profile.Type.Url) {
                            if (snapshot.source.startsWith("https://", true)) {
                                val client = OkHttpClient()
                                val request = buildProfileRequest(context, snapshot.source)

                                client.newCall(request).execute().use { response ->
                                    val userinfo = response.headers["subscription-userinfo"]
                                    if (response.isSuccessful && userinfo != null) {
                                        val flags = userinfo.split(";")
                                        for (flag in flags) {
                                            val info = flag.split("=")
                                            when {
                                                info[0].contains("upload") && info[1].isNotEmpty() -> upload =
                                                    BigDecimal(info[1].split('.').first()).longValueExact()

                                                info[0].contains("download") && info[1].isNotEmpty() -> download =
                                                    BigDecimal(info[1].split('.').first()).longValueExact()

                                                info[0].contains("total") && info[1].isNotEmpty() -> total =
                                                    BigDecimal(info[1].split('.').first()).longValueExact()

                                                info[0].contains("expire") && info[1].isNotEmpty() ->  expire =
                                                    (info[1].toDouble() * 1000).toLong()
                                            }
                                        }
                                    }
                                    if (response.isSuccessful) {
                                        saveProfileHeaders(
                                            context.importedDir.resolve(snapshot.uuid.toString()),
                                            response.headers
                                        )
                                    }
                                }
                            }
                            val new = Imported(
                                snapshot.uuid,
                                snapshot.name,
                                snapshot.type,
                                snapshot.source,
                                snapshot.interval,
                                upload,
                                download,
                                total,
                                expire,
                                old?.createdAt ?: System.currentTimeMillis()
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        } else if (snapshot?.type == Profile.Type.File) {
                            val new = Imported(
                                snapshot.uuid,
                                snapshot.name,
                                snapshot.type,
                                snapshot.source,
                                snapshot.interval,
                                upload,
                                download,
                                total,
                                expire,
                                old?.createdAt ?: System.currentTimeMillis()
                            )
                            if (old != null) {
                                ImportedDao().update(new)
                            } else {
                                ImportedDao().insert(new)
                            }

                            PendingDao().remove(snapshot.uuid)

                            context.pendingDir.resolve(snapshot.uuid.toString())
                                .deleteRecursively()

                            context.sendProfileChanged(snapshot.uuid)
                        }
                    }
                }
            }
        }
    }

    suspend fun update(context: Context, uuid: UUID, callback: IFetchObserver?) {
        withContext(NonCancellable) {
            processLock.withLock {
                val snapshot = profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.importedDir.resolve(imported.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    imported
                }

                val prefetched = snapshot.type == Profile.Type.Url &&
                        (snapshot.source.startsWith("https://", true) || snapshot.source.startsWith("http://", true)) &&
                        prefetchProfileConfig(context, snapshot.source, context.processingDir.resolve("config.yaml"))

                var cb = callback

                val fetchSource = if (prefetched) {
                    context.processingDir.resolve("config.yaml").toURI().toString()
                } else {
                    snapshot.source
                }

                Clash.fetchAndValid(context.processingDir, fetchSource, !prefetched) {
                    try {
                        cb?.updateStatus(it)
                    } catch (e: Exception) {
                        cb = null

                        Log.w("Report fetch status: $e", e)
                    }
                }.await()

                profileLock.withLock {
                    if (ImportedDao().exists(snapshot.uuid)) {
                        context.importedDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                        context.processingDir
                            .copyRecursively(context.importedDir.resolve(snapshot.uuid.toString()))

                        context.sendProfileChanged(snapshot.uuid)
                    }
                }
            }
        }
    }

    suspend fun delete(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                ImportedDao().remove(uuid)
                PendingDao().remove(uuid)

                val pending = context.pendingDir.resolve(uuid.toString())
                val imported = context.importedDir.resolve(uuid.toString())

                pending.deleteRecursively()
                imported.deleteRecursively()

                context.sendProfileChanged(uuid)
            }
        }
    }

    suspend fun release(context: Context, uuid: UUID): Boolean {
        return withContext(NonCancellable) {
            profileLock.withLock {
                PendingDao().remove(uuid)

                context.pendingDir.resolve(uuid.toString()).deleteRecursively()
            }
        }
    }

    suspend fun active(context: Context, uuid: UUID) {
        withContext(NonCancellable) {
            profileLock.withLock {
                if (ImportedDao().exists(uuid)) {
                    val store = ServiceStore(context)

                    store.activeProfile = uuid

                    context.sendProfileChanged(uuid)
                }
            }
        }
    }

    data class ProfileHeaders(
        val supportUrl: String = "",
        val profileWebPageUrl: String = "",
        val profileTitle: String = "",
        val profileLogo: String = "",
        val profileUpdateInterval: Int = 0,
        val announce: String = "",
    )

    fun saveProfileHeaders(profileDir: File, headers: okhttp3.Headers) {
        try {
            val json = JSONObject()
            headers["support-url"]?.let { if (it.isNotBlank()) json.put("support_url", it) }
            headers["profile-web-page-url"]?.let { if (it.isNotBlank()) json.put("profile_web_page_url", it) }
            headers["profile-title"]?.let { if (it.isNotBlank()) json.put("profile_title", decodeHeaderValue(it)) }
            headers["profile-logo"]?.let { if (it.isNotBlank()) json.put("profile_logo", it) }
            headers["profile-update-interval"]?.let {
                val hours = it.trim().toIntOrNull()
                if (hours != null && hours > 0) json.put("profile_update_interval", hours)
            }
            headers["announce"]?.let { if (it.isNotBlank()) json.put("announce", decodeHeaderValue(it)) }
            profileDir.resolve("profile_links.json").writeText(json.toString())
        } catch (_: Exception) {}
    }

    fun readProfileHeaders(profileDir: File): ProfileHeaders {
        return try {
            val file = profileDir.resolve("profile_links.json")
            if (file.exists()) {
                val json = JSONObject(file.readText())
                ProfileHeaders(
                    supportUrl = json.optString("support_url", ""),
                    profileWebPageUrl = json.optString("profile_web_page_url", ""),
                    profileTitle = json.optString("profile_title", ""),
                    profileLogo = json.optString("profile_logo", ""),
                    profileUpdateInterval = json.optInt("profile_update_interval", 0),
                    announce = json.optString("announce", ""),
                )
            } else ProfileHeaders()
        } catch (_: Exception) { ProfileHeaders() }
    }

    data class UrlHeaders(
        val title: String = "",
        val updateIntervalHours: Int = 0,
    )

    suspend fun fetchUrlHeaders(context: Context, url: String): UrlHeaders {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                val baseRequest = buildProfileRequest(context, url)

                fun parse(response: okhttp3.Response): UrlHeaders {
                    if (!response.isSuccessful) return UrlHeaders()
                    val title = response.headers["profile-title"]?.let { decodeHeaderValue(it) } ?: ""
                    val interval = response.headers["profile-update-interval"]?.trim()?.toIntOrNull() ?: 0
                    return UrlHeaders(title, interval)
                }

                // Some servers don't support HEAD correctly. Fallback to lightweight GET.
                val headRequest = baseRequest.newBuilder().head().build()
                client.newCall(headRequest).execute().use { response ->
                    val headers = parse(response)
                    if (headers.title.isNotEmpty() || headers.updateIntervalHours > 0) {
                        return@withContext headers
                    }
                }

                val getRequest = baseRequest.newBuilder()
                    .header("Range", "bytes=0-0")
                    .get()
                    .build()
                client.newCall(getRequest).execute().use { response ->
                    parse(response)
                }
            } catch (_: Exception) {
                UrlHeaders()
            }
        }
    }

    fun decodeHeaderValue(value: String): String {
        return if (value.startsWith("base64:")) {
            try {
                val encoded = value.removePrefix("base64:")
                String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT))
            } catch (_: Exception) { value }
        } else value
    }

    private fun Pending.enforceFieldValid() {
        val scheme = Uri.parse(source)?.scheme?.lowercase(Locale.getDefault())

        when {
            name.isBlank() ->
                throw IllegalArgumentException("Empty name")

            source.isEmpty() && type != Profile.Type.File ->
                throw IllegalArgumentException("Invalid url")

            source.isNotEmpty() && scheme != "https" && scheme != "http" && scheme != "content" ->
                throw IllegalArgumentException("Unsupported url $source")

            interval != 0L && TimeUnit.MILLISECONDS.toMinutes(interval) < 15 ->
                throw IllegalArgumentException("Invalid interval")
        }
    }
}