package com.github.kr328.clash.design.compose.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.NullableTextAdapter
import com.github.kr328.clash.design.compose.component.TextAdapter

/**
 * Compose equivalents of the View-based editable preferences
 * (editableText / editableTextList / editableTextMap), restyled to MD3 Expressive.
 */

@Composable
fun <T> EditableTextPreference(
    title: String,
    value: T,
    adapter: NullableTextAdapter<T>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    empty: String? = null,
    numeric: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val display = adapter.from(value)
    val summary = when {
        display == null -> placeholder
        display.isEmpty() -> empty
        else -> display
    }
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        TextInputDialog(
            title = title,
            initial = adapter.from(value) ?: "",
            numeric = numeric,
            onConfirm = {
                onValueChange(adapter.to(it))
                showDialog = false
            },
            onReset = {
                onValueChange(adapter.to(null))
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    numeric: Boolean,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = if (numeric) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun <T> EditableListPreference(
    title: String,
    value: List<T>?,
    adapter: TextAdapter<T>,
    onValueChange: (List<T>?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary = when {
        value == null -> placeholder
        value.isEmpty() -> stringResource(R.string.empty)
        else -> stringResource(R.string.format_elements, value.size)
    }
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        ListEditorDialog(
            title = title,
            initial = value?.map { adapter.from(it) } ?: emptyList(),
            onApply = {
                onValueChange(it.map { item -> adapter.to(item) })
                showDialog = false
            },
            onReset = {
                onValueChange(null)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ListEditorDialog(
    title: String,
    initial: List<String>,
    onApply: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = remember { mutableStateListOf<String>().apply { addAll(initial) } }
    var newItem by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { items.removeAt(index) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_outline_delete),
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (newItem.isNotBlank()) {
                                items.add(newItem.trim())
                                newItem = ""
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_add),
                            contentDescription = null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(items.toList()) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun <K, V> EditableMapPreference(
    title: String,
    value: Map<K, V>?,
    keyAdapter: TextAdapter<K>,
    valueAdapter: TextAdapter<V>,
    onValueChange: (Map<K, V>?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val summary = when {
        value == null -> placeholder
        value.isEmpty() -> stringResource(R.string.empty)
        else -> stringResource(R.string.format_elements, value.size)
    }
    PreferenceRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        MapEditorDialog(
            title = title,
            initial = value?.map { keyAdapter.from(it.key) to valueAdapter.from(it.value) }
                ?: emptyList(),
            onApply = { pairs ->
                onValueChange(
                    pairs.associate { keyAdapter.to(it.first) to valueAdapter.to(it.second) },
                )
                showDialog = false
            },
            onReset = {
                onValueChange(null)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun MapEditorDialog(
    title: String,
    initial: List<Pair<String, String>>,
    onApply: (List<Pair<String, String>>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pairs = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(initial) } }
    var newKey by remember { mutableStateOf("") }
    var newValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                pairs.forEachIndexed { index, (k, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$k → $v",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { pairs.removeAt(index) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_outline_delete),
                                contentDescription = stringResource(R.string.delete),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (newKey.isNotBlank() && newValue.isNotBlank()) {
                                pairs.add(newKey.trim() to newValue.trim())
                                newKey = ""
                                newValue = ""
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_add),
                            contentDescription = null,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(pairs.toList()) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.reset)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
