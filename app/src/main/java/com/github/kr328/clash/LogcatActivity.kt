package com.github.kr328.clash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.IBinder
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.compat.startForegroundServiceCompat
import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.common.util.fileName
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.LogcatScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.dialog.withModelProgressBar
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.util.format
import com.github.kr328.clash.log.LogcatFilter
import com.github.kr328.clash.log.LogcatReader
import com.github.kr328.clash.util.logsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LogcatActivity : BaseActivity() {
    private var conn: ServiceConnection? = null
    private val messagesFlow = MutableStateFlow<List<LogMessage>>(emptyList())

    override suspend fun main() {
        val fileName = intent?.fileName

        if (fileName != null) {
            val file = LogFile.parseFromFileName(fileName) ?: return showInvalid()
            return mainLocalFile(file)
        }

        return mainStreaming()
    }

    private suspend fun mainLocalFile(file: LogFile) {
        val messages = try {
            LogcatReader(this, file).readAll()
        } catch (e: Exception) {
            Log.e("Fail to read log file ${file.fileName}: ${e.message}")
            return showInvalid()
        }

        messagesFlow.value = messages
        val title = file.date.format(this)

        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val msgs by messagesFlow.collectAsStateWithLifecycle()
                LogcatScreen(
                    title = title,
                    messages = msgs,
                    streaming = false,
                    onBack = { finish() },
                    onClose = {},
                    onDelete = { launch { deleteLocal(file) } },
                    onExport = { launch { exportLocal(messages, file) } },
                    onCopy = ::copyMessage,
                )
            }
        }

        while (isActive) {
            events.receive()
        }
    }

    private suspend fun deleteLocal(file: LogFile) {
        withContext(Dispatchers.IO) {
            logsDir.resolve(file.fileName).delete()
        }
        finish()
    }

    private suspend fun exportLocal(messages: List<LogMessage>, file: LogFile) {
        val output = startActivityForResult(
            ActivityResultContracts.CreateDocument("text/plain"),
            file.fileName
        ) ?: return
        try {
            withContext(Dispatchers.IO) {
                writeLogTo(messages, file, output)
            }
            Toast.makeText(this, R.string.file_exported, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun mainStreaming() {
        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val msgs by messagesFlow.collectAsStateWithLifecycle()
                LogcatScreen(
                    title = getString(R.string.logs),
                    messages = msgs,
                    streaming = true,
                    onBack = { closeStreaming() },
                    onClose = { closeStreaming() },
                    onDelete = {},
                    onExport = {},
                    onCopy = ::copyMessage,
                )
            }
        }

        startForegroundServiceCompat(LogcatService::class.intent)

        val logcat = bindLogcatService()
        val ticker = ticker(500)

        var initial = true

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                if (activityStarted) {
                    ticker.onReceive {
                        val snapshot = logcat.snapshot(initial) ?: return@onReceive
                        messagesFlow.value = snapshot.messages
                        initial = false
                    }
                }
            }
        }
    }

    private fun closeStreaming() {
        stopService(LogcatService::class.intent)
        startActivity(LogsActivity::class.intent)
        finish()
    }

    private fun copyMessage(message: LogMessage) {
        val data = ClipData.newPlainText("log_message", message.message)
        getSystemService<ClipboardManager>()?.setPrimaryClip(data)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        conn?.apply(this::unbindService)
        super.onDestroy()
    }

    private suspend fun bindLogcatService(): LogcatService {
        return suspendCoroutine { ctx ->
            bindService(LogcatService::class.intent, object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val srv = service!!.queryLocalInterface("") as LogcatService
                    ctx.resume(srv)
                    conn = this
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    conn = null
                }
            }, Context.BIND_AUTO_CREATE)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun writeLogTo(messages: List<LogMessage>, file: LogFile, uri: Uri) {
        LogcatFilter(OutputStreamWriter(contentResolver.openOutputStream(uri)), this).use {
            withContext(Dispatchers.Main) {
                withModelProgressBar {
                    configure {
                        isIndeterminate = true
                        max = messages.size
                    }

                    withContext(Dispatchers.IO) {
                        it.writeHeader(file.date)

                        messages.forEachIndexed { idx, msg ->
                            configure {
                                isIndeterminate = false
                                progress = idx
                            }

                            it.writeMessage(msg)
                        }
                    }
                }
            }
        }
    }

    private fun showInvalid() {
        Toast.makeText(this, R.string.invalid_log_file, Toast.LENGTH_LONG).show()
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
