package com.github.kr328.clash.common.util

import android.content.Context
import android.content.pm.PackageManager

object TvUtils {
    fun isTv(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}
