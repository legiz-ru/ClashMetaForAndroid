package com.github.kr328.clash.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.PropertiesActivity
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.dialog.applyFetchStatus
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.service.ProfileProcessor
import com.github.kr328.clash.service.TemplateManager
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.pendingDir
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

data class ProfileImportResult(
    val uuid: UUID,
    val autoImported: Boolean,
)

/** Proxy-link URI schemes whose presence indicates raw proxy-link content. */
private val PROXY_SCHEMES = listOf(
    "vless://", "trojan://", "vmess://", "ss://", "ssr://",
    "hysteria://", "hysteria2://", "hy2://", "tuic://", "anytls://", "wireguard://"
)

private enum class HwidIssue { None, NotSupported, MaxDevicesReached }

private fun resolveHwidImportIssue(exception: Throwable): Pair<HwidIssue, String> {
    var current: Throwable? = exception
    while (current != null) {
        when (current) {
            is ProfileProcessor.HwidNotSupportedException ->
                return HwidIssue.NotSupported to ""
            is ProfileProcessor.HwidMaxDevicesReachedException ->
                return HwidIssue.MaxDevicesReached to current.supportUrl
        }
        val msg = current.message.orEmpty()
        if (msg.contains("HWID_NOT_SUPPORTED", ignoreCase = true))
            return HwidIssue.NotSupported to ""
        if (msg.contains("HWID_MAX_DEVICES_REACHED", ignoreCase = true))
            return HwidIssue.MaxDevicesReached to ""
        current = current.cause
    }
    return HwidIssue.None to ""
}

private suspend fun Context.showHwidNotSupportedImportDialog() {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val dialog = MaterialAlertDialogBuilder(this@showHwidNotSupportedImportDialog)
                .setTitle(com.github.kr328.clash.design.R.string.hwid_not_supported_title)
                .setMessage(com.github.kr328.clash.design.R.string.hwid_not_supported_msg)
                .setPositiveButton(com.github.kr328.clash.design.R.string.ok) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
                .setOnCancelListener { if (cont.isActive) cont.resume(Unit) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }
    }
}

private fun isAgeKeyRequiredImport(exception: Throwable): Boolean {
    var current: Throwable? = exception
    while (current != null) {
        if (current is ProfileProcessor.AgeKeyRequiredException) return true
        if (current.message.orEmpty().contains("AGE_KEY_REQUIRED", ignoreCase = true)) return true
        current = current.cause
    }
    return false
}

private suspend fun Context.showAgeKeyRequiredImportDialog(uuid: UUID) {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val dialog = MaterialAlertDialogBuilder(this@showAgeKeyRequiredImportDialog)
                .setTitle(com.github.kr328.clash.design.R.string.age_key_required_title)
                .setMessage(com.github.kr328.clash.design.R.string.age_key_required_msg)
                .setPositiveButton(com.github.kr328.clash.design.R.string.age_key_required_set) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
                .setNegativeButton(com.github.kr328.clash.design.R.string.cancel) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
                .setOnCancelListener { if (cont.isActive) cont.resume(Unit) }
                .show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }
    }
}

private suspend fun Context.showHwidMaxDevicesImportDialog(supportUrl: String) {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { cont ->
            val builder = MaterialAlertDialogBuilder(this@showHwidMaxDevicesImportDialog)
                .setTitle(com.github.kr328.clash.design.R.string.hwid_max_devices_title)
                .setMessage(com.github.kr328.clash.design.R.string.hwid_max_devices_msg)
                .setPositiveButton(com.github.kr328.clash.design.R.string.ok) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                }
                .setOnCancelListener { if (cont.isActive) cont.resume(Unit) }
            if (supportUrl.isNotEmpty()) {
                builder.setNeutralButton(com.github.kr328.clash.design.R.string.hwid_support_btn) { _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    try {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(supportUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                }
            }
            val dialog = builder.show()
            cont.invokeOnCancellation { dialog.dismiss() }
        }
    }
}

/**
 * Returns true when [input] starts with a recognised proxy-link URI scheme,
 * indicating that the content is a raw proxy-link list rather than an HTTP(S) URL.
 */
private fun isDirectProxyLinks(input: String): Boolean {
    val trimmed = input.trimStart()
    return PROXY_SCHEMES.any { trimmed.startsWith(it, ignoreCase = true) }
}

/**
 * Import a profile from [url].
 *
 * Routing logic:
 * 1. If [url] is a raw proxy-link (vless://, trojan://, etc.) → import as [Profile.Type.Converted]
 *    with the default template, then open [PropertiesActivity] so the user can change the template.
 * 2. Otherwise treat as an HTTP(S) subscription URL ([Profile.Type.Url]).
 *    - Auto-imports (commit + activate) when both profile headers are present.
 *    - If [forceAutoImport] is true (deeplink flow), auto-imports even without headers.
 *    - During commit, [ProfileProcessor] will auto-detect convertible content (proxy links,
 *      base64-encoded proxy-link lists) and transparently switch the stored type to Converted.
 *
 * [ageSecretKey] (optional) is stored on the profile so an age-encrypted config can be
 * decrypted during commit/load (used by the TV transfer flow to carry the sender's key).
 */
