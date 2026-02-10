package com.github.kr328.clash.util

import android.content.Context
import com.github.kr328.clash.PropertiesActivity
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
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

    if (headers.title.isNotEmpty() && headers.updateIntervalHours > 0) {
        // Auto-import: commit and activate
        withProfile { commit(uuid, null) }
        withProfile { setActive(queryByUUID(uuid)!!) }
    } else {
        // Open properties for manual editing
        startActivity(PropertiesActivity::class.intent.setUUID(uuid))
    }

    return uuid
}
