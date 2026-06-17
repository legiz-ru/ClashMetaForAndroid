package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.File
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString

/**
 * In-app file browser (1:1 with FilesDesign, restyled to MD3E). The toolbar "add"
 * action imports a new file and is shown only outside the profile base directory.
 * Per-file operations live in a bottom-sheet menu, mirroring dialog_files_menu.xml.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    files: List<File>,
    inBaseDir: Boolean,
    configurationEditable: Boolean,
    now: Long,
    onBack: () -> Unit,
    onOpenFile: (File) -> Unit,
    onOpenDirectory: (File) -> Unit,
    onNew: () -> Unit,
    onRename: (File) -> Unit,
    onImport: (File) -> Unit,
    onExport: (File) -> Unit,
    onDelete: (File) -> Unit,
) {
    var menuFile by remember { mutableStateOf<File?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.browse_files)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (!inBaseDir) {
                            IconButton(onClick = onNew) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_add),
                                    contentDescription = stringResource(R.string._new),
                                )
                            }
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
                    items(items = files, key = { it.id }) { file ->
                        Box(modifier = Modifier.animateItem()) {
                            FileRow(
                                file = file,
                                now = now,
                                onOpen = {
                                    if (file.isDirectory) onOpenDirectory(file) else onOpenFile(file)
                                },
                                onMore = { menuFile = file },
                            )
                        }
                    }
                }
            }
        }
    }

    menuFile?.let { file ->
        FileMenuSheet(
            file = file,
            inBaseDir = inBaseDir,
            configurationEditable = configurationEditable,
            onDismiss = { menuFile = null },
            onRename = { menuFile = null; onRename(file) },
            onImport = { menuFile = null; onImport(file) },
            onExport = { menuFile = null; onExport(file) },
            onDelete = { menuFile = null; onDelete(file) },
        )
    }
}

@Composable
private fun FileRow(
    file: File,
    now: Long,
    onOpen: () -> Unit,
    onMore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                if (file.isDirectory) R.drawable.ic_outline_folder else R.drawable.ic_outline_article,
            ),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!file.isDirectory) {
                Text(
                    text = file.size.toBytesString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (!file.isDirectory) {
            Text(
                text = (now - file.lastModified).elapsedIntervalString(context),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconButton(onClick = onMore) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_more_vert),
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileMenuSheet(
    file: File,
    inBaseDir: Boolean,
    configurationEditable: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            if (!file.isDirectory && (!inBaseDir || configurationEditable)) {
                FileMenuItem(R.drawable.ic_baseline_get_app, stringResource(R.string.import_), onImport)
            }
            if (!file.isDirectory && file.size > 0) {
                FileMenuItem(R.drawable.ic_baseline_publish, stringResource(R.string.export), onExport)
            }
            if (!inBaseDir) {
                FileMenuItem(R.drawable.ic_baseline_edit, stringResource(R.string.rename), onRename)
                FileMenuItem(
                    icon = R.drawable.ic_outline_delete,
                    text = stringResource(R.string.delete),
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun FileMenuItem(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
        )
    }
}
