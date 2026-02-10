package com.github.kr328.clash.core.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ConvertResult(
    val success: Boolean,
    val content: String? = null,
    val format: String? = null,
    val error: String? = null
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun fromJson(jsonString: String): ConvertResult {
            return try {
                json.decodeFromString(jsonString)
            } catch (e: Exception) {
                ConvertResult(
                    success = false,
                    error = "Failed to parse result: ${e.message}"
                )
            }
        }
    }

    fun getDetectedFormat(): ProfileFormat {
        return format?.let { ProfileFormat.fromString(it) } ?: ProfileFormat.UNKNOWN
    }

    fun isSuccess(): Boolean = success && content != null

    fun getErrorMessage(): String = error ?: "Unknown error"

    fun getClashConfig(): String {
        if (!isSuccess()) {
            throw IllegalStateException("Cannot get config from failed conversion: ${getErrorMessage()}")
        }
        return content!!
    }
}
