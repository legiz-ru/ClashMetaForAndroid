package com.github.kr328.clash.util

import android.app.Activity
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
 * Shows a dialog to select an imported profile and sends it to the TV device.
 *
 * [tvUrl] is the URL from the QR code, e.g.
 *   "http://192.168.1.100:48291/Prizrak-BoxTVimport"
 *
 * Profile types:
 *  - Url / Converted  → POST type="url", url=profile.source  (TV imports like clipboard paste)
 *  - File             → read importedDir/{uuid}/config.yaml, POST type="yaml", content=...
 */
suspend fun Activity.sendProfileToTv(tvUrl: String) {
    // Load all imported profiles (not pending/unimported)
    val profiles = withProfile { queryAll() }.filter { it.imported }

    if (profiles.isEmpty()) {
        Toast.makeText(this, getString(R.string.tv_send_no_profiles), Toast.LENGTH_LONG).show()
        return
    }

    // Extract host:port for the dialog subtitle
    val hostPort = try {
        val u = URL(tvUrl)
        "${u.host}:${u.port}"
    } catch (_: Exception) {
        tvUrl
    }

    // Build display items: "Profile Name (URL)" or "Profile Name (YAML)"
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

    // Show single-choice dialog; suspends until user picks or cancels
    var pendingSelection = -1
    val confirmed = suspendCancellableCoroutine<Boolean> { continuation ->
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.tv_send_profile_title, hostPort))
            .setSingleChoiceItems(displayItems, -1) { _, which ->
                pendingSelection = which
            }
            .setPositiveButton(R.string.tv_send_button) { _, _ ->
                if (!continuation.isCompleted) continuation.resume(pendingSelection >= 0)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                if (!continuation.isCompleted) continuation.resume(false)
            }
            .setOnCancelListener {
                if (!continuation.isCompleted) continuation.resume(false)
            }
            .show()
        continuation.invokeOnCancellation { dialog.dismiss() }
    }

    if (!confirmed || pendingSelection < 0) return

    val profile = profiles[pendingSelection]

    // Build POST endpoint: strip trailing /Prizrak-BoxTVimport if present, then append /api/transfer
    val baseUrl = tvUrl.trimEnd('/')
        .let { if (it.endsWith("/Prizrak-BoxTVimport")) it else "$it/Prizrak-BoxTVimport" }
    val transferUrl = "$baseUrl/api/transfer"

    // Build JSON payload based on profile type
    val jsonBody = when (profile.type) {
        Profile.Type.Url, Profile.Type.Converted -> {
            buildJsonObject(
                "type" to "url",
                "url" to profile.source,
                "name" to profile.name
            )
        }
        Profile.Type.File -> {
            val yamlContent = withContext(Dispatchers.IO) {
                importedDir.resolve("${profile.uuid}/config.yaml")
                    .takeIf { it.exists() }
                    ?.readText()
            }
            if (yamlContent == null) {
                Toast.makeText(this, getString(R.string.tv_send_error), Toast.LENGTH_LONG).show()
                return
            }
            buildJsonObject(
                "type" to "yaml",
                "content" to yamlContent,
                "name" to profile.name
            )
        }
        Profile.Type.External -> {
            // External profiles cannot be serialised; send source URL
            buildJsonObject(
                "type" to "url",
                "url" to profile.source,
                "name" to profile.name
            )
        }
    }

    // POST to TV server (network on IO dispatcher)
    val success = withContext(Dispatchers.IO) {
        try {
            val conn = URL(transferUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val bytes = jsonBody.toByteArray(Charsets.UTF_8)
            conn.setRequestProperty("Content-Length", bytes.size.toString())
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    Toast.makeText(
        this,
        if (success) getString(R.string.tv_send_success) else getString(R.string.tv_send_error),
        Toast.LENGTH_LONG
    ).show()
}

// ── Minimal JSON builder (no external dependency) ────────────────────────────

private fun escapeJson(s: String): String = buildString {
    for (ch in s) when (ch) {
        '"'  -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> append(ch)
    }
}

private fun buildJsonObject(vararg pairs: Pair<String, String>): String =
    pairs.joinToString(",", "{", "}") { (k, v) ->
        "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
    }
