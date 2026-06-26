package com.github.kr328.clash.util

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
import com.github.kr328.clash.tv.TvCrypto
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shows a dialog to select an imported profile and sends it to the TV device
 * via the same /submit endpoint used by the browser web page.
 *
 * [tvUrl] is the URL from the QR code, e.g.
 *   "http://192.168.1.100:48291/Prizrak-BoxTVimport"
 */
suspend fun Activity.sendProfileToTv(tvUrl: String) {
    val profiles = withProfile { queryAll() }.filter { it.imported }

    if (profiles.isEmpty()) {
        Toast.makeText(this, getString(R.string.tv_send_no_profiles), Toast.LENGTH_LONG).show()
        return
    }

    // Parse the QR URL once. Strip any query (?v=1&t=..&k=..) to derive the base address.
    val uri = Uri.parse(tvUrl)
    val baseUrl = "${uri.scheme}://${uri.host}:${uri.port}"
    val hostPort = "${uri.host}:${uri.port}"

    // Ping the TV import server first: if its import window isn't open (or the device is
    // unreachable), tell the user up front instead of letting them pick a profile and only
    // failing on send.
    if (!pingTv(baseUrl)) {
        Toast.makeText(this, getString(R.string.tv_send_not_open), Toast.LENGTH_LONG).show()
        return
    }

    val displayItems = profiles.map { p ->
        val typeLabel = when (p.type) {
            Profile.Type.Url, Profile.Type.Converted ->
                getString(R.string.tv_profile_type_url)
            Profile.Type.File ->
                getString(R.string.tv_profile_type_yaml)
            Profile.Type.External ->
                getString(R.string.tv_profile_type_url)
        }
        "${p.name} ($typeLabel)"
    }.toTypedArray()

    var pendingSelection = -1
    val confirmed = suspendCancellableCoroutine<Boolean> { continuation ->
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.tv_send_profile_title, hostPort))
            .setSingleChoiceItems(displayItems, -1) { _, which ->
                pendingSelection = which
            }
            .setPositiveButton(R.string.tv_send_button) { _, _ ->
                if (!continuation.isCompleted) continuation.resume(pendingSelection >= 0) {}
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (!continuation.isCompleted) continuation.resume(false) {}
            }
            .setOnCancelListener {
                if (!continuation.isCompleted) continuation.resume(false) {}
            }
            .show()
        continuation.invokeOnCancellation { dialog.dismiss() }
    }

    if (!confirmed || pendingSelection < 0) return

    val profile = profiles[pendingSelection]

    // Use the richer /api/transfer endpoint (typed url/yaml + name + optional
    // age-secret-key). The browser web page keeps using /submit (untouched).
    val transferUrl = "$baseUrl/Prizrak-BoxTVimport/api/transfer"

    // Secrets carried in the QR: token (auth) + AES-256-GCM key (payload encryption).
    val token = uri.getQueryParameter("t")
    val keyB64 = uri.getQueryParameter("k")

    // Subscription URL for Url/Converted/External; raw YAML for File profiles.
    val (transferType, contentValue) = when (profile.type) {
        Profile.Type.Url, Profile.Type.Converted, Profile.Type.External ->
            "url" to profile.source

        Profile.Type.File -> {
            val yaml = withContext(Dispatchers.IO) {
                importedDir.resolve("${profile.uuid}/config.yaml")
                    .takeIf { it.exists() }
                    ?.readText()
            }
            if (yaml == null) {
                Toast.makeText(this, getString(R.string.tv_send_error), Toast.LENGTH_LONG).show()
                return
            }
            "yaml" to yaml
        }
    }

    val contentField = if (transferType == "url") "url" else "content"
    // Carry the profile's age-secret-key so an encrypted config decrypts on the TV.
    val keyJson = if (profile.ageSecretKey.isNotEmpty())
        ""","age-secret-key":"${escapeJson(profile.ageSecretKey)}""""
    else ""
    val jsonBody =
        """{"type":"$transferType","$contentField":"${escapeJson(contentValue)}","name":"${escapeJson(profile.name)}"$keyJson}"""

    // Secured path: authenticate with the token and encrypt the payload with the QR key.
    // Plain fallback when the QR has no token/key (older TV without the secured channel).
    val secured = !token.isNullOrEmpty() && !keyB64.isNullOrEmpty()
    val requestBody = if (secured) {
        val enc = TvCrypto.encrypt(TvCrypto.decodeB64(keyB64!!), jsonBody)
        """{"nonce":"${enc.nonce}","data":"${enc.data}"}"""
    } else {
        jsonBody
    }

    var errorMsg: String? = null
    val success = withContext(Dispatchers.IO) {
        try {
            Log.d("TvSender", "POST $transferUrl secured=$secured body=${requestBody.length} chars")
            val conn = URL(transferUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (secured) conn.setRequestProperty("X-PXA-Token", token!!)
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val bytes = requestBody.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", bytes.size.toString())
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val responseBody = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
            } catch (_: Exception) { "" }
            conn.disconnect()
            Log.d("TvSender", "Response code=$code body=$responseBody")
            if (code !in 200..299) {
                errorMsg = "HTTP $code: $responseBody"
            }
            code in 200..299
        } catch (e: Exception) {
            Log.e("TvSender", "Transfer failed", e)
            errorMsg = e.javaClass.simpleName + ": " + e.message
            false
        }
    }

    val toastText = if (success) {
        getString(R.string.tv_send_success)
    } else {
        val detail = errorMsg
        if (detail != null) "${getString(R.string.tv_send_error)}\n$detail"
        else getString(R.string.tv_send_error)
    }
    Toast.makeText(this, toastText, Toast.LENGTH_LONG).show()
}

/**
 * Quick reachability check: GET /Prizrak-BoxTVimport/api/ping with a short timeout.
 * Returns true only when the TV import server answers with a 2xx, i.e. its import
 * window is open and reachable on the LAN.
 */
private suspend fun pingTv(baseUrl: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val conn = URL("$baseUrl/Prizrak-BoxTVimport/api/ping").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 2_000
        conn.readTimeout = 2_000
        val code = conn.responseCode
        conn.disconnect()
        code in 200..299
    } catch (e: Exception) {
        Log.d("TvSender", "Ping failed: ${e.javaClass.simpleName}: ${e.message}")
        false
    }
}

private fun escapeJson(s: String): String = buildString {
    for (ch in s) when {
        ch == '"'      -> append("\\\"")
        ch == '\\'     -> append("\\\\")
        ch == '\n'     -> append("\\n")
        ch == '\r'     -> append("\\r")
        ch == '\t'     -> append("\\t")
        ch.code > 0x7F -> append("\\u%04x".format(ch.code))
        else           -> append(ch)
    }
}
