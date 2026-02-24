package com.github.kr328.clash.service

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Manages the built-in and custom Clash YAML templates used when importing
 * profiles via proxy-link conversion (Profile.Type.Converted).
 *
 * Built-in templates are bundled as assets inside the service module.
 * The custom template is loaded from a file path stored in SharedPreferences.
 */
object TemplateManager {

    /** All templates that can be selected by the user. */
    enum class Template(
        val id: String,
        /** Display name shown in the UI. */
        val displayName: String,
        /** Asset path relative to `assets/`, or null for custom. */
        val assetPath: String?
    ) {
        Default("default", "Default (Prizrak-Box)", "templates/template_default.yaml"),
        RuBundle("ru_bundle", "RU-Bundle by Legiz", "templates/template_ru_bundle.yaml"),
        Ultimate("ultimate", "Ultimate by Davoyan", "templates/template_ultimate.yaml"),
        DefaultSmart("default_smart", "Default + smart", "templates/template_default_smart.yaml"),
        RuBundleSmart("ru_bundle_smart", "RU-Bundle by Legiz + smart", "templates/template_ru_bundle_smart.yaml"),
        UltimateSmart("ultimate_smart", "Ultimate by Davoyan + smart", "templates/template_ultimate_smart.yaml"),
        ChinaSmart("china_smart", "🇨🇳 by qichiyuhub + smart", "templates/template_china_smart.yaml"),
        Custom("custom", "Custom", null);

        companion object {
            fun fromId(id: String): Template =
                entries.firstOrNull { it.id == id } ?: Default
        }
    }

    /** Name of the per-profile metadata file persisted alongside config.yaml. */
    const val META_FILE = "template_meta.json"

    /** SharedPreferences key for the user-supplied custom template file path. */
    private const val PREF_CUSTOM_PATH = "custom_template_path"
    private const val PREFS_NAME = "ui"

    // -------------------------------------------------------------------------
    // Template content loading
    // -------------------------------------------------------------------------

    /**
     * Loads the YAML content for the given [templateId].
     * Falls back to [Template.Default] if the id is unknown or the custom path
     * is unset / unreadable.
     */
    fun loadTemplate(context: Context, templateId: String): String {
        val template = Template.fromId(templateId)
        if (template == Template.Custom) {
            val path = getCustomTemplatePath(context)
            if (!path.isNullOrBlank()) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    return file.readText(Charsets.UTF_8)
                }
            }
            // Fallback to Default when custom file is unavailable.
            return loadAsset(context, Template.Default.assetPath!!)
        }
        return loadAsset(context, template.assetPath!!)
    }

    private fun loadAsset(context: Context, path: String): String =
        context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }

    // -------------------------------------------------------------------------
    // Per-profile metadata (selected template)
    // -------------------------------------------------------------------------

    /**
     * Returns the template id stored in [profileDir]/[META_FILE], or
     * [Template.Default.id] when the file is absent / malformed.
     */
    fun getSelectedTemplateId(profileDir: File): String {
        val metaFile = profileDir.resolve(META_FILE)
        if (!metaFile.exists()) return Template.Default.id
        return try {
            JSONObject(metaFile.readText()).optString("templateId", Template.Default.id)
        } catch (_: Exception) {
            Template.Default.id
        }
    }

    /**
     * Persists [templateId] to [profileDir]/[META_FILE].
     * Creates parent directories as needed.
     */
    fun saveSelectedTemplateId(profileDir: File, templateId: String) {
        profileDir.mkdirs()
        val json = JSONObject().apply { put("templateId", templateId) }
        profileDir.resolve(META_FILE).writeText(json.toString(), Charsets.UTF_8)
    }

    /**
     * Returns true when the given profile directory contains a template
     * metadata file, indicating this is a Converted profile.
     */
    fun hasTemplateMeta(profileDir: File): Boolean =
        profileDir.resolve(META_FILE).exists()

    // -------------------------------------------------------------------------
    // Custom template path (SharedPreferences)
    // -------------------------------------------------------------------------

    fun getCustomTemplatePath(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CUSTOM_PATH, null)

    fun setCustomTemplatePath(context: Context, path: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_PATH, path)
            .apply()
    }
}
