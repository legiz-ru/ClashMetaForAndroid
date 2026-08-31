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
import com.github.kr328.clash.service.subscription.SubscriptionAlerts
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.TimeUnit

object ProfileProcessor {
    class HwidNotSupportedException(val supportUrl: String = "") : IOException("HWID_NOT_SUPPORTED")
    class HwidMaxDevicesReachedException(val supportUrl: String = "") : IOException("HWID_MAX_DEVICES_REACHED")

    /** Thrown when a fetched config is age-encrypted but the profile has no age-secret-key set. */
    class AgeKeyRequiredException : IOException("AGE_KEY_REQUIRED")

    /**
     * A subscription fetch failed for a distinguishable reason.
     *
     * [message] is a short code, not prose — same convention as
     * [HwidNotSupportedException]/[AgeKeyRequiredException]: `sendProfileUpdateFailed`
     * carries only a plain `String` across the service boundary, so the type
     * information these classes give directly is only available to a caller
     * standing right next to the throw (e.g. PropertiesActivity's own update
     * button); everyone else has to recover the reason from the message text,
     * hence a fixed code rather than a sentence. [detail] is the underlying
     * exception's own message, kept separate so callers can show it as a
     * secondary, technical line without it drowning out the plain-language one.
     */
    open class FetchFailedException(message: String, val detail: String? = null) : IOException(message)

    /** No active network at all — checked before the request is even attempted. */
    class FetchNoConnectivityException : FetchFailedException("FETCH_NO_CONNECTIVITY")

    /** DNS didn't resolve, or the connection was refused/unreachable. */
    class FetchHostUnreachableException(detail: String?) : FetchFailedException("FETCH_HOST_UNREACHABLE", detail)

    /** Connect or read timed out. */
    class FetchTimeoutException(detail: String?) : FetchFailedException("FETCH_TIMEOUT", detail)

    /** TLS handshake or certificate validation failed. */
    class FetchTlsErrorException(detail: String?) : FetchFailedException("FETCH_TLS_ERROR", detail)

    /** The server answered, but not with 2xx. [code] is folded into the message
     *  (`FETCH_HTTP_ERROR:404`) since it has to survive the same string-only
     *  boundary as the rest of this hierarchy. */
    class FetchHttpErrorException(val code: Int) : FetchFailedException("FETCH_HTTP_ERROR:$code")

    /** Catch-all for a failure that doesn't fit any of the above. */
    class FetchUnknownException(detail: String?) : FetchFailedException("FETCH_UNKNOWN", detail)

    /**
     * Whether the device currently has a network with general internet access.
     *
     * Checked before a subscription fetch so "you're offline" can be reported
     * as itself, distinct from what a request over a dead connection would
     * otherwise surface (a timeout, or a DNS failure once the OS falls back to
     * a cached/no resolver) — both technically true, neither as useful to read
     * as "no connection" would be.
     *
     * Deliberately not conditioned on `NET_CAPABILITY_VALIDATED`: that flag
     * changes on its own timeline (see NetworkObserveModule) and a network that
     * simply hasn't been re-validated yet is not the same claim as "offline" —
     * a request over it should be allowed to run and fail on its own terms.
     */
    private fun hasActiveConnectivity(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            ?: return true // Fail open: don't block a fetch on our own uncertainty about the API.
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Turns the coded [reason] string carried by a failed-update broadcast
     * (`FETCH_*`, `HWID_*`, `AGE_KEY_REQUIRED`, or an arbitrary raw message
     * from before these codes existed) into a short, human-readable phrase
     * for the "Update failed" notification. Unknown codes and plain messages
     * pass through unchanged.
     */
    fun describeFetchFailureReason(context: Context, reason: String?): String {
        val r = reason.orEmpty()
        if (r == "FETCH_NO_CONNECTIVITY") return context.getString(R.string.fetch_no_connectivity_short)
        if (r == "HWID_NOT_SUPPORTED") return context.getString(R.string.hwid_not_supported_short)
        if (r == "HWID_MAX_DEVICES_REACHED") return context.getString(R.string.hwid_max_devices_short)
        if (r == "AGE_KEY_REQUIRED") return context.getString(R.string.age_key_required_short)
        if (r.startsWith("FETCH_HOST_UNREACHABLE")) return context.getString(R.string.fetch_host_unreachable_short)
        if (r.startsWith("FETCH_TIMEOUT")) return context.getString(R.string.fetch_timeout_short)
        if (r.startsWith("FETCH_TLS_ERROR")) return context.getString(R.string.fetch_tls_error_short)
        if (r.startsWith("FETCH_HTTP_ERROR")) {
            val code = r.substringAfter(":", "").toIntOrNull() ?: 0
            return context.getString(R.string.fetch_http_error_short, code)
        }
        if (r.startsWith("FETCH_UNKNOWN")) return context.getString(R.string.fetch_unknown_short)
        return r
    }

    /** Maps an exception caught around an OkHttp call to the typed reason it represents. */
    private fun classifyFetchException(e: Exception): FetchFailedException {
        return when (e) {
            is FetchFailedException -> e
            is java.net.UnknownHostException, is java.net.ConnectException ->
                FetchHostUnreachableException(e.message)
            is java.net.SocketTimeoutException -> FetchTimeoutException(e.message)
            is javax.net.ssl.SSLException -> FetchTlsErrorException(e.message)
            else -> FetchUnknownException(e.message)
        }
    }

    /**
     * Detects whether [content] is an age-encrypted payload (ASCII-armored or binary header).
     * Such configs can only be parsed by the core when a matching age-secret-key is present
     * (written to age-secret-key.txt alongside config.yaml).
     */
    fun isAgeEncrypted(content: String): Boolean {
        val trimmed = content.trimStart()
        return trimmed.startsWith("-----BEGIN AGE ENCRYPTED FILE-----") ||
            trimmed.startsWith("age-encryption.org/v1")
    }

    private fun isHeaderTrue(headers: okhttp3.Headers, name: String): Boolean {
        return headers[name]?.trim()?.equals("true", ignoreCase = true) == true
    }

    // RFC 1123 ("EEE, dd MMM yyyy HH:mm:ss zzz") is the format the standard
    // `Date` response header is sent in. Parsed by hand instead of via
    // okhttp3.Headers.date(name) so this doesn't depend on that Kotlin-only
    // API resolving correctly across OkHttp/Kotlin toolchain combinations;
    // java.time is avoided since it needs desugaring below API 26.
    private val httpDateFormat = ThreadLocal.withInitial {
        java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("GMT")
        }
    }

    private fun parseHttpDateMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            httpDateFormat.get()!!.parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun throwIfHwidBlocked(headers: okhttp3.Headers) {
        if (isHeaderTrue(headers, "x-hwid-not-supported")) {
            throw HwidNotSupportedException(headers["support-url"]?.trim() ?: "")
        }

        if (isHeaderTrue(headers, "x-hwid-max-devices-reached")) {
            throw HwidMaxDevicesReachedException(headers["support-url"]?.trim() ?: "")
        }
    }

