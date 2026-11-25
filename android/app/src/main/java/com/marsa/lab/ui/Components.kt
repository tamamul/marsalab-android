package com.marsa.lab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (online) MarsaColors.Online else MarsaColors.Muted)
    )
}

@Composable
fun ErrorBanner(message: String, stale: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            if (stale) {
                Text(
                    "Data di bawah mungkin sudah usang.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: Int, modifier: Modifier = Modifier, accent: androidx.compose.ui.graphics.Color = MarsaColors.TextMain) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MarsaColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, MarsaColors.Outline),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value.toString(), color = accent, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = MarsaColors.Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun RowGap(w: Int = 8) { Spacer(Modifier.width(w.dp)) }

@Composable
fun StatusLabel(online: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(online)
        RowGap(6)
        Text(
            if (online) "Online" else "Offline",
            color = if (online) MarsaColors.Online else MarsaColors.Muted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
