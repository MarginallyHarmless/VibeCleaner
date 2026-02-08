package com.example.photocleanup.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocleanup.PhotoCleanupApp
import com.example.photocleanup.R
import com.example.photocleanup.ui.components.AppButton
import com.example.photocleanup.ui.components.ButtonVariant
import com.example.photocleanup.ui.components.DialogButton
import com.example.photocleanup.ui.components.DialogButtonIntent
import com.example.photocleanup.ui.components.PremiumUpsellSheet
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.CarbonBlack
import com.example.photocleanup.ui.theme.HoneyBronze
import com.example.photocleanup.ui.theme.HoneyBronzeDim
import com.example.photocleanup.ui.theme.TextSecondary
import com.example.photocleanup.viewmodel.PhotoViewModel

@Composable
fun SettingsTabScreen(
    viewModel: PhotoViewModel,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showUpsellSheet by remember { mutableStateOf(false) }
    val appPreferences = (LocalContext.current.applicationContext as PhotoCleanupApp).appPreferences
    var premiumEnabled by remember { mutableStateOf(appPreferences.isPremium) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = "Settings",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Reset Reviews Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "\uD83D\uDD04",
                        fontSize = 24.sp
                    )
                    Text(
                        text = " Reset Reviews",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Clear all your review history and start fresh. This will not restore any deleted photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                AppButton(
                    text = "Reset All Reviews",
                    onClick = { showResetDialog = true },
                    variant = ButtonVariant.Secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Premium card
        if (!premiumEnabled) {
            // Upsell card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HoneyBronzeDim.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, HoneyBronze.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = HoneyBronze,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.premium_settings_upgrade_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = HoneyBronze
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.premium_settings_upgrade_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showUpsellSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HoneyBronze,
                            contentColor = CarbonBlack
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.premium_unlock_button),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Thank-you card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HoneyBronze.copy(alpha = 0.1f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = HoneyBronze,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = stringResource(R.string.premium_settings_unlocked_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = HoneyBronze
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.premium_settings_unlocked_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // DEV: Premium toggle (temporary — remove before release, wire to IAP)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = "Premium Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Unlock paid features (video support, etc.). Restart app after toggling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = premiumEnabled,
                    onCheckedChange = {
                        premiumEnabled = it
                        appPreferences.isPremium = it
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentPrimary)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // App version at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 100.dp), // Space for bottom nav
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Clean My Photos v1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Reviews?") },
            text = {
                Text("This will clear your review history and show all photos again for review. Deleted photos will not be restored.")
            },
            confirmButton = {
                DialogButton(
                    text = "Reset",
                    onClick = {
                        viewModel.resetAllReviews()
                        showResetDialog = false
                    },
                    intent = DialogButtonIntent.Positive
                )
            },
            dismissButton = {
                DialogButton(
                    text = "Cancel",
                    onClick = { showResetDialog = false },
                    intent = DialogButtonIntent.Neutral
                )
            }
        )
    }

    if (showUpsellSheet) {
        PremiumUpsellSheet(
            onDismiss = { showUpsellSheet = false },
            onUnlockClick = { showUpsellSheet = false },
            onRestoreClick = { showUpsellSheet = false }
        )
    }
}
