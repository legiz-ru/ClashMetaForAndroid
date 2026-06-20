package com.github.kr328.clash.design.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.component.AddProfileAction
import com.github.kr328.clash.design.compose.component.AddProfileSheet
import com.github.kr328.clash.design.compose.component.AppNavigationRail
import com.github.kr328.clash.design.compose.component.GhostPowerButton
import com.github.kr328.clash.design.compose.component.ProxyGroupCard
import com.github.kr328.clash.design.compose.component.ProxySelectionSheet
import com.github.kr328.clash.design.compose.component.RemoteIcon
import com.github.kr328.clash.design.compose.component.SimpleModeProxyList
import com.github.kr328.clash.design.compose.component.verticalScrollbar
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.core.model.ProxyGroup
import com.github.kr328.clash.service.model.Profile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen (stage 5a). Mirrors the static `design_main.xml`: header, loading,
 * onboarding, the active-profile card, the ghost power button (disconnected) and,
 * while running, a floating toolbar (disconnect + latency test) plus an entry to
 * the proxy selector. The inline proxy-group accordion is handled in a follow-up.
 */
@Composable
fun MainScreen(
    expanded: Boolean,
    clashRunning: Boolean,
    isLoading: Boolean,
    hasProfiles: Boolean,
    activeProfile: Profile?,
    appTitle: String,
    appLogoUrl: String,
    latencyTesting: Boolean,
    proxyGroups: List<Pair<String, ProxyGroup>>,
    useDots: Boolean,
    onPowerToggle: () -> Unit,
    onUpdateProfile: () -> Unit,
    onManageProfiles: () -> Unit,
    onModeSelector: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenSupport: (String) -> Unit,
    onOpenWebPage: (String) -> Unit,
    onAdd: (AddProfileAction) -> Unit,
    onNavigate: (SettingsNavTarget) -> Unit,
    onLatencyTest: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectProxy: (String, String) -> Unit,
    onUrlTest: (String) -> Unit,
    onLogoTap: () -> Unit = {},
    isTv: Boolean = false,
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var openedGroup by remember { mutableStateOf<String?>(null) }
    val simpleMode = activeProfile?.simpleMode == true
    val groupMap = remember(proxyGroups) { proxyGroups.toMap() }
    val visibleGroups = remember(proxyGroups) { proxyGroups.filter { !it.second.hidden } }
    val homeScroll = rememberScrollState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant

    val body: @Composable (Modifier) -> Unit = { contentModifier ->
        Scaffold(
            modifier = contentModifier,
            bottomBar = {
                if (!expanded) {
                    MainBottomBar(
                        clashRunning = clashRunning,
                        simpleMode = simpleMode,
                        latencyTesting = latencyTesting,
                        onNavigate = onNavigate,
                        onDisconnect = onDisconnect,
                        onLatencyTest = onLatencyTest,
                    )
                }
            },
        ) { inner ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollbar(homeScroll, scrollbarColor)
                        .verticalScroll(homeScroll)
                        .padding(
                            top = inner.calculateTopPadding(),
                            bottom = inner.calculateBottomPadding(),
                        )
                        .padding(horizontal = 16.dp),
                ) {
                    Header(appTitle = appTitle, logoUrl = appLogoUrl, onLogoTap = onLogoTap)

                    AnimatedVisibility(
                        visible = isLoading,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(vertical = 64.dp)
                                .size(48.dp),
                        )
                    }

                    if (!hasProfiles && !clashRunning && !isLoading) {
                        Onboarding(onAdd = { showAddSheet = true })
                    }

                    if (hasProfiles && !isLoading) {
                        MainProfileCard(
                            profile = activeProfile,
                            clashRunning = clashRunning,
                            onUpdate = onUpdateProfile,
                            onManage = onManageProfiles,
                            onModeSelector = onModeSelector,
                            onConnections = onOpenConnections,
                            onProviders = onOpenProviders,
                            onSupport = onOpenSupport,
                            onWebPage = onOpenWebPage,
                        )
                    }

                    AnimatedVisibility(visible = hasProfiles && !clashRunning && !isLoading) {
                        PowerSection(onToggle = onPowerToggle)
                    }

                    AnimatedVisibility(
                        visible = clashRunning,
                        // Drop in from the top (like the power button), instead of
                        // the default bottom-anchored expand that read diagonally.
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        Column {
                            if (simpleMode && visibleGroups.isNotEmpty()) {
                                val (firstName, firstGroup) = visibleGroups.first()
                                SimpleModeProxyList(
                                    group = firstGroup,
                                    groupMap = groupMap,
                                    useDots = useDots,
                                    onSelect = { proxy -> onSelectProxy(firstName, proxy) },
                                )
                            } else {
                                for ((name, group) in visibleGroups) {
                                    ProxyGroupCard(
                                        name = name,
                                        group = group,
                                        groupMap = groupMap,
                                        onClick = { openedGroup = name },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
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
                    selected = SettingsNavTarget.Home,
                    clashRunning = clashRunning,
                    onNavigate = onNavigate,
                    onToggleStatus = onPowerToggle,
                    hasProfiles = hasProfiles,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    body(Modifier.fillMaxSize())
                }
            }
        } else {
            body(Modifier.fillMaxSize())
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

    val opened = openedGroup
    val openedData = opened?.let { groupMap[it] }
    if (opened != null && openedData != null) {
        ProxySelectionSheet(
            groupName = opened,
            group = openedData,
            groupMap = groupMap,
            useDots = useDots,
            isTv = isTv,
            onSelect = { proxy -> onSelectProxy(opened, proxy) },
            onUrlTest = { onUrlTest(opened) },
            onDismiss = { openedGroup = null },
        )
    }
}

@Composable
private fun Header(appTitle: String, logoUrl: String, onLogoTap: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Hidden easter egg: tapping the logo 15× unlocks the "Always Summer"
        // theme. Suppress the ripple so it stays invisible.
        val logoModifier = Modifier
            .size(64.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLogoTap,
            )
        val defaultLogo: @Composable () -> Unit = {
            Icon(
                painter = painterResource(R.drawable.ic_clash),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = logoModifier,
            )
        }
        if (logoUrl.isNotEmpty()) {
            RemoteIcon(url = logoUrl, modifier = logoModifier, fallback = defaultLogo)
        } else {
            defaultLogo()
        }
        Text(
            text = appTitle,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun Onboarding(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.welcome),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.add_first_profile),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 12.dp),
        )
        // Same muted circular style as the (stopped) ghost power button.
        Box(
            modifier = Modifier
                .padding(top = 48.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_add),
                contentDescription = stringResource(R.string.add_profile),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
        }
        Text(
            text = stringResource(R.string.add_profile),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun PowerSection(onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GhostPowerButton(running = false, onClick = onToggle, size = 100.dp)
        Text(
            text = stringResource(R.string.tap_to_start),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/**
 * Floating bottom bar: a Home/Settings nav pill, flanked while running by a red
 * square "stop" button (right) and, for simple-mode profiles, a square
 * latency-test button (left). Inspired by the June app's floating pill nav.
 */
@Composable
private fun MainBottomBar(
    clashRunning: Boolean,
    simpleMode: Boolean,
    latencyTesting: Boolean,
    onNavigate: (SettingsNavTarget) -> Unit,
    onDisconnect: () -> Unit,
    onLatencyTest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(visible = clashRunning && simpleMode) {
            Row {
                SquareBarButton(
                    onClick = { if (!latencyTesting) onLatencyTest() },
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    if (latencyTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_speed),
                            contentDescription = stringResource(R.string.delay_test),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
        }

        NavPill(selected = SettingsNavTarget.Home, onNavigate = onNavigate)

        AnimatedVisibility(visible = clashRunning) {
            Row {
                Spacer(modifier = Modifier.width(12.dp))
                SquareBarButton(
                    onClick = onDisconnect,
                    container = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mdi_power),
                        contentDescription = stringResource(R.string.disconnect),
                    )
                }
            }
        }
    }
}

@Composable
private fun SquareBarButton(
    onClick: () -> Unit,
    container: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = contentColor,
        shadowElevation = 3.dp,
        modifier = Modifier.size(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun MainProfileCard(
    profile: Profile?,
    clashRunning: Boolean,
    onUpdate: () -> Unit,
    onManage: () -> Unit,
    onModeSelector: () -> Unit,
    onConnections: () -> Unit,
    onProviders: () -> Unit,
    onSupport: (String) -> Unit,
    onWebPage: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: sync + name + manage
            Row(verticalAlignment = Alignment.CenterVertically) {
                MainCardIcon(R.drawable.ic_baseline_sync, stringResource(R.string.update), onUpdate)
                Text(
                    text = profile?.name ?: stringResource(R.string.profile),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                MainCardIcon(
                    R.drawable.ic_mdi_account_cog_outline,
                    stringResource(R.string.manage_profiles),
                    onManage,
                )
            }

            val hasTraffic = profile != null && profile.total > 1
            val hasExpire = profile != null && profile.expire > 0

            if (hasTraffic && profile != null) {
                MainInfoRow(
                    R.drawable.ic_mdi_chart_timeline_variant,
                    stringResource(R.string.traffic_used),
                    (profile.download + profile.upload).toBytesString(),
                    topMargin = 16.dp,
                )
                MainInfoRow(
                    R.drawable.ic_mdi_database_check,
                    stringResource(R.string.traffic_available),
                    profile.total.toBytesString(),
                )
            }
            if (hasExpire && profile != null) {
                val expireText = remember(profile.expire) {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(profile.expire))
                }
                MainInfoRow(R.drawable.ic_mdi_calendar_alert, stringResource(R.string.expires), expireText)
            }
            if (profile != null) {
                val updatedText = (System.currentTimeMillis() - profile.updatedAt)
                    .elapsedIntervalString(androidx.compose.ui.platform.LocalContext.current)
                MainInfoRow(R.drawable.ic_mdi_update, stringResource(R.string.updated), updatedText)
            }

            // Link icons row
            val supportUrl = profile?.supportUrl.orEmpty()
            val webPageUrl = profile?.profileWebPageUrl.orEmpty()
            val showShortcuts = clashRunning &&
                (profile?.globalModeMp == true || profile?.connsViewMp == true || profile?.rpMp == true)
            if (supportUrl.isNotEmpty() || webPageUrl.isNotEmpty() || showShortcuts) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (clashRunning && profile?.globalModeMp == true) {
                        MainCardIcon(R.drawable.ic_mdi_earth, null, onModeSelector)
                    }
                    if (clashRunning && profile?.connsViewMp == true) {
                        MainCardIcon(R.drawable.ic_mdi_transit_connection_variant, null, onConnections)
                    }
                    if (clashRunning && profile?.rpMp == true) {
                        MainCardIcon(R.drawable.ic_mdi_database, null, onProviders)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (supportUrl.isNotEmpty()) {
                        MainCardIcon(R.drawable.ic_mdi_face_agent, null, { onSupport(supportUrl) })
                    }
                    if (webPageUrl.isNotEmpty()) {
                        MainCardIcon(R.drawable.ic_mdi_home_import_outline, null, { onWebPage(webPageUrl) })
                    }
                }
            }

            val announce = profile?.announce.orEmpty().replace("\\n", "\n")
            if (announce.isNotEmpty()) {
                Text(
                    text = announce,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun MainInfoRow(
    icon: Int,
    label: String,
    value: String,
    topMargin: androidx.compose.ui.unit.Dp = 8.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun MainCardIcon(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
