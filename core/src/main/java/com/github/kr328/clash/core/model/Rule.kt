package com.github.kr328.clash.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Rule(
    val type: String,
    val payload: String,
    val proxy: String,
)
