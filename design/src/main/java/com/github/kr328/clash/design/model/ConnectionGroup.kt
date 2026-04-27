package com.github.kr328.clash.design.model

import android.graphics.drawable.Drawable
import com.github.kr328.clash.core.model.Connection

data class ConnectionGroup(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val connections: List<Connection>,
    val uploadSpeed: Long,
    val downloadSpeed: Long,
)
