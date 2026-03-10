package com.github.kr328.clash.util

import android.app.Activity
import android.util.Log
import android.widget.Toast
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.importedDir
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

    val hostPort = try {
        val u = URL(tvUrl)
        "${u.host}:${u.port}"
    } catch (_: Exception) {
        tvUrl
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

    // Resolve the /submit endpoint – same one the browser web page uses
    val baseUrl = tvUrl.trimEnd('/').removeSuffix("/Prizrak-BoxTVimport")
    val submitUrl = "$baseUrl/Prizrak-BoxTVimport/submit"

    // Content to send: subscription URL for Url/Converted/External,
    // raw YAML for File profiles (mirrors what a user would paste in the browser)
    val content: String = when (profile.type) {
        Profile.Type.Url, Profile.Type.Converted, Profile.Type.External ->
            profile.source

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
            yaml
        }
    }

    // {"content":"..."} – identical format to the browser web page POST
    val jsonBody = """{"content":"${escapeJson(content)}"}"""

    var errorMsg: String? = null
    val success = withContext(Dispatchers.IO) {
        try {
            Log.d("TvSender", "POST $submitUrl body=${jsonBody.length} chars")
            val conn = URL(submitUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val bytes = jsonBody.toByteArray(Charsets.UTF_8)
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
