package com.example.photocleanup.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.photocleanup.data.ScanPhase
import com.example.photocleanup.data.ScanState
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.DarkSurface
import com.example.photocleanup.ui.theme.TextSecondary

/**
 * Card component that displays the status of a duplicate scan.
 * Shows different states: queued, scanning with progress, and scan info.
 */
@Composable
fun ScanStatusCard(
    scanState: ScanState,
    onCancelScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = scanState is ScanState.Queued || scanState is ScanState.Scanning,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = AccentPrimary,
                    strokeWidth = 3.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Status text
                Column(modifier = Modifier.weight(1f)) {
                    when (scanState) {
                        is ScanState.Queued -> {
                            Text(
                                text = "Preparing scan...",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        is ScanState.Scanning -> {
                            Text(
                                text = when (scanState.phase) {
                                    ScanPhase.HASHING -> "Scanning photos..."
                                    ScanPhase.COMPARING -> "Finding duplicates..."
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (scanState.phase == ScanPhase.HASHING && scanState.total > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${scanState.current} of ${scanState.total} photos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Progress bar
                            val animatedProgress by animateFloatAsState(
                                targetValue = scanState.progress / 100f,
                                label = "scan_progress"
                            )
                            LinearProgressIndicator(
                                progress = animatedProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = AccentPrimary,
                                trackColor = AccentPrimary.copy(alpha = 0.2f)
                            )
                        }
                        else -> {}
                    }
                }

                // Cancel button
                IconButton(onClick = onCancelScan) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cancel scan",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}
