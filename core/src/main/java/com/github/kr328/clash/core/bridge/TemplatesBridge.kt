package com.github.kr328.clash.core.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bridge for working with profile templates
 */
object TemplatesBridge {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Template(
        val id: String,
        val name: String,
        val description: String,
        val builtin: Boolean
    )

    /**
     * Get list of available templates
     */
    fun getAvailableTemplates(): List<Template> {
        val result = nativeGetAvailableTemplates()
        return try {
            json.decodeFromString<List<Template>>(result)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get current template ID for a profile
     */
    fun getCurrentTemplate(profilePath: String): String {
        return nativeGetCurrentTemplate(profilePath)
    }

    /**
     * Set template ID for a profile
     */
    fun setCurrentTemplate(profilePath: String, templateId: String) {
        val error = nativeSetCurrentTemplate(profilePath, templateId)
        if (error != null) {
            throw Exception(error)
        }
    }

    /**
     * Apply template to profile with proxies from subscription
     */
    fun applyTemplateToProfile(
        profilePath: String,
        templateContent: String,
        proxies: List<Map<String, Any>>
    ) {
        val normalized = proxies.map { map ->
            JsonObject(map.mapValues { (_, value) -> toJsonElement(value) })
        }

        val proxiesJson = json.encodeToString(ListSerializer(JsonObject.serializer()), normalized)

        val error = nativeApplyTemplateToProfile(profilePath, templateContent, proxiesJson)
        if (error != null) {
            throw Exception(error)
        }
    }

    /**
     * Validate template YAML syntax
     */
    fun validateTemplateYAML(content: String) {
        val error = nativeValidateTemplateYAML(content)
        if (error != null) {
            throw Exception(error)
        }
    }

    private fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonPrimitive("")
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }

    // Native methods
    private external fun nativeGetAvailableTemplates(): String
    private external fun nativeGetCurrentTemplate(profilePath: String): String
    private external fun nativeSetCurrentTemplate(profilePath: String, templateId: String): String?
    private external fun nativeApplyTemplateToProfile(
        profilePath: String,
        templateContent: String,
        proxiesJSON: String
    ): String?
    private external fun nativeValidateTemplateYAML(content: String): String?
}
