package dev.omalauncher.droidspaces.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.omalauncher.ui.Mono
import dev.omalauncher.ui.Palette
import dev.omalauncher.ui.TextSize

/**
 * Small pill badge shown on the launcher to indicate the active Linux backend.
 *
 * Uses the theme's [Palette.green] for a running native DroidSpaces container,
 * [Palette.accent] for PRoot, and [Palette.border] when Linux is not running.
 *
 * @param isDroidSpaces True when the native DroidSpaces backend is active; false for PRoot.
 * @param isRunning Whether any Linux backend is currently running.
 * @param modifier Optional modifier applied to the badge row.
 */
@Composable
fun BackendStatusBadge(
    isDroidSpaces: Boolean,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val (badgeColor, label) = when {
        !isRunning -> Palette.border to "Linux: Off"
        isDroidSpaces -> Palette.green to "🐧 Native"
        else -> Palette.accent to "🐧 PRoot"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isRunning) badgeColor else Palette.border),
        )
        BasicText(
            text = label,
            style = Mono.copy(
                fontSize = TextSize.Small,
                fontWeight = FontWeight.Medium,
                color = badgeColor,
            ),
        )
    }
}
