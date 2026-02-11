package com.github.kr328.clash.core.bridge

import android.content.Context
import com.github.kr328.clash.core.Clash
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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
            json.decodeFromString(result)
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
     * Load template content from assets or custom file
     */
    fun getTemplateContent(context: Context, templateId: String, profilePath: String): String {
        return when (templateId) {
            "default", "rubundle", "davoyan" -> {
                // Load from assets
                context.assets.open("templates/$templateId.yaml").use {
                    it.bufferedReader().readText()
                }
            }
            "custom" -> {
                // Load from profile directory
                val customFile = File(profilePath, "custom-template.yaml")
                if (customFile.exists()) {
                    customFile.readText()
                } else {
                    // Create default custom template
                    val defaultTemplate = context.assets.open("templates/default.yaml").use {
                        it.bufferedReader().readText()
                    }
                    customFile.writeText(defaultTemplate)
                    defaultTemplate
                }
            }
            else -> throw IllegalArgumentException("Unknown template ID: $templateId")
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
        val proxiesJson = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.builtins.serializer(),
                    kotlinx.serialization.json.JsonElement.serializer()
                )
            ),
            proxies.map { map ->
                map.mapValues { (_, value) ->
                    when (value) {
                        is String -> kotlinx.serialization.json.JsonPrimitive(value)
                        is Number -> kotlinx.serialization.json.JsonPrimitive(value)
                        is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                        else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
                    }
                }
            }
        )

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

    /**
     * Save custom template content
     */
    fun saveCustomTemplate(profilePath: String, content: String) {
        // Validate first
        validateTemplateYAML(content)

        // Save to file
        val customFile = File(profilePath, "custom-template.yaml")
        customFile.writeText(content)
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

    init {
        Clash.load()
    }
}
