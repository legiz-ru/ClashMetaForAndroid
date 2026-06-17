package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.util.format

/**
 * Logs file list (1:1 with LogsDesign, restyled to MD3E). Toolbar actions:
 * start live logcat (adb icon) and delete all (clear-all icon).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    files: List<LogFile>,
    onBack: () -> Unit,
    onStartLogcat: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpenFile: (LogFile) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.logs)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onStartLogcat) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_adb),
                                contentDescription = stringResource(R.string.logcat),
                            )
                        }
                        IconButton(onClick = onDeleteAll) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_clear_all),
                                contentDescription = stringResource(R.string.delete_all_logs),
                            )
                        }
                    },
                )
            },
        ) { inner ->
            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                ) {
                    items(items = files, key = { it.fileName }) { file ->
                        Box(modifier = Modifier.animateItem()) {
                            LogFileRow(file = file, onClick = { onOpenFile(file) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogFileRow(file: LogFile, onClick: () -> Unit) {
    val context = LocalContext.current
    val subtitle = remember(file) { file.date.format(context) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = file.fileName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
