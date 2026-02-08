package com.example.photocleanup.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.photocleanup.ui.components.AppButton
import com.example.photocleanup.ui.components.ButtonVariant
import com.example.photocleanup.ui.theme.AccentPrimary
import com.example.photocleanup.ui.theme.AccentPrimaryDim
import com.example.photocleanup.ui.theme.DarkBackground
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Android 13+: request both image and video permissions
        val permissionsState = rememberMultiplePermissionsState(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        )

        LaunchedEffect(permissionsState.allPermissionsGranted) {
            if (permissionsState.allPermissionsGranted) {
                onPermissionGranted()
            }
        }

        val shouldShowRationale = permissionsState.shouldShowRationale

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "\uD83D\uDCF7", fontSize = 72.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Let's Clean Up!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (shouldShowRationale) {
                        "Clean My Photos needs access to your photos and videos to help you review and organize them. Please grant permission to continue."
                    } else {
                        "To tidy up your photo and video chaos, we'll need a backstage pass to your library. Don't worry, your camera roll secrets are safe with us!"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(48.dp))
                AppButton(
                    text = "Grant Permission",
                    onClick = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
        }
    } else {
        // Android 12-: single permission (READ_EXTERNAL_STORAGE covers both)
        val permissionState = rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

        LaunchedEffect(permissionState.status) {
            if (permissionState.status.isGranted) {
                onPermissionGranted()
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "\uD83D\uDCF7", fontSize = 72.sp)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Let's Clean Up!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (permissionState.status.shouldShowRationale) {
                        "Clean My Photos needs access to your photos and videos to help you review and organize them. Please grant permission to continue."
                    } else {
                        "To tidy up your photo and video chaos, we'll need a backstage pass to your library. Don't worry, your camera roll secrets are safe with us!"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(48.dp))
                AppButton(
                    text = "Grant Permission",
                    onClick = { permissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

/**
 * Composable that prompts the user to grant full storage access (MANAGE_EXTERNAL_STORAGE).
 * This allows moving photos between albums without showing a confirmation dialog for each photo.
 * Only shown on Android 11+ devices.
 */
@Composable
fun FullStorageAccessPrompt(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Only show on Android 11+
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return
    }

    // Don't show if already granted
    if (Environment.isExternalStorageManager()) {
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = AccentPrimaryDim.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Quick Photo Organization",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AccentPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Grant full storage access to move photos between albums without confirmation dialogs.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppButton(
                text = "Open Settings",
                onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            AppButton(
                text = "Close",
                onClick = onDismiss,
                variant = ButtonVariant.Secondary
            )
        }
    }
}

/**
 * Check if the app has full storage access on Android 11+.
 */
fun hasFullStorageAccess(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}
