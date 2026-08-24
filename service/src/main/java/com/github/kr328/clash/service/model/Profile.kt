@file:UseSerializers(UUIDSerializer::class)

package com.github.kr328.clash.service.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import com.github.kr328.clash.service.util.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
data class Profile(
    val uuid: UUID,
    val name: String,
    val type: Type,
    val source: String,
    val active: Boolean,
    val interval: Long,
    val upload: Long,
    var download: Long,
    val total: Long,
    val expire: Long,


    val updatedAt: Long,
    val imported: Boolean,
    val pending: Boolean,
    val supportUrl: String = "",
    val profileWebPageUrl: String = "",
    val profileTitle: String = "",
    val profileLogo: String = "",
    val profileUpdateInterval: Int = 0,
    val announce: String = "",
    val hwidActive: Boolean = false,
    val allowTemplateSelection: Boolean = true,
    val latencyDots: Int = -1,
    val globalModeMp: Boolean = false,
    val connsViewMp: Boolean = false,
    val rpMp: Boolean = false,
    val simpleMode: Boolean = false,
    val ageSecretKey: String = "",
    /**
     * How far ahead the panel's clock is of the device's, in milliseconds
     * (already sign-corrected — add it to [System.currentTimeMillis] to land on
     * the panel's idea of "now"). 0 when there is no usable measurement.
     *
     * Device clocks drift — a wrong timezone, a manual change, a sync that
     * hasn't run yet since boot — and [expire] is compared against the device
     * clock everywhere it's read. Without this, "expires in 3 days" can read a
     * day off in either direction on a phone with bad time.
     */
    val clockSkewMillis: Long = 0,
    /**
     * Expiry/traffic reminder thresholds the panel opted this profile into
     * via `notify-expire-days`/`notify-traffic-percent`/`notification-subs-expire`
     * (see ProfileProcessor.ProfileHeaders). `null` means the panel sent no
     * such header at all — the profile editor's notification-info icon is
     * shown only when at least one of these is non-null.
     */
    val notifyExpireDays: List<Int>? = null,
    val notifyTrafficPercent: List<Int>? = null,
) : Parcelable {
    enum class Type {
        File, Url, External,
        /** Profile whose content was converted from proxy links (vless://, trojan://, etc.)
         *  or SingBox JSON via [com.github.kr328.clash.core.Clash.convertAndApplyTemplate].
         *  A Clash YAML template is applied during each import/update. */
        Converted
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Profile> {
        override fun createFromParcel(parcel: Parcel): Profile {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<Profile?> {
            return arrayOfNulls(size)
        }
    }
}
