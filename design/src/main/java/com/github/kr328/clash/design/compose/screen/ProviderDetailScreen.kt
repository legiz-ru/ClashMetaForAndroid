package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R

/**
 * Single provider's rule list (1:1 with ProviderDetailDesign, restyled to
 * MD3E). Rule-type providers get an in-list search with match navigation and
 * highlighting; non-inline providers get a toolbar update action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    title: String,
    rules: List<String>,
    loading: Boolean,
    updating: Boolean,
    searchEnabled: Boolean,
    updateEnabled: Boolean,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(rules, query) {
        if (query.isBlank()) emptyList()
        else rules.indices.filter { rules[it].contains(query, ignoreCase = true) }
    }
    var current by remember(matches) { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(current, matches) {
        if (matches.isNotEmpty()) {
            listState.animateScrollToItem(matches[current.coerceIn(0, matches.size - 1)])
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
                        if (updateEnabled) {
                            if (updating) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp)
                                        .size(24.dp),
                                )
                            } else {
                                IconButton(onClick = onUpdate) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_baseline_sync),
                                        contentDescription = stringResource(R.string.update),
                                    )
                                }
                            }
                        }
                    },
                )
            },
        ) { inner ->
            Column(modifier = Modifier.padding(inner).fillMaxSize()) {
                if (searchEnabled && !loading) {
                    SearchRow(
                        query = query,
                        onQueryChange = { query = it },
                        counter = when {
                            query.isBlank() -> ""
                            matches.isEmpty() -> "0"
                            else -> "${current.coerceIn(0, matches.size - 1) + 1}/${matches.size}"
                        },
                        navEnabled = matches.isNotEmpty(),
                        onPrev = { if (matches.isNotEmpty()) current = (current - 1 + matches.size) % matches.size },
                        onNext = { if (matches.isNotEmpty()) current = (current + 1) % matches.size },
                    )
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(strokeWidth = 2.dp)
                        rules.isEmpty() -> Text(
                            text = stringResource(R.string.empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> {
                            val currentPos = if (matches.isNotEmpty()) matches[current.coerceIn(0, matches.size - 1)] else -1
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                itemsIndexed(rules) { index, rule ->
                                    RuleEntryRow(
                                        text = rule,
                                        query = query,
                                        isCurrent = index == currentPos,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    counter: String,
    navEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_search),
                    contentDescription = null,
                )
            },
            placeholder = { Text(stringResource(R.string.search)) },
            modifier = Modifier.weight(1f),
        )
        if (counter.isNotEmpty()) {
            Text(
                text = counter,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconButton(onClick = onPrev, enabled = navEnabled) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_expand_more),
                contentDescription = null,
                modifier = Modifier.rotate(180f),
            )
        }
        IconButton(onClick = onNext, enabled = navEnabled) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_expand_more),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun RuleEntryRow(text: String, query: String, isCurrent: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val highlighted = remember(text, query) { highlight(text, query, scheme.primary) }
    Text(
        text = highlighted,
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) scheme.secondaryContainer else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

private fun highlight(text: String, query: String, color: Color): AnnotatedString =
    buildAnnotatedString {
        if (query.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        val lower = text.lowercase()
        val q = query.lowercase()
        var i = 0
        while (true) {
            val idx = lower.indexOf(q, i)
            if (idx < 0) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, idx))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                append(text.substring(idx, idx + q.length))
            }
            i = idx + q.length
        }
    }