suspend fun Context.importProfileFromUrl(
    url: String,
    forceAutoImport: Boolean = false,
    ageSecretKey: String = "",
): ProfileImportResult {
    // Direct proxy links — bypass HTTP fetch entirely.
    if (isDirectProxyLinks(url)) {
        return importDirectProxyLinks(url)
    }

    // Standard HTTP(S) URL flow.
    val headers = ProfileProcessor.fetchUrlHeaders(this, url)

    // Show HWID dialog immediately if the server already signals an error — no profile created.
    if (headers.hwidNotSupported) {
        showHwidNotSupportedImportDialog()
        return ProfileImportResult(java.util.UUID.randomUUID(), false)
    }
    if (headers.hwidMaxDevicesReached) {
        showHwidMaxDevicesImportDialog(headers.supportUrl)
        return ProfileImportResult(java.util.UUID.randomUUID(), false)
    }

    val name = headers.title.ifEmpty { getString(com.github.kr328.clash.design.R.string.new_profile) }
    val intervalMs = if (headers.updateIntervalHours > 0) headers.updateIntervalHours.toLong() * 60 * 60 * 1000 else 0L

    val uuid = withProfile {
        create(Profile.Type.Url, name).also {
            patch(it, name, url, intervalMs, ageSecretKey)
        }
    }

    val hasAutoHeaders = headers.title.isNotEmpty() && headers.updateIntervalHours > 0
    val autoImported = hasAutoHeaders || forceAutoImport

    if (autoImported) {
        var hwidIssue = HwidIssue.None
        var hwidSupportUrl = ""
        var committed = false
        var ageKeyRequired = false

        withModelProgressBar {
            configure {
                isIndeterminate = true
                text = getString(com.github.kr328.clash.design.R.string.import_loading_activating)
            }

            try {
                withProfile {
                    coroutineScope {
                        commit(uuid) { status ->
                            launch { configure { applyFetchStatus(this@importProfileFromUrl, status) } }
                        }
                    }
                }
                withProfile { queryByUUID(uuid)?.let { setActive(it) } }
                committed = true
            } catch (e: Exception) {
                if (isAgeKeyRequiredImport(e)) {
                    ageKeyRequired = true
                    return@withModelProgressBar
                }
                val (issue, supportUrl) = resolveHwidImportIssue(e)
                hwidIssue = issue
                hwidSupportUrl = supportUrl
                if (issue == HwidIssue.None) {
                    Toast.makeText(
                        this@importProfileFromUrl,
                        getString(com.github.kr328.clash.design.R.string.import_profile_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
                return@withModelProgressBar
            }
        }

        if (ageKeyRequired) {
            showAgeKeyRequiredImportDialog(uuid)
        } else when (hwidIssue) {
            HwidIssue.NotSupported -> showHwidNotSupportedImportDialog()
            HwidIssue.MaxDevicesReached -> showHwidMaxDevicesImportDialog(hwidSupportUrl)
            HwidIssue.None -> if (committed) {
                startActivity(
                    MainActivity::class.intent
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
        }
    } else {
        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
    }

    return ProfileImportResult(uuid, autoImported)
}

/**
 * Creates a [Profile.Type.Converted] profile from raw proxy-link text, commits it with the
 * default template, activates it, and opens [PropertiesActivity] so the user can change the
 * template afterwards.
 */
private suspend fun Context.importDirectProxyLinks(proxyLinks: String): ProfileImportResult {
    val name = getString(com.github.kr328.clash.design.R.string.new_profile)

    val uuid = withProfile {
        create(Profile.Type.Converted, name).also {
            patch(it, name, proxyLinks, 0L)
        }
    }

    // Write default template metadata into the newly-created pending directory so that
    // ProfileProcessor.apply() can find it.
    val pendingProfileDir = pendingDir.resolve(uuid.toString())
    TemplateManager.saveSelectedTemplateId(pendingProfileDir, TemplateManager.Template.Default.id)

    withModelProgressBar {
        configure {
            isIndeterminate = true
            text = getString(com.github.kr328.clash.design.R.string.import_loading_activating)
        }

        try {
            withProfile {
                coroutineScope {
                    commit(uuid) { status ->
                        launch { configure { applyFetchStatus(this@importDirectProxyLinks, status) } }
                    }
                }
            }
            withProfile { queryByUUID(uuid)?.let { setActive(it) } }
        } catch (_: Exception) {
            Toast.makeText(
                this@importDirectProxyLinks,
                getString(com.github.kr328.clash.design.R.string.import_profile_failed),
                Toast.LENGTH_LONG
            ).show()
            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
            return@withModelProgressBar
        }
    }

    // Open PropertiesActivity so the user can switch templates.
    startActivity(PropertiesActivity::class.intent.setUUID(uuid))

    return ProfileImportResult(uuid, true)
}
