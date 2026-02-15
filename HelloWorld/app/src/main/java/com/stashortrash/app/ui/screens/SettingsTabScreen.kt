package com.stashortrash.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.stashortrash.app.PhotoCleanupApp
import com.stashortrash.app.R
import com.stashortrash.app.ui.components.AppButton
import com.stashortrash.app.ui.components.ButtonVariant
import com.stashortrash.app.ui.components.DialogButton
import com.stashortrash.app.ui.components.DialogButtonIntent
import com.stashortrash.app.ui.components.PremiumUpsellSheet
import android.app.Activity
import android.widget.Toast
import com.stashortrash.app.data.BillingManager
import com.stashortrash.app.ui.theme.CarbonBlack
import com.stashortrash.app.ui.theme.HoneyBronze
import com.stashortrash.app.ui.theme.HoneyBronzeDim
import com.stashortrash.app.ui.theme.TextSecondary
import com.stashortrash.app.viewmodel.PhotoViewModel

@Composable
fun SettingsTabScreen(
    viewModel: PhotoViewModel,
    modifier: Modifier = Modifier
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showUpsellSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appPreferences = (context.applicationContext as PhotoCleanupApp).appPreferences
    val billingManager = (context.applicationContext as PhotoCleanupApp).billingManager
    val activity = context as Activity
    val billingState by billingManager.billingState.collectAsState()
    val premiumEnabled = appPreferences.isPremium

    // Show feedback when billing state changes
    LaunchedEffect(billingState) {
        when (val state = billingState) {
            is BillingManager.BillingState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                billingManager.resetState()
            }
            is BillingManager.BillingState.PurchaseComplete -> {
                Toast.makeText(context, "Premium unlocked! Thank you!", Toast.LENGTH_SHORT).show()
                billingManager.resetState()
            }
            else -> {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        // Premium card
        if (!premiumEnabled) {
            // Upsell card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HoneyBronzeDim.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, HoneyBronze.copy(alpha = 0.4f))
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

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.premium_settings_upgrade_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Feature list
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsFeatureRow(Icons.Filled.Movie, stringResource(R.string.premium_feature_video_title), stringResource(R.string.premium_feature_video_desc))
                        SettingsFeatureRow(Icons.Filled.Shuffle, stringResource(R.string.premium_feature_shuffle_title), stringResource(R.string.premium_feature_shuffle_desc))
                        SettingsFeatureRow(Icons.Filled.FolderOpen, stringResource(R.string.premium_feature_albums_title), stringResource(R.string.premium_feature_albums_desc))
                        SettingsFeatureRow(Icons.Filled.DocumentScanner, stringResource(R.string.premium_feature_scanner_title), stringResource(R.string.premium_feature_scanner_desc))
                        SettingsFeatureRow(Icons.Filled.BarChart, stringResource(R.string.premium_feature_stats_title), stringResource(R.string.premium_feature_stats_desc))
                    }

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

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.premium_one_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        } else {
            // Thank-you card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = HoneyBronze.copy(alpha = 0.1f)
                )
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
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Reviews",
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

        Spacer(modifier = Modifier.height(24.dp))

        // App version at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 100.dp), // Space for bottom nav
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Stash or Trash v1.0",
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
            onUnlockClick = {
                showUpsellSheet = false
                billingManager.launchPurchase(activity)
            },
            onRestoreClick = {
                showUpsellSheet = false
                billingManager.restorePurchase()
            }
        )
    }
}

@Composable
private fun SettingsFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HoneyBronze,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
