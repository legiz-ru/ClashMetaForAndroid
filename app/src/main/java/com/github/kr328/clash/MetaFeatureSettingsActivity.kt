package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.MetaFeatureSettingsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.util.GetContentCompat
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

private enum class GeoImport { GeoIp, GeoSite, Country, ASN }

class MetaFeatureSettingsActivity : BaseActivity() {
    private val resetRequests = Channel<Unit>(Channel.CONFLATED)

    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                MetaFeatureSettingsScreen(
                    configuration = configuration,
                    onBack = { finish() },
                    onReset = { resetRequests.trySend(Unit) },
                    onImportGeoIp = { launchImport(GeoImport.GeoIp) },
                    onImportGeoSite = { launchImport(GeoImport.GeoSite) },
                    onImportCountry = { launchImport(GeoImport.Country) },
                    onImportASN = { launchImport(GeoImport.ASN) },
                    onGenerateAgeKeyPair = { showAgeKeypairDialog() },
                )
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                resetRequests.onReceive {
                    if (requestResetConfirm()) {
                        defer {
                            withClash {
                                clearOverride(Clash.OverrideSlot.Persist)
                            }
                        }
                        finish()
                    }
                }
            }
        }
    }

    private fun launchImport(type: GeoImport) {
        launch {
            val uri = startActivityForResult(GetContentCompat(), "*/*")
            importGeoFile(uri, type)
        }
    }

    private suspend fun requestResetConfirm(): Boolean {
        return suspendCancellableCoroutine { ctx ->
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_override_settings)
                .setMessage(R.string.reset_override_settings_message)
                .setPositiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .show()

            dialog.setOnDismissListener {
                if (!ctx.isCompleted) ctx.resume(false)
            }

            ctx.invokeOnCancellation { dialog.dismiss() }
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun showAgeKeypairDialog() {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_age_keypair, null)

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.key_type_group)
        val btnRegenerate = dialogView.findViewById<android.widget.ImageButton>(R.id.btn_regenerate)
        val publicKeyText = dialogView.findViewById<TextView>(R.id.public_key_text)
        val privateKeyText = dialogView.findViewById<TextView>(R.id.private_key_text)
        val btnCopyPublic = dialogView.findViewById<android.widget.ImageButton>(R.id.btn_copy_public)
        val btnCopyPrivate = dialogView.findViewById<android.widget.ImageButton>(R.id.btn_copy_private)

        var currentKeyType = "MLKEM768-X25519"
        var currentPublicKey = ""
        var currentPrivateKey = ""

        fun updateKeys(secretKey: String, publicKey: String) {
            currentPublicKey = publicKey
            currentPrivateKey = secretKey
            publicKeyText.text = publicKey
            privateKeyText.text = secretKey
        }

        fun regenerate() {
            val pair = Clash.generateAgeKeyPair(currentKeyType)
            if (pair != null) {
                updateKeys(pair.first, pair.second)
            }
        }

        regenerate()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.age_keypair_result_title)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ -> }
            .create()

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            currentKeyType = if (checkedId == R.id.radio_x25519) "X25519" else "MLKEM768-X25519"
            regenerate()
        }

        btnRegenerate.setOnClickListener { regenerate() }

        btnCopyPublic.setOnClickListener {
            if (currentPublicKey.isNotEmpty())
                copyToClipboard(getString(R.string.age_public_key), currentPublicKey)
        }

        btnCopyPrivate.setOnClickListener {
            if (currentPrivateKey.isNotEmpty())
                copyToClipboard(getString(R.string.age_private_key), currentPrivateKey)
        }

        dialog.show()
    }

    private val validDatabaseExtensions = listOf(
        ".metadb", ".db", ".dat", ".mmdb"
    )

    private suspend fun importGeoFile(uri: Uri?, importType: GeoImport) {
        val cursor: Cursor? = uri?.let {
            contentResolver.query(it, null, null, null, null, null)
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    if (columnIndex != -1) it.getString(columnIndex) else ""
                val ext = "." + displayName.substringAfterLast(".")

                if (!validDatabaseExtensions.contains(ext)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.geofile_unknown_db_format)
                        .setMessage(
                            getString(
                                R.string.geofile_unknown_db_format_message,
                                validDatabaseExtensions.joinToString("/")
                            )
                        )
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
                    return
                }
                val outputFileName = when (importType) {
                    GeoImport.GeoIp -> "geoip$ext"
                    GeoImport.GeoSite -> "geosite$ext"
                    GeoImport.Country -> "country$ext"
                    GeoImport.ASN -> "ASN$ext"
                    else -> ""
                }

                withContext(Dispatchers.IO) {
                    val outputFile = File(clashDir, outputFileName)
                    contentResolver.openInputStream(uri).use { ins ->
                        FileOutputStream(outputFile).use { outs ->
                            ins?.copyTo(outs)
                        }
                    }
                }
                Toast.makeText(
                    this, getString(R.string.geofile_imported, displayName),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    }

    private fun currentThemeVariant(): ClashThemeVariant {
        val cfg = resources.configuration
        return when (uiStore.darkMode) {
            DarkMode.Auto ->
                if (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
                    ClashThemeVariant.Dark
                } else {
                    ClashThemeVariant.Light
                }
            DarkMode.ForceLight -> ClashThemeVariant.Light
            DarkMode.ForceDark -> ClashThemeVariant.Dark
            DarkMode.AlwaysSummer -> ClashThemeVariant.Summer
        }
    }
}
