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

private sealed class FilesRequest {
    data class OpenFile(val file: File) : FilesRequest()
    data class OpenDirectory(val file: File) : FilesRequest()
    data class RenameFile(val file: File) : FilesRequest()
    data class DeleteFile(val file: File) : FilesRequest()
    data class ImportFile(val file: File?) : FilesRequest()
    data class ExportFile(val file: File) : FilesRequest()
    object PopStack : FilesRequest()
}

class FilesActivity : BaseActivity() {
    private val filesFlow = MutableStateFlow<List<File>>(emptyList())
    private val inBaseFlow = MutableStateFlow(true)
    private val nowFlow = MutableStateFlow(System.currentTimeMillis())
    private val requests = Channel<FilesRequest>(Channel.UNLIMITED)

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
                    onBack = { requests.trySend(FilesRequest.PopStack) },
                    onOpenFile = { requests.trySend(FilesRequest.OpenFile(it)) },
                    onOpenDirectory = { requests.trySend(FilesRequest.OpenDirectory(it)) },
                    onNew = { requests.trySend(FilesRequest.ImportFile(null)) },
                    onRename = { requests.trySend(FilesRequest.RenameFile(it)) },
                    onImport = { requests.trySend(FilesRequest.ImportFile(it)) },
                    onExport = { requests.trySend(FilesRequest.ExportFile(it)) },
                    onDelete = { requests.trySend(FilesRequest.DeleteFile(it)) },
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
                            FilesRequest.PopStack -> {
                                if (stack.empty()) {
                                    finish()
                                } else {
                                    stack.pop()
                                }
                            }
                            is FilesRequest.OpenDirectory -> {
                                stack.push(it.file.id)
                            }
                            is FilesRequest.OpenFile -> {
                                startActivityForResult(
                                    ActivityResultContracts.StartActivityForResult(),
                                    Intent(Intent.ACTION_VIEW).setDataAndType(
                                        client.buildDocumentUri(it.file.id),
                                        "text/plain"
                                    ).grantPermissions()
                                )
                            }
                            is FilesRequest.DeleteFile -> {
                                client.deleteDocument(it.file.id)
                            }
                            is FilesRequest.RenameFile -> {
                                val newName = requestFileName(it.file.name)

                                client.renameDocument(it.file.id, newName)
                            }
                            is FilesRequest.ImportFile -> {
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
                            is FilesRequest.ExportFile -> {
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
        requests.trySend(FilesRequest.PopStack)
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
