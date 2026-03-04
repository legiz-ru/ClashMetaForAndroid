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

    private const val KEY_TEMPLATE_ID = "templateId"
    private const val KEY_PXA_TEMPLATE_URL = "pxaTemplateUrl"
    private const val KEY_ALLOW_TEMPLATE_SELECTION = "allowTemplateSelection"
    private const val KEY_PXA_TEMPLATE_SCHEME = "pxaTemplateScheme"

    /**
     * Special template id meaning "use the server-specified pxa-template URL".
     * Stored as the selected templateId when the user picks "Шаблон из подписки".
     */
    const val PXA_SUBSCRIPTION_TEMPLATE_ID = "pxa_subscription"

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
    // Per-profile metadata (selected template + pxa headers)
    // -------------------------------------------------------------------------

    private fun readMeta(profileDir: File): JSONObject {
        val metaFile = profileDir.resolve(META_FILE)
        if (!metaFile.exists()) return JSONObject()
        return try { JSONObject(metaFile.readText()) } catch (_: Exception) { JSONObject() }
    }

    private fun writeMeta(profileDir: File, json: JSONObject) {
        profileDir.mkdirs()
        profileDir.resolve(META_FILE).writeText(json.toString(), Charsets.UTF_8)
    }

    /**
     * Returns the template id stored in [profileDir]/[META_FILE], or
     * [Template.Default.id] when the file is absent / malformed.
     */
    fun getSelectedTemplateId(profileDir: File): String =
        readMeta(profileDir).optString(KEY_TEMPLATE_ID, Template.Default.id)

    /**
     * Persists [templateId] to [profileDir]/[META_FILE], preserving any
     * other fields (pxa meta) already present.
     */
    fun saveSelectedTemplateId(profileDir: File, templateId: String) {
        val json = readMeta(profileDir)
        json.put(KEY_TEMPLATE_ID, templateId)
        writeMeta(profileDir, json)
    }

    /**
     * Returns the pxa-template URL stored in [profileDir]/[META_FILE], or null
     * if not set. When non-null, this URL is used as the conversion template
     * instead of the user-selected built-in template.
     */
    fun getPxaTemplateUrl(profileDir: File): String? =
        readMeta(profileDir).optString(KEY_PXA_TEMPLATE_URL, "").ifBlank { null }

    /**
     * Returns whether the user is allowed to change the conversion template for
     * this profile. Defaults to true when no metadata is stored.
     *
     * - If no pxa-template header: always true (normal behavior).
     * - If pxa-template present: false, unless pxa-change-template also present.
     */
    fun isTemplateSelectionAllowed(profileDir: File): Boolean =
        readMeta(profileDir).optBoolean(KEY_ALLOW_TEMPLATE_SELECTION, true)

    /**
     * Returns the pxa-template-scheme value stored for this profile, e.g. "proxy-providers".
     * Null when not set (normal convert.go mode).
     */
    fun getPxaTemplateScheme(profileDir: File): String? =
        readMeta(profileDir).optString(KEY_PXA_TEMPLATE_SCHEME, "").ifBlank { null }

    /**
     * Saves pxa header values to [profileDir]/[META_FILE], preserving the
     * existing selected template id.
     *
     * @param pxaTemplateUrl        URL from the pxa-template response header, or null to clear.
     * @param allowTemplateSelection Whether the user may change the template.
     * @param pxaTemplateScheme     Value of pxa-template-scheme header (e.g. "proxy-providers"), or null to clear.
     */
    fun savePxaMeta(
        profileDir: File,
        pxaTemplateUrl: String?,
        allowTemplateSelection: Boolean,
        pxaTemplateScheme: String? = null,
    ) {
        val json = readMeta(profileDir)
        if (!pxaTemplateUrl.isNullOrBlank()) {
            json.put(KEY_PXA_TEMPLATE_URL, pxaTemplateUrl)
        } else {
            json.remove(KEY_PXA_TEMPLATE_URL)
        }
        json.put(KEY_ALLOW_TEMPLATE_SELECTION, allowTemplateSelection)
        if (!pxaTemplateScheme.isNullOrBlank()) {
            json.put(KEY_PXA_TEMPLATE_SCHEME, pxaTemplateScheme)
        } else {
            json.remove(KEY_PXA_TEMPLATE_SCHEME)
        }
        writeMeta(profileDir, json)
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
