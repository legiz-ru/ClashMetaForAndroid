@file:Suppress("BlockingMethodInNonBlockingContext")

package com.github.kr328.clash

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.grantPermissions
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.common.util.uuid
import com.github.kr328.clash.design.FilesDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.FilesScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.dialog.requestModelTextInput
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.util.ValidatorFileName
import com.github.kr328.clash.remote.FilesClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.fileName
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import java.util.*
import java.util.concurrent.TimeUnit

class FilesActivity : BaseActivity<FilesDesign>() {
    private val filesFlow = MutableStateFlow<List<File>>(emptyList())
    private val inBaseFlow = MutableStateFlow(true)
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private val requests = Channel<FilesDesign.Request>(Channel.UNLIMITED)

    override suspend fun main() {
        val uuid = intent.uuid ?: return finish()
        val profile = withProfile { queryByUUID(uuid) } ?: return finish()
        val root = uuid.toString()

        val client = FilesClient(this)
        val stack = Stack<String>()
        val configurationEditable = profile.type != Profile.Type.Url

        fetch(client, stack, root)

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val files by filesFlow.collectAsStateWithLifecycle()
                val inBaseDir by inBaseFlow.collectAsStateWithLifecycle()
                val now by nowFlow.collectAsStateWithLifecycle()
                FilesScreen(
                    files = files,
                    inBaseDir = inBaseDir,
                    configurationEditable = configurationEditable,
                    now = now,
                    onBack = { requests.trySend(FilesDesign.Request.PopStack) },
                    onOpenFile = { requests.trySend(FilesDesign.Request.OpenFile(it)) },
                    onOpenDirectory = { requests.trySend(FilesDesign.Request.OpenDirectory(it)) },
                    onNew = { requests.trySend(FilesDesign.Request.ImportFile(null)) },
                    onRename = { requests.trySend(FilesDesign.Request.RenameFile(it)) },
                    onImport = { requests.trySend(FilesDesign.Request.ImportFile(it)) },
                    onExport = { requests.trySend(FilesDesign.Request.ExportFile(it)) },
                    onDelete = { requests.trySend(FilesDesign.Request.DeleteFile(it)) },
                )
            }
        }

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ActivityStop -> {
                            fetch(client, stack, root)
                        }
                        else -> Unit
                    }
                }
                requests.onReceive {
                    try {
                        when (it) {
                            FilesDesign.Request.PopStack -> {
                                if (stack.empty()) {
                                    finish()
                                } else {
                                    stack.pop()
                                }
                            }
                            is FilesDesign.Request.OpenDirectory -> {
                                stack.push(it.file.id)
                            }
                            is FilesDesign.Request.OpenFile -> {
                                startActivityForResult(
                                    ActivityResultContracts.StartActivityForResult(),
                                    Intent(Intent.ACTION_VIEW).setDataAndType(
                                        client.buildDocumentUri(it.file.id),
                                        "text/plain"
                                    ).grantPermissions()
                                )
                            }
                            is FilesDesign.Request.DeleteFile -> {
                                client.deleteDocument(it.file.id)
                            }
                            is FilesDesign.Request.RenameFile -> {
                                val newName = requestFileName(it.file.name)

                                client.renameDocument(it.file.id, newName)
                            }
                            is FilesDesign.Request.ImportFile -> {
                                val uri: Uri? = startActivityForResult(
                                    com.github.kr328.clash.util.GetContentCompat(),
                                    "*/*"
                                )

                                if (uri != null) {
                                    if (it.file == null) {
                                        val name = requestFileName(uri.fileName ?: "File")

                                        client.importDocument(stack.last(), uri, name)
                                    } else {
                                        client.copyDocument(it.file!!.id, uri)
                                    }
                                }
                            }
                            is FilesDesign.Request.ExportFile -> {
                                val uri: Uri? = startActivityForResult(
                                    ActivityResultContracts.CreateDocument("text/plain"),
                                    it.file.name
                                )

                                if (uri != null) {
                                    client.copyDocument(uri, it.file.id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@FilesActivity, e.message ?: "Unknown", Toast.LENGTH_LONG).show()
                    }

                    fetch(client, stack, root)
                }
                if (activityStarted) {
                    ticker.onReceive {
                        nowFlow.value = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        requests.trySend(FilesDesign.Request.PopStack)
    }

    private suspend fun requestFileName(name: String): String {
        return requestModelTextInput(
            initial = name,
            title = getText(R.string.file_name),
            hint = getText(R.string.file_name),
            error = getText(R.string.invalid_file_name),
            validator = ValidatorFileName,
        )
    }

    private suspend fun fetch(client: FilesClient, stack: Stack<String>, root: String) {
        val documentId = stack.lastOrNull() ?: root
        val files = if (stack.empty()) {
            val list = client.list(documentId)
            val config = list.firstOrNull { it.id.endsWith("config.yaml") }

            if (config == null || config.size > 0) list else listOf(config)
        } else {
            client.list(documentId)
        }

        filesFlow.value = files
        inBaseFlow.value = stack.empty()
        nowFlow.value = System.currentTimeMillis()
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
