package com.github.kr328.clash.util

import android.content.Context
import android.widget.Toast
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.PropertiesActivity
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.service.ProfileProcessor
import com.github.kr328.clash.service.model.Profile
import java.util.UUID

/**
 * Import a profile from URL. Checks profile-title and profile-update-interval headers.
 * If both are present, auto-commits and activates without showing PropertiesActivity.
 * Otherwise opens PropertiesActivity for manual editing.
 * Returns the UUID of the created profile.
 */
suspend fun Context.importProfileFromUrl(url: String): UUID {
    val headers = ProfileProcessor.fetchUrlHeaders(this, url)
    val name = headers.title.ifEmpty { getString(com.github.kr328.clash.design.R.string.new_profile) }
    val intervalMs = if (headers.updateIntervalHours > 0) headers.updateIntervalHours.toLong() * 60 * 60 * 1000 else 0L

    val uuid = withProfile {
        create(Profile.Type.Url, name).also {
            patch(it, name, url, intervalMs)
        }
    }

    val autoImported = headers.title.isNotEmpty() && headers.updateIntervalHours > 0

    if (autoImported) {
        withModelProgressBar {
            configure {
                isIndeterminate = true
                text = getString(com.github.kr328.clash.design.R.string.import_loading_activating)
            }

            try {
                withProfile { commit(uuid, null) }
                withProfile { queryByUUID(uuid)?.let { setActive(it) } }
            } catch (_: Exception) {
                Toast.makeText(
                    this@importProfileFromUrl,
                    getString(com.github.kr328.clash.design.R.string.import_profile_failed),
                    Toast.LENGTH_LONG
                ).show()
                startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                return@withModelProgressBar
            }
        }

        startActivity(
            MainActivity::class.intent
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    } else {
        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
    }

    return uuid
}
