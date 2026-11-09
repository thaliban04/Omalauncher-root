package dev.omalauncher.droidspaces.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.omalauncher.droidspaces.DroidSpacesBootstrap
import dev.omalauncher.ui.Mono
import dev.omalauncher.ui.Palette
import dev.omalauncher.ui.TextSize
import dev.omalauncher.ui.frame
import kotlinx.coroutines.delay

/**
 * Shows DroidSpaces installation/setup progress.
 * Integrates with [DroidSpacesBootstrap.DsSetupState] using the launcher's theme.
 *
 * @param state Current setup state from [DroidSpacesBootstrap]
 * @param onRetry Called when user taps Retry after a failure
 * @param onUseProot Called when user opts to fall back to PRoot mode
 */
@Composable
fun DroidSpacesSetupScreen(
    state: DroidSpacesBootstrap.DsSetupState,
    onRetry: () -> Unit,
    onUseProot: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is DroidSpacesBootstrap.DsSetupState.Idle -> {
                BasicText("⚙ Initializing DroidSpaces…", style = Mono.copy(fontSize = TextSize.Large))
                Spacer(modifier = Modifier.height(16.dp))
                BrailleSpinner()
            }

            is DroidSpacesBootstrap.DsSetupState.CheckingRequirements -> {
                BasicText(
                    "Checking kernel requirements…",
                    style = Mono.copy(fontSize = TextSize.Large, fontWeight = FontWeight.Medium),
                )
                Spacer(modifier = Modifier.height(16.dp))
                BrailleSpinner()
            }

            is DroidSpacesBootstrap.DsSetupState.MissingRequirements -> {
                BasicText(
                    text = "⚠ DroidSpaces Not Available",
                    style = Mono.copy(fontSize = TextSize.Title, fontWeight = FontWeight.Bold, color = Palette.red),
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (state.noRoot) {
                    BasicText(
                        "Root access is required for native Linux containers.",
                        style = Mono.copy(color = Palette.foreground.copy(alpha = 0.6f)),
                    )
                } else {
                    BasicText(
                        "Missing kernel features:",
                        style = Mono.copy(fontSize = TextSize.Body, color = Palette.foreground.copy(alpha = 0.6f)),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    for (feature in state.missingFeatures) {
                        BasicText(
                            "• ${feature.name}: ${feature.description}",
                            style = Mono.copy(fontSize = TextSize.Small, color = Palette.red),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                PrimaryDsButton("Use PRoot Mode Instead", onClick = onUseProot)
            }

            is DroidSpacesBootstrap.DsSetupState.DeployingBinary -> {
                BasicText(
                    "Installing DroidSpaces runtime…",
                    style = Mono.copy(fontSize = TextSize.Large, fontWeight = FontWeight.Medium),
                )
                Spacer(modifier = Modifier.height(16.dp))
                DsProgressBar(state.progress)
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    "${(state.progress * 100).toInt()}%",
                    style = Mono.copy(fontSize = TextSize.Small, color = Palette.foreground.copy(alpha = 0.6f)),
                )
            }

            is DroidSpacesBootstrap.DsSetupState.DownloadingRootfs -> {
                BasicText(
                    "Downloading ${state.distro} rootfs…",
                    style = Mono.copy(fontSize = TextSize.Large, fontWeight = FontWeight.Medium),
                )
                Spacer(modifier = Modifier.height(16.dp))
                DsProgressBar(state.progress)
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    "${(state.progress * 100).toInt()}%",
                    style = Mono.copy(fontSize = TextSize.Small, color = Palette.foreground.copy(alpha = 0.6f)),
                )
            }

            is DroidSpacesBootstrap.DsSetupState.InstallingDistro -> {
                BasicText(
                    "Setting up ${state.distro}…",
                    style = Mono.copy(fontSize = TextSize.Large, fontWeight = FontWeight.Medium),
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    state.step,
                    style = Mono.copy(fontSize = TextSize.Small, color = Palette.foreground.copy(alpha = 0.6f)),
                )
                Spacer(modifier = Modifier.height(16.dp))
                BrailleSpinner()
            }

            is DroidSpacesBootstrap.DsSetupState.Ready -> {
                BasicText(
                    "✓ DroidSpaces Ready!",
                    style = Mono.copy(fontSize = TextSize.Title, fontWeight = FontWeight.Bold, color = Palette.green),
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    "Native Linux container is running.",
                    style = Mono.copy(color = Palette.foreground.copy(alpha = 0.6f)),
                )
            }

            is DroidSpacesBootstrap.DsSetupState.Failed -> {
                BasicText(
                    "Setup Failed",
                    style = Mono.copy(fontSize = TextSize.Title, fontWeight = FontWeight.Bold, color = Palette.red),
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicText(
                    state.reason,
                    style = Mono.copy(fontSize = TextSize.Small, color = Palette.foreground.copy(alpha = 0.6f)),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Outlined retry button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .frame(1.dp, Palette.border, Palette.surface, radius = 14.dp)
                            .clickable(onClick = onRetry)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "Retry",
                            style = Mono.copy(fontSize = TextSize.Item, fontWeight = FontWeight.Medium),
                        )
                    }
                    // Filled PRoot button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .frame(0.dp, Palette.accent, Palette.accent, radius = 14.dp)
                            .clickable(onClick = onUseProot)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            "Use PRoot",
                            style = Mono.copy(
                                fontSize = TextSize.Item,
                                fontWeight = FontWeight.Bold,
                                color = Palette.background,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Filled primary action button matching the launcher's PrimaryButton style. */
@Composable
private fun PrimaryDsButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .frame(0.dp, Palette.accent, Palette.accent, radius = 14.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = Mono.copy(color = Palette.background, fontSize = TextSize.Item, fontWeight = FontWeight.Bold),
        )
    }
}

/** Thin animated progress bar using the theme's accent and surface colours. */
@Composable
private fun DsProgressBar(progress: Float) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), label = "ds_progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Palette.surface),
    ) {
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(4.dp)
                .background(Palette.accent),
        )
    }
}

/** A braille spinner matching the launcher's terminal aesthetic. */
@Composable
private fun BrailleSpinner() {
    val frames = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            i = (i + 1) % frames.length
        }
    }
    BasicText(frames[i].toString(), style = Mono.copy(color = Palette.accent, fontWeight = FontWeight.Bold))
}
