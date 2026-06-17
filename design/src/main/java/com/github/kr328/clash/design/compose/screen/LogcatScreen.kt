package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.format

/**
 * Log message viewer (1:1 with LogcatDesign, restyled to MD3E).
 * Streaming mode shows a stop action and follows the tail; a saved file shows
 * delete + export. Long-press a line to copy it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogcatScreen(
    title: String,
    messages: List<LogMessage>,
    streaming: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onCopy: (LogMessage) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (streaming && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (streaming) {
                            IconButton(onClick = onClose) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_stop),
                                    contentDescription = stringResource(R.string.stop),
                                )
                            }
                        } else {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_delete),
                                    contentDescription = stringResource(R.string.delete),
                                )
                            }
                            IconButton(onClick = onExport) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_publish),
                                    contentDescription = stringResource(R.string.export),
                                )
                            }
                        }
                    },
                )
            },
        ) { inner ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
            ) {
                items(items = messages) { message ->
                    LogMessageRow(message = message, onCopy = { onCopy(message) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LogMessageRow(message: LogMessage, onCopy: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onCopy)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message.level.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = message.time.format(context, includeDate = false, includeTime = true),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = message.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
