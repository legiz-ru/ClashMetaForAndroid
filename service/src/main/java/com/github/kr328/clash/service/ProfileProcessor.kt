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
        val success: Boolean,
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

    private fun prefetchProfileConfig(context: Context, source: String, targetConfigFile: File): PrefetchResult {
        return try {
            val request = buildProfileRequest(context, source)
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return PrefetchResult(false)

                val body = response.body ?: return PrefetchResult(false)
                targetConfigFile.parentFile?.mkdirs()
                targetConfigFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                PrefetchResult(true, response.headers)
            }
        } catch (_: IOException) {
            PrefetchResult(false)
        } catch (_: Exception) {
            PrefetchResult(false)
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
        val request = buildProfileRequest(context, source)
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to fetch profile: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Empty response body")
            val pxaTemplateUrl = response.headers["pxa-template"]?.trim()?.ifBlank { null }
            val pxaTemplateScheme = response.headers["pxa-template-scheme"]?.trim()?.ifBlank { null }
            // Template selection is allowed unless the server locks it via pxa-template.
            val allowTemplateSelection = pxaTemplateUrl == null && pxaTemplateScheme == null
            return FetchedSource(body, pxaTemplateUrl, allowTemplateSelection, pxaTemplateScheme, headersAvailable = true, rawHeaders = response.headers)
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
    ): FetchTarget {
        val isHttpUrl = source.startsWith("https://", true) || source.startsWith("http://", true)

        if (type == Profile.Type.Url && isHttpUrl) {
            if (!alreadyPrefetched) {
                val localConfig = context.processingDir.resolve("config.yaml")
                val result = prefetchProfileConfig(context, source, localConfig)
                if (!result.success) {
                    throw IOException("Unable to fetch url profile with HWID headers")
                }
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

                // Accumulates HTTP response headers for profiles that end up being converted,
                // so we can save profile metadata (title, logo, subscription info, etc.) later.
                var convertedHeaders: okhttp3.Headers? = null

                // Converted profiles: fetch source (with pxa headers), update meta, convert.
                if (snapshot.type == Profile.Type.Converted) {
                    val pendingDir = context.pendingDir.resolve(snapshot.uuid.toString())
                    val fetchResult = fetchSourceContentWithPxa(context, snapshot.source)
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
                    val prefetchResult = prefetchProfileConfig(context, snapshot.source, localConfig)
                    if (!prefetchResult.success) {
                        throw IOException("Unable to fetch url profile with HWID headers")
                    }
                    alreadyPrefetched = true

                    if (localConfig.exists()) {
                        val content = localConfig.readText(Charsets.UTF_8)
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
                                old?.createdAt ?: System.currentTimeMillis()
                            )
                            if (old != null) ImportedDao().update(new) else ImportedDao().insert(new)
                            PendingDao().remove(snapshot.uuid)
                            context.pendingDir.resolve(snapshot.uuid.toString()).deleteRecursively()
                            context.sendProfileChanged(snapshot.uuid)
                        } else if (snapshot?.type == Profile.Type.Url) {
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

                // Converted profiles: re-fetch source (with pxa headers) and re-apply template.
                var convertedFetchResult: FetchedSource? = null
                if (snapshot.type == Profile.Type.Converted) {
                    val importedDir = context.importedDir.resolve(snapshot.uuid.toString())
                    val fetchResult = fetchSourceContentWithPxa(context, snapshot.source)
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

                val fetchTarget = resolveFetchTarget(context, snapshot.type, snapshot.source)
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
        val profileTitle: String = "",
        val profileLogo: String = "",
        val profileUpdateInterval: Int = 0,
        val announce: String = "",
        val latencyDots: Int = -1,
        val globalModeMp: Boolean = false,
        val connsViewMp: Boolean = false,
        val rpMp: Boolean = false,
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
            headers["pxa-latency-dots"]?.trim()?.toIntOrNull()?.let {
                if (it == 0 || it == 1) json.put("pxa_latency_dots", it)
            }
            if (headers["pxa-global-mode-mp"]?.trim() == "1") json.put("pxa_global_mode_mp", true)
            if (headers["pxa-conns-view-mp"]?.trim() == "1") json.put("pxa_conns_view_mp", true)
            if (headers["pxa-rp-mp"]?.trim() == "1") json.put("pxa_rp_mp", true)
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
                    latencyDots = if (json.has("pxa_latency_dots")) json.getInt("pxa_latency_dots") else -1,
                    globalModeMp = json.optBoolean("pxa_global_mode_mp", false),
                    connsViewMp = json.optBoolean("pxa_conns_view_mp", false),
                    rpMp = json.optBoolean("pxa_rp_mp", false),
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
