package com.github.kr328.clash

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setFileName
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.LogsScreen
import com.github.kr328.clash.design.compose.theme.ClashTheme
import com.github.kr328.clash.design.compose.theme.ClashThemeVariant
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.util.logsDir
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class LogsActivity : BaseActivity() {
    private val filesFlow = MutableStateFlow<List<LogFile>>(emptyList())
    private val deleteRequests = Channel<Unit>(Channel.CONFLATED)

    override suspend fun main() {
        setContent {
            ClashTheme(variant = currentThemeVariant()) {
                val files by filesFlow.collectAsStateWithLifecycle()
                LogsScreen(
                    files = files,
                    onBack = { finish() },
                    onStartLogcat = {
                        startActivity(LogcatActivity::class.intent)
                        finish()
                    },
                    onDeleteAll = { deleteRequests.trySend(Unit) },
                    onOpenFile = {
                        startActivity(LogcatActivity::class.intent.setFileName(it.fileName))
                    },
                )
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    if (it == Event.ActivityStart) {
                        filesFlow.value = withContext(Dispatchers.IO) { loadFiles() }
                    }
                }
                deleteRequests.onReceive {
                    if (requestDeleteAll()) {
                        withContext(Dispatchers.IO) { deleteAllLogs() }
                        filesFlow.value = withContext(Dispatchers.IO) { loadFiles() }
                    }
                }
            }
        }
    }

    private suspend fun requestDeleteAll(): Boolean {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { ctx ->
                MaterialAlertDialogBuilder(this@LogsActivity)
                    .setTitle(R.string.delete_all_logs)
                    .setMessage(R.string.delete_all_logs_warn)
                    .setPositiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                    .setNegativeButton(R.string.cancel) { _, _ -> }
                    .show()
                    .setOnDismissListener { if (!ctx.isCompleted) ctx.resume(false) }
            }
        }
    }

    private fun loadFiles(): List<LogFile> {
        val list = cacheDir.resolve("logs").listFiles()?.toList() ?: emptyList()
        return list.mapNotNull { LogFile.parseFromFileName(it.name) }
    }

    private fun deleteAllLogs() {
        logsDir.deleteRecursively()
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
