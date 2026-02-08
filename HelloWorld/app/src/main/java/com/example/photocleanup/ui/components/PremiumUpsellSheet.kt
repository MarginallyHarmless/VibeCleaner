package com.example.photocleanup.ui.components

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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocleanup.R
import com.example.photocleanup.ui.theme.CarbonBlack
import com.example.photocleanup.ui.theme.DarkSurface
import com.example.photocleanup.ui.theme.HoneyBronze
import com.example.photocleanup.ui.theme.TextPrimary
import com.example.photocleanup.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumUpsellSheet(
    onDismiss: () -> Unit,
    onUnlockClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = HoneyBronze,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.premium_unlock_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.premium_unlock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature list
            FeatureRow(
                icon = Icons.Filled.Movie,
                title = stringResource(R.string.premium_feature_video_title),
                description = stringResource(R.string.premium_feature_video_desc)
            )
            FeatureRow(
                icon = Icons.Filled.Shuffle,
                title = stringResource(R.string.premium_feature_shuffle_title),
                description = stringResource(R.string.premium_feature_shuffle_desc)
            )
            FeatureRow(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(R.string.premium_feature_albums_title),
                description = stringResource(R.string.premium_feature_albums_desc)
            )
            FeatureRow(
                icon = Icons.Filled.DocumentScanner,
                title = stringResource(R.string.premium_feature_scanner_title),
                description = stringResource(R.string.premium_feature_scanner_desc)
            )
            FeatureRow(
                icon = Icons.Filled.BarChart,
                title = stringResource(R.string.premium_feature_stats_title),
                description = stringResource(R.string.premium_feature_stats_desc)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Unlock button
            Button(
                onClick = onUnlockClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HoneyBronze,
                    contentColor = CarbonBlack
                )
            ) {
                Text(
                    text = stringResource(R.string.premium_unlock_button),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.premium_one_time),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Restore purchase
            TextButton(onClick = onRestoreClick) {
                Text(
                    text = stringResource(R.string.premium_restore),
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HoneyBronze,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontSize = 15.sp
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}
