package com.github.kr328.clash.design.compose.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.AddProfileAction
import com.github.kr328.clash.design.compose.component.AddProfileSheet
import com.github.kr328.clash.design.compose.component.AppNavigationRail
import com.github.kr328.clash.design.compose.component.ProfileCard
import com.github.kr328.clash.service.model.Profile
import java.util.UUID

/**
 * Profiles screen. Kept visually close to the View-based `design_profiles.xml`:
 * a top bar (back / update-all / add) over a scrollable list of profile cards.
 * On tablets/TV a side navigation rail replaces the implicit top-level navigation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    expanded: Boolean,
    clashRunning: Boolean,
    isTv: Boolean,
    profiles: List<Profile>,
    now: Long,
    updatingUuids: Set<UUID>,
    allUpdating: Boolean,
    onBack: () -> Unit,
    onUpdateAll: () -> Unit,
    onAdd: (AddProfileAction) -> Unit,
    onProfileClick: (Profile) -> Unit,
    onProfileUpdate: (Profile) -> Unit,
    onProfileAnnounce: (Profile) -> Unit,
    onProfileSupport: (Profile) -> Unit,
    onProfileWebPage: (Profile) -> Unit,
    onProfileEdit: (Profile) -> Unit,
    onProfileDelete: (Profile) -> Unit,
    onNavigate: (SettingsNavTarget) -> Unit,
    onToggleStatus: () -> Unit,
) {
    var showAddSheet by remember { mutableStateOf(false) }
    val updatable = remember(profiles) {
        profiles.any { it.imported && it.type != Profile.Type.File }
    }

    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        Scaffold(
            modifier = contentModifier,
            topBar = {
                ProfilesTopBar(
                    updatable = updatable,
                    allUpdating = allUpdating,
                    onBack = onBack,
                    onUpdateAll = onUpdateAll,
                    onAdd = { showAddSheet = true },
                )
            },
        ) { inner ->
            LazyColumn(
                contentPadding = PaddingValues(
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = inner.calculateBottomPadding() + 8.dp,
                ),
            ) {
                items(items = profiles, key = { it.uuid }) { profile ->
                    ProfileCard(
                        profile = profile,
                        now = now,
                        updating = profile.uuid in updatingUuids,
                        onClick = { onProfileClick(profile) },
                        onUpdate = { onProfileUpdate(profile) },
                        onAnnounce = { onProfileAnnounce(profile) },
                        onSupport = { onProfileSupport(profile) },
                        onWebPage = { onProfileWebPage(profile) },
                        onEdit = { onProfileEdit(profile) },
                        onDelete = { onProfileDelete(profile) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                AppNavigationRail(
                    selected = SettingsNavTarget.Profiles,
                    clashRunning = clashRunning,
                    onNavigate = onNavigate,
                    onToggleStatus = onToggleStatus,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    content(Modifier.fillMaxSize())
                }
            }
        } else {
            content(Modifier.fillMaxSize())
        }
    }

    if (showAddSheet) {
        AddProfileSheet(
            isTv = isTv,
            onAction = {
                showAddSheet = false
                onAdd(it)
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilesTopBar(
    updatable: Boolean,
    allUpdating: Boolean,
    onBack: () -> Unit,
    onUpdateAll: () -> Unit,
    onAdd: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.profiles)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_arrow_back),
                    contentDescription = null,
                )
            }
        },
        actions = {
            if (updatable) {
                val transition = rememberInfiniteTransition(label = "updateAll")
                val angle by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = if (allUpdating) 360f else 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "updateAllAngle",
                )
                IconButton(onClick = onUpdateAll) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_sync),
                        contentDescription = stringResource(R.string.update),
                        modifier = Modifier.rotate(angle),
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_add),
                    contentDescription = stringResource(R.string._new),
                )
            }
        },
    )
}