    fun buildProfileRequest(context: Context, url: String): Request {
        val uiPrefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val sendHwid = uiPrefs.getBoolean("send_hwid", true)
        val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName

        val builder = Request.Builder().url(url)
        builder.header("User-Agent", "Clash-Meta/Prizrak-Box (Android Build $versionName)")

        if (sendHwid) {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            builder.header("x-hwid", deviceId)
            builder.header("x-device-os", "Android")
            builder.header("x-devices-os", "Android")
            builder.header("x-ver-os", Build.VERSION.RELEASE ?: "unknown")
            builder.header("x-device-model", Build.MODEL ?: "unknown")
        }

        return builder.build()
    }

    private val profileLock = Mutex()
    private val processLock = Mutex()

    // -------------------------------------------------------------------------
    // Internal fetch result types
    // -------------------------------------------------------------------------

    private data class PrefetchResult(
        val headers: okhttp3.Headers? = null,
    )

    /** Result of fetching a Converted profile source URL, including pxa header values. */
    private data class FetchedSource(
        val content: String,
        val pxaTemplateUrl: String? = null,
        val allowTemplateSelection: Boolean = true,
        /** Value of pxa-template-scheme header, e.g. "proxy-providers". Null = normal mode. */
        val pxaTemplateScheme: String? = null,
        /** True when the source was an HTTP URL and headers were actually read. */
        val headersAvailable: Boolean = false,
        /** Raw HTTP response headers; null for non-HTTP sources. */
        val rawHeaders: okhttp3.Headers? = null,
    )

    // -------------------------------------------------------------------------
    // Fetch helpers
    // -------------------------------------------------------------------------

    /** Caps subscription/config downloads so a misbehaving server can't exhaust storage or memory. */
    private const val MAX_PROFILE_RESPONSE_BYTES = 32L shl 20 // 32 MiB

