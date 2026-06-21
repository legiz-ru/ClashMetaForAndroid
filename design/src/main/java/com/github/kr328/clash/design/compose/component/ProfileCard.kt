package com.github.kr328.clash.design.compose.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.elapsedIntervalString
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDateStr
import com.github.kr328.clash.service.model.Profile

/**
 * A single profile card, kept visually 1:1 with the View-based `adapter_profile.xml`:
 * header (sync + name), traffic/expiry/updated rows, and a bottom action row.
 *
 * @param now        current time in millis, used for the "updated … ago" label
 * @param updating   whether this profile is being refreshed (spins the sync icon)
 */
@Composable
fun ProfileCard(
    profile: Profile,
    now: Long,
    updating: Boolean,
    onClick: () -> Unit,
    onUpdate: () -> Unit,
    onAnnounce: () -> Unit,
    onSupport: () -> Unit,
    onWebPage: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    // Touch: the whole card activates the profile (tap anywhere but the action
    // icons). D-pad: the card-level click is made non-focusable so it does not
    // shadow the inner buttons — the name is the focus target that activates and
    // each icon is its own target.
    val cardSource = remember { MutableInteractionSource() }
    val nameSource = remember { MutableInteractionSource() }
    val nameFocused by nameSource.collectIsFocusedAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (profile.active) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false }
                .clickable(
                    interactionSource = cardSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                )
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Name = the D-pad focus target (also activates).
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (nameFocused) {
                                Modifier.border(3.dp, scheme.primary, RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            interactionSource = nameSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (profile.imported && profile.type != Profile.Type.File) {
                    // Spin only while updating: a fresh 0..360 loop that settles at 0.
                    val rotation = remember { Animatable(0f) }
                    LaunchedEffect(updating) {
                        if (updating) {
                            rotation.snapTo(0f)
                            while (true) {
                                rotation.animateTo(
                                    targetValue = 360f,
                                    animationSpec = tween(1000, easing = LinearEasing),
                                )
                                rotation.snapTo(0f)
                            }
                        } else {
                            rotation.snapTo(0f)
                        }
                    }
                    CardActionIcon(
                        icon = R.drawable.ic_baseline_sync,
                        contentDescription = stringResource(R.string.update),
                        onClick = onUpdate,
                        iconModifier = Modifier.rotate(rotation.value),
                    )
                }
            }

            // Info rows — full width, values hug the right edge.
            if (profile.download >= 2) {
                InfoRow(
                    icon = R.drawable.ic_mdi_chart_timeline_variant,
                    label = stringResource(R.string.traffic_used),
                    value = (profile.download + profile.upload).toBytesString(),
                    topMargin = 8.dp,
                )
            }
            if (profile.total >= 2) {
                InfoRow(
                    icon = R.drawable.ic_mdi_database_check,
                    label = stringResource(R.string.traffic_available),
                    value = profile.total.toBytesString(),
                )
            }
            if (profile.expire != 0L) {
                InfoRow(
                    icon = R.drawable.ic_mdi_calendar_alert,
                    label = stringResource(R.string.expires),
                    value = profile.expire.toDateStr(),
                )
            }
            InfoRow(
                icon = R.drawable.ic_mdi_update,
                label = stringResource(R.string.updated),
                value = (now - profile.updatedAt).elapsedIntervalString(context),
            )

            // Action row — each icon is independently focusable for D-pad.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (profile.active) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_check),
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.weight(1f))

                if (profile.announce.isNotEmpty()) {
                    CardActionIcon(R.drawable.ic_mdi_bullhorn_variant_outline, null, onAnnounce)
                }
                if (profile.supportUrl.isNotEmpty()) {
                    CardActionIcon(R.drawable.ic_mdi_face_agent, null, onSupport)
                }
                if (profile.profileWebPageUrl.isNotEmpty()) {
                    CardActionIcon(R.drawable.ic_mdi_home_import_outline, null, onWebPage)
                }
                CardActionIcon(R.drawable.ic_baseline_edit, stringResource(R.string.edit), onEdit)
                CardActionIcon(R.drawable.ic_baseline_delete, stringResource(R.string.delete), onDelete)
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: Int,
    label: String,
    value: String,
    topMargin: androidx.compose.ui.unit.Dp = 6.dp,
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
private fun CardActionIcon(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .padding(start = 4.dp)
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (focused) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = iconModifier.size(22.dp),
        )
    }
}
