package com.github.kr328.clash.core.bridge

enum class ProfileFormat(val value: String) {
    CLASH("clash"),
    CLASH_META("clash_meta"),
    BASE64("base64"),
    SHADOWROCKET("shadowrocket"),
    V2RAY("v2ray"),
    SIP008("sip008"),
    UNKNOWN("unknown"),
    AUTO("auto");

    companion object {
        fun fromString(value: String): ProfileFormat {
            return values().find { it.value == value } ?: UNKNOWN
        }
    }

    override fun toString(): String = value
}
