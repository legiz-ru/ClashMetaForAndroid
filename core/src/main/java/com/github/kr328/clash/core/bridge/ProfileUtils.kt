package com.github.kr328.clash.core.bridge

import java.io.File
import java.io.InputStream

/**
 * Utility functions for working with profile files and streams
 */
object ProfileUtils {

    /**
     * Read and convert a profile from a file
     *
     * @param file The file containing the profile
     * @param sourceFormat The source format (AUTO for auto-detection)
     * @return ConvertResult with the converted config
     */
    suspend fun convertFileToClash(
        file: File,
        sourceFormat: ProfileFormat = ProfileFormat.AUTO
    ): ConvertResult {
        if (!file.exists()) {
            return ConvertResult(
                success = false,
                error = "File does not exist: ${file.absolutePath}"
            )
        }

        if (!file.canRead()) {
            return ConvertResult(
                success = false,
                error = "Cannot read file: ${file.absolutePath}"
            )
        }

        return try {
            val content = file.readText()
            ProfileConverter.convertToClash(content, sourceFormat)
        } catch (e: Exception) {
            ConvertResult(
                success = false,
                error = "Failed to read file: ${e.message}"
            )
        }
    }

    /**
     * Read and convert a profile from an InputStream
     *
     * @param inputStream The input stream containing the profile
     * @param sourceFormat The source format (AUTO for auto-detection)
     * @return ConvertResult with the converted config
     */
    suspend fun convertStreamToClash(
        inputStream: InputStream,
        sourceFormat: ProfileFormat = ProfileFormat.AUTO
    ): ConvertResult {
        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            ProfileConverter.convertToClash(content, sourceFormat)
        } catch (e: Exception) {
            ConvertResult(
                success = false,
                error = "Failed to read stream: ${e.message}"
            )
        }
    }

    /**
     * Save a ConvertResult to a file
     *
     * @param result The conversion result
     * @param outputFile The output file
     * @return true if successful, false otherwise
     */
    fun saveToFile(result: ConvertResult, outputFile: File): Boolean {
        if (!result.isSuccess()) {
            return false
        }

        return try {
            outputFile.parentFile?.mkdirs()
            outputFile.writeText(result.getClashConfig())
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a file contains a valid Clash profile
     *
     * @param file The file to check
     * @return true if valid, false otherwise
     */
    fun isValidClashFile(file: File): Boolean {
        if (!file.exists() || !file.canRead()) {
            return false
        }

        return try {
            val content = file.readText()
            ProfileConverter.isValidClashProfile(content)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Detect the format of a profile file
     *
     * @param file The file to detect
     * @return The detected ProfileFormat
     */
    fun detectFileFormat(file: File): ProfileFormat {
        if (!file.exists() || !file.canRead()) {
            return ProfileFormat.UNKNOWN
        }

        return try {
            val content = file.readText()
            ProfileConverter.detectFormat(content)
        } catch (e: Exception) {
            ProfileFormat.UNKNOWN
        }
    }

    /**
     * Get a user-friendly name for a ProfileFormat
     *
     * @param format The format
     * @return Human-readable format name
     */
    fun getFormatDisplayName(format: ProfileFormat): String {
        return when (format) {
            ProfileFormat.CLASH -> "Clash"
            ProfileFormat.CLASH_META -> "Clash Meta"
            ProfileFormat.BASE64 -> "Base64 (Subscription)"
            ProfileFormat.SHADOWROCKET -> "Shadowrocket"
            ProfileFormat.V2RAY -> "V2Ray"
            ProfileFormat.SIP008 -> "SIP008 (Shadowsocks)"
            ProfileFormat.UNKNOWN -> "Unknown"
            ProfileFormat.AUTO -> "Auto Detect"
        }
    }

    /**
     * Get supported import formats
     *
     * @return List of formats that can be imported
     */
    fun getSupportedImportFormats(): List<ProfileFormat> {
        return listOf(
            ProfileFormat.CLASH,
            ProfileFormat.BASE64,
            ProfileFormat.SIP008
        )
    }

    /**
     * Check if a format is supported for import
     *
     * @param format The format to check
     * @return true if supported, false otherwise
     */
    fun isSupportedFormat(format: ProfileFormat): Boolean {
        return format in getSupportedImportFormats()
    }
}
