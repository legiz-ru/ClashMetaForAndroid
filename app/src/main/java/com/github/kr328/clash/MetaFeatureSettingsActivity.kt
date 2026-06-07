package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.github.kr328.clash.util.GetContentCompat
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.MetaFeatureSettingsDesign
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.withClash
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.github.kr328.clash.design.R


class MetaFeatureSettingsActivity : BaseActivity<MetaFeatureSettingsDesign>() {
    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        val design = MetaFeatureSettingsDesign(
            this,
            configuration
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        MetaFeatureSettingsDesign.Request.ResetOverride -> {
                            if (design.requestResetConfirm()) {
                                defer {
                                    withClash {
                                        clearOverride(Clash.OverrideSlot.Persist)
                                    }
                                }
                                finish()
                            }
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoIp -> {
                            val uri = startActivityForResult(
                                GetContentCompat(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportGeoIp)
                        }
                        MetaFeatureSettingsDesign.Request.ImportGeoSite -> {
                            val uri = startActivityForResult(
                                GetContentCompat(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportGeoSite)
                        }
                        MetaFeatureSettingsDesign.Request.ImportCountry -> {
                            val uri = startActivityForResult(
                                GetContentCompat(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportCountry)
                        }
                        MetaFeatureSettingsDesign.Request.ImportASN -> {
                            val uri = startActivityForResult(
                                GetContentCompat(),
                                "*/*")
                            importGeoFile(uri, MetaFeatureSettingsDesign.Request.ImportASN)
                        }
                        MetaFeatureSettingsDesign.Request.GenerateAgeKeyPair -> {
                            withContext(Dispatchers.Main) {
                                showAgeKeypairDialog()
                            }
                        }
                    }
                }
            }
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

        // Initial generation
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

    private suspend fun importGeoFile(uri: Uri?, importType: MetaFeatureSettingsDesign.Request) {
        val cursor: Cursor? = uri?.let {
            contentResolver.query(it, null, null, null, null, null)
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    if (columnIndex != -1) it.getString(columnIndex) else "";
                val ext = "." + displayName.substringAfterLast(".")

                if (!validDatabaseExtensions.contains(ext)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.geofile_unknown_db_format)
                        .setMessage(getString(R.string.geofile_unknown_db_format_message,
                            validDatabaseExtensions.joinToString("/")))
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
                    return
                }
                val outputFileName = when (importType) {
                    MetaFeatureSettingsDesign.Request.ImportGeoIp ->
                        "geoip$ext"
                    MetaFeatureSettingsDesign.Request.ImportGeoSite ->
                        "geosite$ext"
                    MetaFeatureSettingsDesign.Request.ImportCountry ->
                        "country$ext"
                    MetaFeatureSettingsDesign.Request.ImportASN ->
                        "ASN$ext"
                    else -> ""
                }

                withContext(Dispatchers.IO) {
                    val outputFile = File(clashDir, outputFileName);
                    contentResolver.openInputStream(uri).use { ins ->
                        FileOutputStream(outputFile).use { outs ->
                            ins?.copyTo(outs)
                        }
                    }
                }
                Toast.makeText(this, getString(R.string.geofile_imported, displayName),
                    Toast.LENGTH_LONG).show()
                return
            }
        }
        Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    }
}