    /**
     * Copies [input] to [output] like [java.io.InputStream.copyTo], but aborts once more than
     * [limit] bytes have been read — protects against an unbounded or lying Content-Length.
     */
    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw FetchUnknownException("Response larger than $limit bytes")
            output.write(buffer, 0, read)
        }
    }

    /**
     * Reads [body] fully into a String like [okhttp3.ResponseBody.string], but aborts once more
     * than [limit] bytes have been read.
     */
    private fun stringLimited(body: okhttp3.ResponseBody, limit: Long): String {
        val out = java.io.ByteArrayOutputStream()
        body.byteStream().use { input -> copyLimited(input, out, limit) }
        return out.toString(body.contentType()?.charset(Charsets.UTF_8)?.name() ?: "UTF-8")
    }

    private fun prefetchProfileConfig(context: Context, source: String, targetConfigFile: File): PrefetchResult {
        if (!hasActiveConnectivity(context)) {
            throw FetchNoConnectivityException()
        }

        try {
            val request = buildProfileRequest(context, source)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                throwIfHwidBlocked(response.headers)

                if (!response.isSuccessful) throw FetchHttpErrorException(response.code)

                val body = response.body ?: throw FetchUnknownException("Empty response body")
                targetConfigFile.parentFile?.mkdirs()
                targetConfigFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        copyLimited(input, output, MAX_PROFILE_RESPONSE_BYTES)
                    }
                }
                return PrefetchResult(response.headers)
            }
        } catch (e: HwidNotSupportedException) {
            throw e
        } catch (e: HwidMaxDevicesReachedException) {
            throw e
        } catch (e: FetchFailedException) {
            throw e
        } catch (e: Exception) {
            throw classifyFetchException(e)
        }
    }

    /**
     * Fetches a Converted profile source URL and extracts pxa-template /
     * pxa-change-template headers. For non-HTTP sources (direct proxy text)
     * the content is returned as-is with no headers.
     */
    private fun fetchSourceContentWithPxa(context: Context, source: String): FetchedSource {
        if (!source.startsWith("http://", ignoreCase = true) &&
            !source.startsWith("https://", ignoreCase = true)
        ) {
            return FetchedSource(source) // Direct proxy-link text, no headers
        }

        if (!hasActiveConnectivity(context)) {
            throw FetchNoConnectivityException()
        }

        try {
            val request = buildProfileRequest(context, source)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                throwIfHwidBlocked(response.headers)

                if (!response.isSuccessful) {
                    throw FetchHttpErrorException(response.code)
                }
                val responseBody = response.body ?: throw FetchUnknownException("Empty response body")
                val body = stringLimited(responseBody, MAX_PROFILE_RESPONSE_BYTES)
                val pxaTemplateUrl = response.headers["pxa-template"]?.trim()?.ifBlank { null }
                val pxaTemplateScheme = response.headers["pxa-template-scheme"]?.trim()?.ifBlank { null }
                // Template selection is allowed unless the server locks it via pxa-template.
                val allowTemplateSelection = pxaTemplateUrl == null && pxaTemplateScheme == null
                return FetchedSource(body, pxaTemplateUrl, allowTemplateSelection, pxaTemplateScheme, headersAvailable = true, rawHeaders = response.headers)
            }
        } catch (e: HwidNotSupportedException) {
            throw e
        } catch (e: HwidMaxDevicesReachedException) {
            throw e
        } catch (e: FetchFailedException) {
            throw e
        } catch (e: Exception) {
            throw classifyFetchException(e)
        }
    }

    // -------------------------------------------------------------------------
    // Subscription URL management (new-url / new-domain / fallback-url / fallback-domain)
    // -------------------------------------------------------------------------

    private fun isHttpUrl(s: String) =
        s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

    /** Replaces only the host of [url] with [newHost], keeping scheme/port/path/query/fragment. */
    private fun swapHost(url: String, newHost: String): String? = try {
        val u = java.net.URI(url)
        val sb = StringBuilder()
        sb.append(u.scheme).append("://")
        if (u.userInfo != null) sb.append(u.userInfo).append("@")
        sb.append(newHost)
        if (u.port != -1) sb.append(":").append(u.port)
        sb.append(u.rawPath ?: "")
        if (u.rawQuery != null) sb.append("?").append(u.rawQuery)
        if (u.rawFragment != null) sb.append("#").append(u.rawFragment)
        sb.toString()
    } catch (_: Exception) {
        null
    }

    /** new-url (full, takes precedence) or new-domain (host-swap) → migrated source, or null. */
    private fun computeMigratedSource(source: String, headers: okhttp3.Headers): String? {
        val newUrl = headers["new-url"]?.trim().orEmpty()
        if (newUrl.isNotBlank() && isHttpUrl(newUrl)) return newUrl
        val newDomain = headers["new-domain"]?.trim().orEmpty()
        if (newDomain.isNotBlank()) {
            swapHost(source, newDomain)?.let { if (it != source) return it }
        }
        return null
    }

    /** Ordered endpoints to try: primary source, then fallback-url, then fallback-domain host-swap. */
    private fun fallbackCandidates(source: String, fallbackUrl: String, fallbackDomain: String): List<String> {
        val list = ArrayList<String>()
        list.add(source)
        if (fallbackUrl.isNotBlank() && isHttpUrl(fallbackUrl)) list.add(fallbackUrl)
        if (fallbackDomain.isNotBlank()) swapHost(source, fallbackDomain)?.let { list.add(it) }
        return list.distinct()
    }

    private data class ProbeResult(val url: String, val headers: okhttp3.Headers)

    /**
     * Returns the first [candidates] endpoint that answers 2xx within 9s, with its
     * response headers. Used to read the management headers and pick a live endpoint.
     */
    private fun probeEndpoint(context: Context, candidates: List<String>): ProbeResult? {
        val client = OkHttpClient.Builder()
            .callTimeout(9, TimeUnit.SECONDS)
            .build()
        for (url in candidates) {
            try {
                client.newCall(buildProfileRequest(context, url)).execute().use { resp ->
                    if (resp.isSuccessful) return ProbeResult(url, resp.headers)
                }
            } catch (_: Exception) {
                // try next candidate
            }
        }
        return null
    }

    /**
     * Resolves the effective subscription endpoint for a profile:
     *  - applies new-url / new-domain migration (persisting the new source via
     *    [persistMigratedSource], up to 3 hops),
     *  - picks a live endpoint via fallback-url / fallback-domain when the primary is down.
     *
     * Entity-agnostic so both the import path (Pending) and the refresh path
     * (Imported) can reuse it — the caller supplies how to persist a migrated source.
     *
     * Best-effort: on any error the original [source] is returned with no override URL.
     * Returns the (possibly migrated) canonical source and the URL to actually download
     * from (null = use the canonical source as before; this may be a temporary
     * fallback mirror that must NOT be persisted as the profile's source).
     */
    private suspend fun resolveSubscriptionEndpoint(
        context: Context,
        uuid: UUID,
        type: Profile.Type,
        source: String,
        persistMigratedSource: suspend (String) -> Unit,
    ): Pair<String, String?> {
        if (type == Profile.Type.File || !isHttpUrl(source)) {
            return source to null
        }
        return try {
            var current = source
            val importedDir = context.importedDir.resolve(uuid.toString())
            val stored = readProfileHeaders(importedDir)
            var fbUrl = stored.fallbackUrl
            var fbDomain = stored.fallbackDomain
            var downloadUrl: String? = null
            var migrations = 0
            while (migrations < 3) {
                val probe = probeEndpoint(context, fallbackCandidates(current, fbUrl, fbDomain))
                    ?: break
                fbUrl = probe.headers["fallback-url"]?.trim().orEmpty()
                fbDomain = probe.headers["fallback-domain"]?.trim().orEmpty()
                val migrated = computeMigratedSource(current, probe.headers)
                if (migrated != null && migrated != current) {
                    current = migrated
                    persistMigratedSource(migrated)
                    migrations++
                    continue
                }
                downloadUrl = probe.url
                break
            }
            current to downloadUrl
        } catch (_: Exception) {
            source to null
        }
    }

    /**
     * Parses the `subscription-userinfo` header and returns [upload, download, total, expire] in bytes/ms.
     */
    private fun parseSubscriptionUserInfo(headers: okhttp3.Headers?): LongArray {
        val out = LongArray(4) { 0L }
        val userinfo = headers?.get("subscription-userinfo") ?: return out
        try {
            userinfo.split(";").forEach { flag ->
                val parts = flag.trim().split("=")
                if (parts.size < 2 || parts[1].isEmpty()) return@forEach
                val v = parts[1].trim()
                when {
                    parts[0].contains("upload")   -> out[0] = BigDecimal(v.split('.').first()).longValueExact()
                    parts[0].contains("download") -> out[1] = BigDecimal(v.split('.').first()).longValueExact()
                    parts[0].contains("total")    -> out[2] = BigDecimal(v.split('.').first()).longValueExact()
                    parts[0].contains("expire")   -> out[3] = (v.toDouble() * 1000).toLong()
                }
            }
        } catch (_: Exception) {}
        return out
    }

    /**
     * Downloads a YAML template from [url]. Returns null on any error so the
     * caller can fall back to the user-selected built-in template.
     */
    private fun fetchTemplateFromUrl(url: String): String? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }

    // -------------------------------------------------------------------------
    // proxy-providers template substitution
    // -------------------------------------------------------------------------

    /**
     * Performs placeholder substitution in [templateContent] for proxy-providers mode.
     *
     * Placeholders:
     *  $subscription_url$          → profile source URL
     *  $User-Agent$                → app User-Agent string
     *  $profile-update-interval$   → profile-update-interval header value × 3600 (hours→seconds), or 0
     *  $x-hwid$                    → HWID if enabled, else removed
     *  $x-device-os$               → "Android" if HWID enabled, else removed
     *  $x-ver-os$                  → Android version if HWID enabled, else removed
     *  $x-device-model$            → device model if HWID enabled, else removed
     */
    private fun applyProxyProviderTemplate(
        context: Context,
        templateContent: String,
        sourceUrl: String,
        profileUpdateIntervalHours: Int,
    ): String {
        val versionName = context.packageManager
            .getPackageInfo(context.packageName, 0).versionName
        val userAgent = "Clash-Meta/Prizrak-Box (Android Build $versionName)"
        val uiPrefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
        val sendHwid = uiPrefs.getBoolean("send_hwid", true)

        val intervalSeconds = (profileUpdateIntervalHours * 3600).coerceAtLeast(0)

        var result = templateContent
        result = result.replace("\$subscription_url\$", sourceUrl)
        result = result.replace("\$User-Agent\$", userAgent)
        result = result.replace("\$profile-update-interval\$", intervalSeconds.toString())

        if (sendHwid) {
            val deviceId = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            result = result.replace("\$x-hwid\$", deviceId)
            result = result.replace("\$x-device-os\$", "Android")
            result = result.replace("\$x-ver-os\$", Build.VERSION.RELEASE ?: "unknown")
            result = result.replace("\$x-device-model\$", Build.MODEL ?: "unknown")
        } else {
            result = result.replace("\$x-hwid\$", "")
            result = result.replace("\$x-device-os\$", "")
            result = result.replace("\$x-ver-os\$", "")
            result = result.replace("\$x-device-model\$", "")
        }

        // Clean up $payload$ if it wasn't substituted (not applicable in proxy-providers mode).
        result = result.replace("\$payload\$", "")

        return result
    }

    /**
     * Downloads the pxa-template, applies placeholder substitution, and writes the
     * result to [processingDir]/config.yaml. Used instead of [convertAndWriteConfig]
     * when pxa-template-scheme == "proxy-providers".
     */
    private fun writeProxyProviderConfig(
        context: Context,
        sourceUrl: String,
        profileUpdateIntervalHours: Int,
        pendingDir: File,
        processingDir: File,
    ) {
        val pxaTemplateUrl = TemplateManager.getPxaTemplateUrl(pendingDir)
            ?: throw IOException("proxy-providers mode requires a pxa-template URL")
        val template = fetchTemplateFromUrl(pxaTemplateUrl)
            ?: throw IOException("Failed to download pxa-template for proxy-providers mode")
        val result = applyProxyProviderTemplate(context, template, sourceUrl, profileUpdateIntervalHours)
        processingDir.mkdirs()
        processingDir.resolve("config.yaml").writeText(result, Charsets.UTF_8)
        // Carry template meta forward so it survives processingDir→importedDir copy.
        TemplateManager.saveSelectedTemplateId(processingDir, TemplateManager.getSelectedTemplateId(pendingDir))
        TemplateManager.savePxaMeta(
            processingDir,
            pxaTemplateUrl,
            TemplateManager.isTemplateSelectionAllowed(pendingDir),
            TemplateManager.getPxaTemplateScheme(pendingDir),
        )
    }

    // -------------------------------------------------------------------------
    // payload template helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the proxy items from the `proxies:` section of a Clash YAML config.
     * Returns the list block text (lines starting with `- `) without the `proxies:` header,
     * each item at column 0 indentation. Returns empty string if the section is absent.
     */
    private fun extractProxiesFromYaml(configYaml: String): String {
        val lines = configYaml.lines()
        val startIdx = lines.indexOfFirst { it == "proxies:" || it.startsWith("proxies:") }
        if (startIdx < 0) return ""
        val result = mutableListOf<String>()
        var i = startIdx + 1
        while (i < lines.size) {
            val line = lines[i]
            // Stop at the next non-indented, non-list key (top-level YAML key).
            if (line.isNotEmpty() && !line[0].isWhitespace() && !line.startsWith('-')) break
            result.add(line)
            i++
        }
        return result.dropLastWhile { it.isBlank() }.joinToString("\n")
    }

    /**
     * Substitutes the `$payload$` placeholder in [templateContent] with [proxiesYaml].
     *
     * The placeholder must appear as the sole content on its line (possibly with leading
     * whitespace for indentation). Each line of [proxiesYaml] is prefixed with that same
     * indentation so the resulting YAML stays properly indented.
     *
     * Example template line (6 spaces indent):
     *   `      $payload$`
     * After substitution the 6-space prefix is added to every proxy item line.
     */
    private fun applyPayloadTemplate(templateContent: String, proxiesYaml: String): String {
        val lines = templateContent.lines()
        val sb = StringBuilder()
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trim()
            if (trimmed == "\$payload\$") {
                val indent = line.takeWhile { it == ' ' }
                val proxiesLines = proxiesYaml.lines()
                proxiesLines.forEachIndexed { pi, proxyLine ->
                    if (pi > 0) sb.append('\n')
                    sb.append(if (proxyLine.isBlank()) proxyLine else indent + proxyLine)
                }
            } else if (line.contains("\$payload\$")) {
                // Inline occurrence — replace without re-indenting.
                sb.append(line.replace("\$payload\$", proxiesYaml))
            } else {
                sb.append(line)
            }
            if (idx < lines.size - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * Implements `pxa-template-scheme: payload` processing:
     *  1. Converts [content] via convert.go using the built-in Default template
     *     to obtain the Clash-format proxy list.
     *  2. Extracts the `proxies:` block from the converted YAML.
     *  3. Downloads the pxa-template from the URL stored in [pendingDir] metadata.
     *  4. Substitutes `$payload$` in the template with the proxy list.
     *  5. Substitutes all standard placeholders ($profile-update-interval$, $subscription_url$, etc.).
     *  6. Writes the final YAML to [processingDir]/config.yaml.
     *
     * Throws [IOException] on any failure.
     */
    private fun writePayloadConfig(
        context: Context,
        content: String,
        sourceUrl: String,
        profileUpdateIntervalHours: Int,
        pendingDir: File,
        processingDir: File,
    ) {
        val pxaTemplateUrl = TemplateManager.getPxaTemplateUrl(pendingDir)
            ?: throw IOException("payload mode requires a pxa-template URL")

        // Use Default template for the intermediate conversion; we only need proxies: from it.
        val conversionTemplate = TemplateManager.loadTemplate(context, TemplateManager.Template.Default.id)
        val resultJson = Clash.convertAndApplyTemplate(content, conversionTemplate)
        val result = JSONObject(resultJson)
        val error = result.optString("error", "")
        if (error.isNotEmpty()) throw IOException("Conversion failed: $error")
        val configYaml = result.optString("yaml", "")
        if (configYaml.isEmpty()) throw IOException("Conversion produced empty config")

        val proxiesYaml = extractProxiesFromYaml(configYaml)
        if (proxiesYaml.isEmpty()) throw IOException("No proxies found in converted config for payload mode")

        val payloadTemplate = fetchTemplateFromUrl(pxaTemplateUrl)
            ?: throw IOException("Failed to download pxa-template for payload mode")

        // First substitute $payload$, then run standard placeholder substitutions so that
        // $profile-update-interval$, $subscription_url$, $x-hwid$, etc. are also replaced.
        val afterPayload = applyPayloadTemplate(payloadTemplate, proxiesYaml)
        val finalConfig = applyProxyProviderTemplate(context, afterPayload, sourceUrl, profileUpdateIntervalHours)

        processingDir.mkdirs()
        processingDir.resolve("config.yaml").writeText(finalConfig, Charsets.UTF_8)
        // Carry template meta forward so it survives processingDir→importedDir copy.
        TemplateManager.saveSelectedTemplateId(processingDir, TemplateManager.getSelectedTemplateId(pendingDir))
        TemplateManager.savePxaMeta(
            processingDir,
            pxaTemplateUrl,
            TemplateManager.isTemplateSelectionAllowed(pendingDir),
            TemplateManager.getPxaTemplateScheme(pendingDir),
        )
    }

    // -------------------------------------------------------------------------
    // Proxy-link / SingBox conversion helpers
    // -------------------------------------------------------------------------

    /** Broad classification of fetched profile content. */
    private enum class ContentFormat { ClashYaml, ConvertibleContent }

    /**
     * Returns [ContentFormat.ConvertibleContent] when the content looks like:
     *  - proxy links (vless://, trojan://, vmess://, ss://, hy2://, etc.)
     *  - a SingBox JSON object (starts with `{`)
     *  - a bare base64-encoded proxy-link list (single long opaque line)
     *
     * Falls back to [ContentFormat.ClashYaml] for everything else.
     */
    private fun detectContentFormat(content: String): ContentFormat {
        val trimmed = content.trimStart()
        val proxySchemes = listOf(
            "vless://", "trojan://", "vmess://", "ss://", "ssr://",
            "hysteria://", "hysteria2://", "hy2://", "tuic://", "anytls://", "wireguard://"
        )
        if (proxySchemes.any { trimmed.startsWith(it, ignoreCase = true) }) {
            return ContentFormat.ConvertibleContent
        }
        val firstLine = trimmed.lineSequence().firstOrNull()?.trim() ?: ""
        if (proxySchemes.any { firstLine.startsWith(it, ignoreCase = true) }) {
            return ContentFormat.ConvertibleContent
        }
        if (trimmed.startsWith("{")) return ContentFormat.ConvertibleContent // SingBox JSON
        // Single long base64-looking line (encoded proxy list)
        if (!trimmed.contains('\n') && trimmed.length > 80 &&
            trimmed.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        ) {
            return ContentFormat.ConvertibleContent
        }
        return ContentFormat.ClashYaml
    }

    /**
     * Converts [content] using the template configured for [pendingDir], writes
     * the resulting Clash YAML to [processingDir]/config.yaml, and copies the
     * full template metadata (selected template id + pxa values) to [processingDir].
     *
     * If a pxa-template URL is stored in [pendingDir]'s metadata, that URL is
     * fetched and used as the template instead of the user-selected built-in
     * template (with fallback to the built-in on network error).
     *
     * Throws [IOException] if conversion fails.
     */
    private fun convertAndWriteConfig(
        context: Context,
        content: String,
        pendingDir: File,
        processingDir: File,
    ) {
        val templateId = TemplateManager.getSelectedTemplateId(pendingDir)
        val pxaTemplateUrl = TemplateManager.getPxaTemplateUrl(pendingDir)
        val allowTemplateSelection = TemplateManager.isTemplateSelectionAllowed(pendingDir)

        // Template priority:
        //  1. allowTemplateSelection=false → pxa-template is mandatory, always use it.
        //  2. User explicitly chose "Шаблон из подписки" (pxa_subscription id) → use pxa-template URL.
        //  3. Otherwise → use the user-selected built-in / custom template.
        val usePxaTemplate = !pxaTemplateUrl.isNullOrBlank() &&
            (!allowTemplateSelection || templateId == TemplateManager.PXA_SUBSCRIPTION_TEMPLATE_ID)

        val templateContent = if (usePxaTemplate) {
            fetchTemplateFromUrl(pxaTemplateUrl!!)
                ?: TemplateManager.loadTemplate(context, TemplateManager.Template.Default.id)
        } else {
            TemplateManager.loadTemplate(context, templateId)
        }

        val resultJson = Clash.convertAndApplyTemplate(content, templateContent)
        val result = JSONObject(resultJson)
        val error = result.optString("error", "")
        if (error.isNotEmpty()) throw IOException("Conversion failed: $error")
        val configYaml = result.optString("yaml", "")
        if (configYaml.isEmpty()) throw IOException("Conversion produced an empty config")

        processingDir.mkdirs()
        processingDir.resolve("config.yaml").writeText(configYaml, Charsets.UTF_8)

        // Copy full template meta (templateId + pxa values) to processingDir.
        TemplateManager.saveSelectedTemplateId(processingDir, templateId)
        TemplateManager.savePxaMeta(
            processingDir,
            pxaTemplateUrl,
            allowTemplateSelection,
            TemplateManager.getPxaTemplateScheme(pendingDir),
        )
    }

    // -------------------------------------------------------------------------

    private data class FetchTarget(
        val source: String,
        val force: Boolean,
    )

    private fun resolveFetchTarget(
        context: Context,
        type: Profile.Type,
        source: String,
        alreadyPrefetched: Boolean = false,
        downloadUrlOverride: String? = null,
    ): FetchTarget {
        val isHttpUrl = source.startsWith("https://", true) || source.startsWith("http://", true)

        if (type == Profile.Type.Url && isHttpUrl) {
            if (!alreadyPrefetched) {
                val localConfig = context.processingDir.resolve("config.yaml")
                prefetchProfileConfig(context, downloadUrlOverride ?: source, localConfig)
            }
            return FetchTarget(context.processingDir.resolve("config.yaml").toURI().toString(), false)
        }

        // For Converted profiles the config.yaml is pre-written; just validate in place.
        if (type == Profile.Type.Converted) {
            return FetchTarget(
                context.processingDir.resolve("config.yaml").toURI().toString(),
                false
            )
        }

        return FetchTarget(source, type != Profile.Type.File)
    }

    suspend fun apply(context: Context, uuid: UUID, callback: IFetchObserver? = null) {
        withContext(NonCancellable) {
            processLock.withLock {
                var snapshot = profileLock.withLock {
                    val pending = PendingDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    pending.enforceFieldValid()

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.pendingDir.resolve(pending.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    pending
                }

                // Subscription URL management: apply new-url / new-domain migration
                // (persisting the new source onto the pending row) and pick a live
                // endpoint via fallback-url / fallback-domain. Best-effort — on any
                // error this is a no-op and the original source is used as before.
                val (migratedSource, downloadUrl) = resolveSubscriptionEndpoint(
                    context, snapshot.uuid, snapshot.type, snapshot.source
                ) { migrated -> PendingDao().update(snapshot.copy(source = migrated)) }
                snapshot = snapshot.copy(source = migratedSource)

                // Accumulates HTTP response headers for profiles that end up being converted,
                // so we can save profile metadata (title, logo, subscription info, etc.) later.
                var convertedHeaders: okhttp3.Headers? = null

                // Converted profiles: fetch source (with pxa headers), update meta, convert.
                if (snapshot.type == Profile.Type.Converted) {
                    val pendingDir = context.pendingDir.resolve(snapshot.uuid.toString())
                    val fetchResult = fetchSourceContentWithPxa(context, downloadUrl ?: snapshot.source)
                    // Encrypted source without a key cannot be converted — surface a clear warning.
                    if (isAgeEncrypted(fetchResult.content) && snapshot.ageSecretKey.isEmpty()) {
                        throw AgeKeyRequiredException()
                    }
                    if (fetchResult.headersAvailable) {
                        TemplateManager.savePxaMeta(
                            pendingDir,
                            fetchResult.pxaTemplateUrl,
                            fetchResult.allowTemplateSelection,
                            fetchResult.pxaTemplateScheme,
                        )
                        if (!fetchResult.pxaTemplateUrl.isNullOrBlank()) {
                            TemplateManager.saveSelectedTemplateId(
                                pendingDir, TemplateManager.PXA_SUBSCRIPTION_TEMPLATE_ID
                            )
                        }
                        convertedHeaders = fetchResult.rawHeaders
                    }
                    // Route to the appropriate conversion mode based on pxa-template-scheme.
                    // pxa-template-scheme is the authoritative routing key; pxa-change-template
                    // only controls whether the user may manually switch templates, it does NOT
                    // disable proxy-providers / payload processing modes.
                    val scheme = fetchResult.pxaTemplateScheme
                    val hasPxaTemplate = !fetchResult.pxaTemplateUrl.isNullOrBlank()
                    when {
                        hasPxaTemplate && scheme == "proxy-providers" -> {
                            val intervalHours = fetchResult.rawHeaders
                                ?.get("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                            writeProxyProviderConfig(context, snapshot.source, intervalHours, pendingDir, context.processingDir)
                        }
                        hasPxaTemplate && scheme == "payload" -> {
                            val intervalHours = fetchResult.rawHeaders
                                ?.get("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                            writePayloadConfig(context, fetchResult.content, snapshot.source, intervalHours, pendingDir, context.processingDir)
                        }
                        else -> convertAndWriteConfig(context, fetchResult.content, pendingDir, context.processingDir)
                    }
                }

                var effectiveType = snapshot.type
                var alreadyPrefetched = false

                // For Url+HTTP profiles: prefetch content and check for convertible format.
                if (snapshot.type == Profile.Type.Url &&
                    (snapshot.source.startsWith("http://", ignoreCase = true) ||
                     snapshot.source.startsWith("https://", ignoreCase = true))
                ) {
                    val localConfig = context.processingDir.resolve("config.yaml")
                    val prefetchResult = prefetchProfileConfig(context, downloadUrl ?: snapshot.source, localConfig)
                    alreadyPrefetched = true

                    if (localConfig.exists()) {
                        val content = localConfig.readText(Charsets.UTF_8)
                        // Age-encrypted config but no key set: the core can't decrypt it.
                        // Throw a typed error so the import UI can prompt for an age-secret-key.
                        if (isAgeEncrypted(content) && snapshot.ageSecretKey.isEmpty()) {
                            throw AgeKeyRequiredException()
                        }
                        if (detectContentFormat(content) == ContentFormat.ConvertibleContent) {
                            val pendingDir = context.pendingDir.resolve(snapshot.uuid.toString())

                            // Extract and save pxa headers for the auto-converted profile.
                            val hdrs = prefetchResult.headers
                            val pxaTemplateUrl = hdrs?.get("pxa-template")?.trim()?.ifBlank { null }
                            val pxaTemplateScheme = hdrs?.get("pxa-template-scheme")?.trim()?.ifBlank { null }
                            // Template selection is allowed unless the server locks it via pxa-template.
                            val allowTemplateSelection = pxaTemplateUrl == null && pxaTemplateScheme == null
                            TemplateManager.savePxaMeta(pendingDir, pxaTemplateUrl, allowTemplateSelection, pxaTemplateScheme)
                            if (!pxaTemplateUrl.isNullOrBlank()) {
                                TemplateManager.saveSelectedTemplateId(
                                    pendingDir, TemplateManager.PXA_SUBSCRIPTION_TEMPLATE_ID
                                )
                            }

                            val hasPxaTemplateUrl = !pxaTemplateUrl.isNullOrBlank()
                            when {
                                hasPxaTemplateUrl && pxaTemplateScheme == "proxy-providers" -> {
                                    val intervalHours = hdrs?.get("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                                    writeProxyProviderConfig(context, snapshot.source, intervalHours, pendingDir, context.processingDir)
                                }
                                hasPxaTemplateUrl && pxaTemplateScheme == "payload" -> {
                                    val intervalHours = hdrs?.get("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                                    writePayloadConfig(context, content, snapshot.source, intervalHours, pendingDir, context.processingDir)
                                }
                                else -> convertAndWriteConfig(context, content, pendingDir, context.processingDir)
                            }
                            effectiveType = Profile.Type.Converted
                            convertedHeaders = hdrs
                        }
                    }
                }

                // For profiles that converted to Converted type, persist standard profile headers
                // into processingDir so they are carried over to importedDir on copy.
                if (effectiveType == Profile.Type.Converted && convertedHeaders != null) {
                    saveProfileHeaders(context.processingDir, convertedHeaders!!)
                }

                if (snapshot.ageSecretKey.isNotEmpty()) {
                    context.processingDir.resolve("age-secret-key.txt")
                        .writeText(snapshot.ageSecretKey)
                }

                // Install the age secret key into the core so it can decrypt an
                // age-encrypted config during fetchAndValid (the core reads identities
                // only from this global, not from age-secret-key.txt). Empty clears it.
                Clash.setAgeSecretKeys(snapshot.ageSecretKey)

                val fetchTarget = resolveFetchTarget(
                    context, snapshot.type, snapshot.source, alreadyPrefetched
                )
                var cb = callback

                Clash.fetchAndValid(context.processingDir, fetchTarget.source, fetchTarget.force) {
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
                        if (effectiveType == Profile.Type.Converted) {
                            val sub = parseSubscriptionUserInfo(convertedHeaders)
                            val profileTitle = convertedHeaders?.get("profile-title")?.let { decodeHeaderValue(it) } ?: ""
                            val updateIntervalHours = convertedHeaders?.get("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                            val resolvedName = if (profileTitle.isNotEmpty()) profileTitle else snapshot.name
                            val resolvedInterval = if (updateIntervalHours > 0) updateIntervalHours.toLong() * 60 * 60 * 1000 else snapshot.interval
                            val new = Imported(
                                snapshot.uuid,
                                resolvedName,
                                Profile.Type.Converted,
                                snapshot.source,
                                resolvedInterval,
                                sub[0], sub[1], sub[2], sub[3],
                                old?.createdAt ?: System.currentTimeMillis(),
                                ageSecretKey = snapshot.ageSecretKey,
                            )
                            if (old != null) ImportedDao().update(new) else ImportedDao().insert(new)
                            PendingDao().remove(snapshot.uuid)
                            context.pendingDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                            context.sendProfileChanged(snapshot.uuid)
                        } else if (snapshot?.type == Profile.Type.Url) {
                            if (snapshot.source.startsWith("https://", true)) {
                                val client = OkHttpClient()
                                val request = buildProfileRequest(context, downloadUrl ?: snapshot.source)

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
                                old?.createdAt ?: System.currentTimeMillis(),
                                ageSecretKey = snapshot.ageSecretKey,
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
                                old?.createdAt ?: System.currentTimeMillis(),
                                ageSecretKey = snapshot.ageSecretKey,
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
                var snapshot = profileLock.withLock {
                    val imported = ImportedDao().queryByUUID(uuid)
                        ?: throw IllegalArgumentException("profile $uuid not found")

                    context.processingDir.deleteRecursively()
                    context.processingDir.mkdirs()

                    context.importedDir.resolve(imported.uuid.toString())
                        .copyRecursively(context.processingDir, overwrite = true)

                    imported
                }

                // Subscription URL management: apply new-url / new-domain migration
                // (persisting the new source onto the imported row) and pick a live
                // endpoint via fallback-url / fallback-domain. Best-effort — on any
                // error this is a no-op and the original source is used as before.
                val (migratedSource, downloadUrl) = resolveSubscriptionEndpoint(
                    context, snapshot.uuid, snapshot.type, snapshot.source
                ) { migrated -> ImportedDao().update(snapshot.copy(source = migrated)) }
                snapshot = snapshot.copy(source = migratedSource)

                // Converted profiles: re-fetch source (with pxa headers) and re-apply template.
                var convertedFetchResult: FetchedSource? = null
                if (snapshot.type == Profile.Type.Converted) {
                    val importedDir = context.importedDir.resolve(snapshot.uuid.toString())
                    val fetchResult = fetchSourceContentWithPxa(context, downloadUrl ?: snapshot.source)
                    convertedFetchResult = fetchResult

                    if (fetchResult.headersAvailable) {
                        // Update stored pxa meta with fresh header values.
                        TemplateManager.savePxaMeta(
                            importedDir,
                            fetchResult.pxaTemplateUrl,
                            fetchResult.allowTemplateSelection,
                            fetchResult.pxaTemplateScheme,
                        )
                    }

                    // Re-determine mode from fresh headers (or stored scheme as fallback).
                    val scheme = if (fetchResult.headersAvailable)
                        fetchResult.pxaTemplateScheme
                    else
                        TemplateManager.getPxaTemplateScheme(importedDir)
                    val allowSel = if (fetchResult.headersAvailable)
                        fetchResult.allowTemplateSelection
                    else
                        TemplateManager.isTemplateSelectionAllowed(importedDir)
                    val hasPxaTemplateUpd = !TemplateManager.getPxaTemplateUrl(importedDir).isNullOrBlank()
                    when {
                        hasPxaTemplateUpd && scheme == "proxy-providers" -> {
                            val intervalHours = fetchResult.rawHeaders
                                ?.get("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                            writeProxyProviderConfig(context, snapshot.source, intervalHours, importedDir, context.processingDir)
                        }
                        hasPxaTemplateUpd && scheme == "payload" -> {
                            val intervalHours = fetchResult.rawHeaders
                                ?.get("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                            writePayloadConfig(context, fetchResult.content, snapshot.source, intervalHours, importedDir, context.processingDir)
                        }
                        else -> convertAndWriteConfig(context, fetchResult.content, importedDir, context.processingDir)
                    }
                }

                // Install the age secret key so an age-encrypted config can be
                // decrypted by the core during this refresh; empty clears it.
                Clash.setAgeSecretKeys(snapshot.ageSecretKey)

                val fetchTarget = resolveFetchTarget(
                    context, snapshot.type, snapshot.source, downloadUrlOverride = downloadUrl
                )
                var cb = callback

                Clash.fetchAndValid(context.processingDir, fetchTarget.source, fetchTarget.force) {
                    try {
                        cb?.updateStatus(it)
                    } catch (e: Exception) {
                        cb = null

                        Log.w("Report fetch status: $e", e)
                    }
                }.await()

                profileLock.withLock {
                    if (ImportedDao().exists(snapshot.uuid)) {
                        val importedDir = context.importedDir.resolve(snapshot.uuid.toString())

                        // For Url profiles, preserve profile_links.json before wiping importedDir:
                        // updateFlow() may have written fresh announce/headers to it concurrently
                        // after processingDir was snapshotted, and we must not lose those updates.
                        val savedProfileLinks = if (snapshot.type == Profile.Type.Url) {
                            importedDir.resolve("profile_links.json")
                                .takeIf { it.exists() }?.readText()
                        } else null

                        importedDir.deleteRecursively()
                        context.processingDir.copyRecursively(importedDir)

                        // For Converted profiles: save standard profile headers and update
                        // subscription info in the DB record.
                        val fetchResult = convertedFetchResult
                        if (fetchResult != null && fetchResult.headersAvailable && fetchResult.rawHeaders != null) {
                            saveProfileHeaders(importedDir, fetchResult.rawHeaders)

                            val current = ImportedDao().queryByUUID(snapshot.uuid)
                            if (current != null) {
                                val sub = parseSubscriptionUserInfo(fetchResult.rawHeaders)
                                val profileTitle = fetchResult.rawHeaders["profile-title"]?.let { decodeHeaderValue(it) } ?: ""
                                val updateIntervalHours = fetchResult.rawHeaders["profile-update-interval"]?.trim()?.toIntOrNull() ?: 0
                                val resolvedName = if (profileTitle.isNotEmpty()) profileTitle else current.name
                                val resolvedInterval = if (updateIntervalHours > 0) updateIntervalHours.toLong() * 60 * 60 * 1000 else current.interval
                                ImportedDao().update(
                                    current.copy(
                                        name = resolvedName,
                                        interval = resolvedInterval,
                                        upload = sub[0],
                                        download = sub[1],
                                        total = sub[2],
                                        expire = sub[3],
                                    )
                                )
                            }
                        }

                        // Restore profile_links.json for Url profiles so that headers written
                        // by a concurrent updateFlow() are not overwritten by the stale snapshot.
                        if (savedProfileLinks != null) {
                            importedDir.resolve("profile_links.json").writeText(savedProfileLinks)
                        }

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
        /** `subscription-renew-url` — where to send the user to renew this subscription. */
        val renewUrl: String = "",
        val profileTitle: String = "",
        val profileLogo: String = "",
        val profileUpdateInterval: Int = 0,
        val announce: String = "",
        val hwidActive: Boolean = false,
        val latencyDots: Int = -1,
        val globalModeMp: Boolean = false,
        val connsViewMp: Boolean = false,
        val rpMp: Boolean = false,
        val simpleMode: Boolean = false,
        val fallbackUrl: String = "",
        val fallbackDomain: String = "",
        /**
         * Reminder thresholds from `notify-expire-days`/`notify-traffic-percent`.
         *
         * `null` and `emptyList()` mean different things and must stay
         * distinguishable: `null` is "the panel sent no header for this kind of
         * reminder at all" — this panel doesn't opt in, so no reminder of this
         * kind fires, full stop; an empty list is "the panel explicitly turned
         * this kind of reminder off" via an empty list value. Neither one falls
         * back to [SubscriptionAlerts.DEFAULT_EXPIRE_DAYS] /
         * [SubscriptionAlerts.DEFAULT_TRAFFIC_PERCENT] — those only fill in
         * thresholds for the bare `notification-subs-expire: true` toggle,
         * which is itself a header the panel had to send.
         */
        val notifyExpireDays: List<Int>? = null,
        val notifyTrafficPercent: List<Int>? = null,
        /**
         * How far the panel's clock is ahead of the device's, in seconds, and
         * when that was measured (device time). See [clockSkewMillis].
         */
        val clockSkewSeconds: Long = 0,
        val clockSkewAtSeconds: Long = 0,
    ) {
        /**
         * The clock-skew correction in milliseconds, or 0 when there is none to
         * apply.
         *
         * A measurement older than 30 days is discarded rather than used: what
         * is dangerous is not crystal drift (seconds a month) but the device
         * clock having been corrected since — by the user, or by time sync after
         * a reboot — which turns a stale correction into an error of its own
         * size. A negative age (measured "in the future") means the same thing
         * and is discarded the same way.
         */
        fun clockSkewMillis(): Long {
            if (clockSkewSeconds == 0L || clockSkewAtSeconds == 0L) return 0

            val ageSeconds = System.currentTimeMillis() / 1000 - clockSkewAtSeconds

            return if (ageSeconds in 0..MAX_CLOCK_SKEW_AGE_SECONDS) clockSkewSeconds * 1000 else 0
        }
    }

    private const val MAX_CLOCK_SKEW_AGE_SECONDS = 30L * 24 * 60 * 60

    /**
     * Parses a comma-separated list of reminder thresholds, e.g. `"7,3,1"`.
     *
     * Returns `null` when the panel said nothing usable (header absent, blank,
     * or unparseable) — the caller should fall back to its own defaults then.
     * Returns an ([lo]..[hi])-bounded, deduplicated, sorted, size-capped list
     * otherwise, or an explicit empty list for `"off"`/`"false"` — the panel
     * turning this kind of reminder off on purpose, not the same as staying
     * silent about it.
     */
    private fun parseThresholds(raw: String?, lo: Int, hi: Int): List<Int>? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        if (trimmed.equals("off", ignoreCase = true) || trimmed.equals("false", ignoreCase = true)) {
            return emptyList()
        }

        val values = trimmed.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in lo..hi }
            .distinct()
            .sorted()

        if (values.isEmpty()) return null

        // A malformed or hostile panel listing a thousand thresholds is a
        // thousand notifications, not a courtesy — cap it.
        return values.take(MAX_THRESHOLDS)
    }

    private const val MAX_THRESHOLDS = 10

    fun saveProfileHeaders(profileDir: File, headers: okhttp3.Headers) {
        try {
            val file = profileDir.resolve("profile_links.json")

            // Unlike every other field below, an absent `Date` header does not
            // mean "clear the clock-skew correction" — it means this particular
            // response said nothing about the server's clock, and the last real
            // measurement (if any) is still the best one available.
            val previousSkew = if (file.exists()) {
                try {
                    val prev = JSONObject(file.readText())
                    prev.optLong("clock_skew", 0) to prev.optLong("clock_skew_at", 0)
                } catch (_: Exception) {
                    0L to 0L
                }
            } else {
                0L to 0L
            }

            val json = JSONObject()
            headers["support-url"]?.let { if (it.isNotBlank()) json.put("support_url", it) }
            headers["profile-web-page-url"]?.let { if (it.isNotBlank()) json.put("profile_web_page_url", it) }
            headers["subscription-renew-url"]?.let { if (it.isNotBlank()) json.put("renew_url", it) }
            headers["profile-title"]?.let { if (it.isNotBlank()) json.put("profile_title", decodeHeaderValue(it)) }
            headers["profile-logo"]?.let { if (it.isNotBlank()) json.put("profile_logo", it) }
            headers["profile-update-interval"]?.let {
                val hours = it.trim().toIntOrNull()
                if (hours != null && hours > 0) json.put("profile_update_interval", hours)
            }
            headers["announce"]?.let { if (it.isNotBlank()) json.put("announce", decodeHeaderValue(it)) }
            if (isHeaderTrue(headers, "x-hwid-active")) json.put("x_hwid_active", true)
            // pxa-latency-dots (0 = numeric ms, 1 = colored dots). For Happ-compatibility
            // the `ping-result` alias is also accepted: time => 0, icon => 1.
            // The native pxa-latency-dots header takes precedence when both are present.
            val latencyDots = headers["pxa-latency-dots"]?.trim()?.toIntOrNull()?.takeIf { it == 0 || it == 1 }
                ?: when (headers["ping-result"]?.trim()?.lowercase()) {
                    "time" -> 0
                    "icon" -> 1
                    else -> null
                }
            if (latencyDots != null) json.put("pxa_latency_dots", latencyDots)
            if (headers["pxa-global-mode-mp"]?.trim() == "1") json.put("pxa_global_mode_mp", true)
            if (headers["pxa-conns-view-mp"]?.trim() == "1") json.put("pxa_conns_view_mp", true)
            if (headers["pxa-rp-mp"]?.trim() == "1") json.put("pxa_rp_mp", true)
            if (headers["pxa-simple-mode"]?.trim() == "1") json.put("pxa_simple_mode", true)
            // Subscription fallback endpoints (absent header => not written => cleared on read).
            headers["fallback-url"]?.trim()?.let { if (it.isNotBlank()) json.put("fallback_url", it) }
            headers["fallback-domain"]?.trim()?.let { if (it.isNotBlank()) json.put("fallback_domain", it) }

            // Reminder thresholds. Written WITHOUT the isNotBlank()-style skip used
            // above: null (key absent) and [] (empty array) mean different things
            // here and both must be representable — see ProfileHeaders' kdoc.
            var expireDays = parseThresholds(headers["notify-expire-days"], 1, 365)
            if (expireDays == null && isHeaderTrue(headers, "notification-subs-expire")) {
                // Bare toggle, no explicit list — Happ-style panels only send
                // this. Falling back to our own defaults keeps them working.
                expireDays = SubscriptionAlerts.DEFAULT_EXPIRE_DAYS
            }
            if (expireDays != null) {
                json.put("notify_expire_days", JSONArray(expireDays))
            }

            val trafficPercent = parseThresholds(headers["notify-traffic-percent"], 1, 100)
            if (trafficPercent != null) {
                json.put("notify_traffic_percent", JSONArray(trafficPercent))
            }

            // `Date` is a standard header, so no suffix search or base64 decoding
            // (the way most other panel headers are read) applies to it.
            val servedAt = parseHttpDateMillis(headers["Date"])
            if (servedAt != null) {
                val nowSeconds = System.currentTimeMillis() / 1000
                json.put("clock_skew", servedAt / 1000 - nowSeconds)
                json.put("clock_skew_at", nowSeconds)
            } else if (previousSkew.first != 0L || previousSkew.second != 0L) {
                json.put("clock_skew", previousSkew.first)
                json.put("clock_skew_at", previousSkew.second)
            }

            file.writeText(json.toString())
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
                    renewUrl = json.optString("renew_url", ""),
                    profileTitle = json.optString("profile_title", ""),
                    profileLogo = json.optString("profile_logo", ""),
                    profileUpdateInterval = json.optInt("profile_update_interval", 0),
                    announce = json.optString("announce", ""),
                    hwidActive = json.optBoolean("x_hwid_active", false),
                    latencyDots = if (json.has("pxa_latency_dots")) json.getInt("pxa_latency_dots") else -1,
                    globalModeMp = json.optBoolean("pxa_global_mode_mp", false),
                    connsViewMp = json.optBoolean("pxa_conns_view_mp", false),
                    rpMp = json.optBoolean("pxa_rp_mp", false),
                    simpleMode = json.optBoolean("pxa_simple_mode", false),
                    fallbackUrl = json.optString("fallback_url", ""),
                    fallbackDomain = json.optString("fallback_domain", ""),
                    // has() before reading: an absent key must come back null
                    // (caller falls back to its own defaults), not [] (caller
                    // stays silent) — see ProfileHeaders' kdoc.
                    notifyExpireDays = if (json.has("notify_expire_days")) {
                        json.getJSONArray("notify_expire_days").toIntList()
                    } else null,
                    notifyTrafficPercent = if (json.has("notify_traffic_percent")) {
                        json.getJSONArray("notify_traffic_percent").toIntList()
                    } else null,
                    clockSkewSeconds = json.optLong("clock_skew", 0),
                    clockSkewAtSeconds = json.optLong("clock_skew_at", 0),
                )
            } else ProfileHeaders()
        } catch (_: Exception) { ProfileHeaders() }
    }

    private fun JSONArray.toIntList(): List<Int> = List(length()) { getInt(it) }

    data class UrlHeaders(
        val title: String = "",
        val updateIntervalHours: Int = 0,
        val hwidNotSupported: Boolean = false,
        val hwidMaxDevicesReached: Boolean = false,
        val supportUrl: String = "",
    )

    suspend fun fetchUrlHeaders(context: Context, url: String): UrlHeaders {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(3, TimeUnit.SECONDS)
                    .callTimeout(3, TimeUnit.SECONDS)
                    .build()
                val baseRequest = buildProfileRequest(context, url)

                fun parse(response: okhttp3.Response): UrlHeaders {
                    val hdrs = response.headers
                    // Check HWID error headers first — they can arrive on any status code (incl. 4xx).
                    val hwidNotSupported = isHeaderTrue(hdrs, "x-hwid-not-supported")
                    val hwidMaxDevices = isHeaderTrue(hdrs, "x-hwid-max-devices-reached")
                    if (hwidNotSupported || hwidMaxDevices) {
                        return UrlHeaders(
                            hwidNotSupported = hwidNotSupported,
                            hwidMaxDevicesReached = hwidMaxDevices,
                            supportUrl = hdrs["support-url"]?.trim() ?: "",
                        )
                    }
                    if (!response.isSuccessful) return UrlHeaders()
                    val title = hdrs["profile-title"]?.let { decodeHeaderValue(it) } ?: ""
                    val interval = hdrs["profile-update-interval"]?.trim()?.toIntOrNull() ?: 0
                    return UrlHeaders(title, interval)
                }

                // Some servers don't support HEAD correctly. Fallback to lightweight GET.
                val headRequest = baseRequest.newBuilder().head().build()
                client.newCall(headRequest).execute().use { response ->
                    val headers = parse(response)
                    if (headers.hwidNotSupported || headers.hwidMaxDevicesReached
                        || headers.title.isNotEmpty() || headers.updateIntervalHours > 0) {
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

            // Converted profiles may have http/https URLs *or* raw proxy-link text as their
            // source, so skip the scheme check for them.
            source.isNotEmpty() && type != Profile.Type.Converted &&
                    scheme != "https" && scheme != "http" && scheme != "content" ->
                throw IllegalArgumentException("Unsupported url $source")

            interval != 0L && TimeUnit.MILLISECONDS.toMinutes(interval) < 15 ->
                throw IllegalArgumentException("Invalid interval")
        }
    }
}
