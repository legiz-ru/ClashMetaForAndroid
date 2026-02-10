package com.github.kr328.clash.core.bridge

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-level API for profile format conversion
 */
object ProfileConverter {

    /**
     * Detect the format of a profile content
     *
     * @param content The profile content to detect
     * @return The detected ProfileFormat
     */
    fun detectFormat(content: String): ProfileFormat {
        val formatString = Bridge.nativeDetectProfileFormat(content)
        return ProfileFormat.fromString(formatString)
    }

    /**
     * Convert a profile to Clash format (async)
     *
     * @param content The profile content to convert
     * @param sourceFormat The source format (use AUTO for auto-detection)
     * @return ConvertResult containing the converted config or error
     */
    suspend fun convertToClash(
        content: String,
        sourceFormat: ProfileFormat = ProfileFormat.AUTO
    ): ConvertResult = withContext(Dispatchers.IO) {
        val completable = CompletableDeferred<String>()

        Bridge.nativeConvertProfileToClash(
            completable,
            content,
            sourceFormat.value
        )

        val resultJson = completable.await()
        ConvertResult.fromJson(resultJson)
    }

    /**
     * Convert a profile to Clash format (blocking)
     *
     * @param content The profile content to convert
     * @param sourceFormat The source format (use AUTO for auto-detection)
     * @return ConvertResult containing the converted config or error
     */
    @Deprecated(
        "Use suspend version instead",
        ReplaceWith("convertToClash(content, sourceFormat)", "kotlinx.coroutines.runBlocking")
    )
    fun convertToClashBlocking(
        content: String,
        sourceFormat: ProfileFormat = ProfileFormat.AUTO
    ): ConvertResult {
        return kotlinx.coroutines.runBlocking {
            convertToClash(content, sourceFormat)
        }
    }

    /**
     * Validate a Clash profile
     *
     * @param content The Clash profile content to validate
     * @return ConvertResult indicating validation success or error
     */
    fun validate(content: String): ConvertResult {
        val resultJson = Bridge.nativeValidateClashProfile(content)
        return ConvertResult.fromJson(resultJson)
    }

    /**
     * Check if content is a valid Clash profile
     *
     * @param content The content to check
     * @return true if valid, false otherwise
     */
    fun isValidClashProfile(content: String): Boolean {
        return validate(content).isSuccess()
    }

    /**
     * Detect and convert a profile to Clash format in one step
     *
     * @param content The profile content
     * @return ConvertResult with the converted config
     */
    suspend fun autoConvert(content: String): ConvertResult {
        return convertToClash(content, ProfileFormat.AUTO)
    }

    /**
     * Convert a Base64 encoded profile to Clash format
     *
     * @param base64Content The Base64 encoded content
     * @return ConvertResult with the converted config
     */
    suspend fun convertBase64ToClash(base64Content: String): ConvertResult {
        return convertToClash(base64Content, ProfileFormat.BASE64)
    }

    /**
     * Convert a SIP008 (Shadowsocks JSON) profile to Clash format
     *
     * @param sip008Content The SIP008 JSON content
     * @return ConvertResult with the converted config
     */
    suspend fun convertSIP008ToClash(sip008Content: String): ConvertResult {
        return convertToClash(sip008Content, ProfileFormat.SIP008)
    }
}
