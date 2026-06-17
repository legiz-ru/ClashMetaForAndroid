package com.github.kr328.clash.design.compose.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (profile.active) BorderStroke(2.dp, scheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: sync (when updatable) + name
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profile.imported && profile.type != Profile.Type.File) {
                    // Keep one continuous 0..360 loop running and apply it only
                    // while updating. Driving rotate() off a conditional target
                    // made the icon spin *backwards* toward 0 when an update
                    // finished mid-rotation; this avoids that.
                    val transition = rememberInfiniteTransition(label = "profileSync")
                    val spin by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "profileSyncAngle",
                    )
                    CardActionIcon(
                        icon = R.drawable.ic_baseline_sync,
                        contentDescription = stringResource(R.string.update),
                        onClick = onUpdate,
                        modifier = Modifier.rotate(if (updating) spin else 0f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (profile.download >= 2) {
                InfoRow(
                    icon = R.drawable.ic_mdi_chart_timeline_variant,
                    label = stringResource(R.string.traffic_used),
                    value = (profile.download + profile.upload).toBytesString(),
                    topMargin = 12.dp,
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

            // Bottom action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
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
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .padding(start = 4.dp)
            .size(32.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
