package dev.omalauncher.droidspaces.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.omalauncher.droidspaces.KernelFeatureChecker
import dev.omalauncher.ui.Mono
import dev.omalauncher.ui.Palette
import dev.omalauncher.ui.TextSize
import dev.omalauncher.ui.frame

/**
 * Screen displaying kernel compatibility check results for DroidSpaces native container.
 * Shows each required kernel feature with ✓ or ✗ indicator using the launcher's theme.
 *
 * @param features List of kernel features to display
 * @param isRootAvailable Whether root access was granted
 * @param onContinue Called when user taps Continue (all required features available)
 * @param onBack Called when user taps Back
 */
@Composable
fun KernelCheckScreen(
    features: List<KernelFeatureChecker.KernelFeature>,
    isRootAvailable: Boolean,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val allRequiredMet = isRootAvailable && features.filter { it.required }.all { it.available }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
    ) {
        BasicText(
            text = "Kernel Compatibility",
            style = Mono.copy(fontSize = TextSize.Title, fontWeight = FontWeight.Bold),
        )

        Spacer(modifier = Modifier.height(8.dp))

        BasicText(
            text = "DroidSpaces requires these kernel features for native Linux containers.",
            style = Mono.copy(fontSize = TextSize.Small, color = Palette.foreground.copy(alpha = 0.6f)),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Root status row
        KernelFeatureRow(
            name = "Root Access",
            description = "Required for kernel namespace operations",
            available = isRootAvailable,
            required = true,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Kernel features list (scrollable)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (feature in features) {
                KernelFeatureRow(
                    name = feature.name,
                    description = feature.description,
                    available = feature.available,
                    required = feature.required,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!allRequiredMet) {
            BasicText(
                text = "⚠ Some required kernel features are missing. DroidSpaces cannot run on this device. The launcher will use PRoot mode instead.",
                style = Mono.copy(fontSize = TextSize.Small, color = Palette.red),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Outlined back button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .frame(1.dp, Palette.border, Palette.surface, radius = 14.dp)
                    .clickable(onClick = onBack)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("← Back", style = Mono.copy(fontSize = TextSize.Item, fontWeight = FontWeight.Medium))
            }

            // Primary continue button (disabled state uses muted colours)
            val continueLabel = if (allRequiredMet) "Continue" else "Not Compatible"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .frame(
                        0.dp,
                        if (allRequiredMet) Palette.accent else Palette.border,
                        if (allRequiredMet) Palette.accent else Palette.surface,
                        radius = 14.dp,
                    )
                    .then(if (allRequiredMet) Modifier.clickable(onClick = onContinue) else Modifier)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    continueLabel,
                    style = Mono.copy(
                        fontSize = TextSize.Item,
                        fontWeight = FontWeight.Bold,
                        color = if (allRequiredMet) Palette.background else Palette.foreground.copy(alpha = 0.4f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun KernelFeatureRow(
    name: String,
    description: String,
    available: Boolean,
    required: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .frame(1.dp, Palette.border, Palette.surface, radius = 10.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status glyph
        val glyphColor = when {
            available -> Palette.green
            required -> Palette.red
            else -> Palette.foreground.copy(alpha = 0.5f)
        }
        BasicText(
            text = if (available) "✓" else "✗",
            modifier = Modifier.width(24.dp),
            style = Mono.copy(fontSize = TextSize.Item, fontWeight = FontWeight.Bold, color = glyphColor),
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = name,
                    style = Mono.copy(fontSize = TextSize.Body, fontWeight = FontWeight.Medium),
                )
                if (required) {
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicText(
                        text = "required",
                        style = Mono.copy(fontSize = TextSize.Small, color = Palette.foreground.copy(alpha = 0.5f)),
                    )
                }
            }
            BasicText(
                text = description,
                style = Mono.copy(fontSize = TextSize.Small, color = Palette.foreground.copy(alpha = 0.6f)),
            )
        }
    }
}
