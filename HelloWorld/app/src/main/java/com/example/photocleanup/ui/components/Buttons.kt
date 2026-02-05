package com.example.photocleanup.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.photocleanup.ui.theme.DustyMauve
import com.example.photocleanup.ui.theme.Seagrass
import com.example.photocleanup.ui.theme.TextSecondary

// ===========================================
// Design Tokens
// ===========================================
private val ButtonCornerRadius = 24.dp
private val ButtonMinHeight = 48.dp
private val ButtonIconSize = 18.dp
private const val SecondaryAlpha = 0.15f

// ===========================================
// Enums
// ===========================================

enum class ButtonVariant {
    Primary,
    Secondary
}

enum class ButtonIntent {
    Positive,
    Destructive
}

enum class DialogButtonIntent {
    Neutral,
    Positive,
    Destructive
}

enum class FabIntent {
    Positive,
    Destructive
}

// ===========================================
// AppButton
// ===========================================

/**
 * Primary button component with consistent styling across the app.
 *
 * @param text The button label text
 * @param onClick Callback when the button is clicked
 * @param modifier Optional modifier for additional styling
 * @param variant Primary (solid fill) or Secondary (semi-transparent fill)
 * @param intent Positive (Seagrass) or Destructive (DustyMauve)
 * @param enabled Whether the button is enabled
 * @param leadingIcon Optional icon displayed before the text
 * @param fillWidth Whether the button should fill the available width (default: true)
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    intent: ButtonIntent = ButtonIntent.Positive,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fillWidth: Boolean = true
) {
    val baseColor = when (intent) {
        ButtonIntent.Positive -> Seagrass
        ButtonIntent.Destructive -> DustyMauve
    }

    val containerColor = when (variant) {
        ButtonVariant.Primary -> baseColor
        ButtonVariant.Secondary -> baseColor.copy(alpha = SecondaryAlpha)
    }

    val contentColor = when (variant) {
        ButtonVariant.Primary -> Color.White
        ButtonVariant.Secondary -> baseColor
    }

    val buttonModifier = if (fillWidth) {
        modifier.fillMaxWidth().heightIn(min = ButtonMinHeight)
    } else {
        modifier.heightIn(min = ButtonMinHeight)
    }

    Button(
        onClick = onClick,
        modifier = buttonModifier,
        enabled = enabled,
        shape = RoundedCornerShape(ButtonCornerRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.38f),
            disabledContentColor = contentColor.copy(alpha = 0.38f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonIconSize)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

// ===========================================
// DialogButton
// ===========================================

/**
 * Text button for use in dialogs with colored text based on intent.
 *
 * @param text The button label text
 * @param onClick Callback when the button is clicked
 * @param modifier Optional modifier for additional styling
 * @param intent Neutral (gray), Positive (Seagrass), or Destructive (DustyMauve)
 * @param enabled Whether the button is enabled
 */
@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: DialogButtonIntent = DialogButtonIntent.Neutral,
    enabled: Boolean = true
) {
    val textColor = when (intent) {
        DialogButtonIntent.Neutral -> TextSecondary
        DialogButtonIntent.Positive -> Seagrass
        DialogButtonIntent.Destructive -> DustyMauve
    }

    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else textColor.copy(alpha = 0.38f)
        )
    }
}

// ===========================================
// AppFab
// ===========================================

/**
 * Floating action button with consistent styling.
 *
 * @param onClick Callback when the FAB is clicked
 * @param icon The icon to display
 * @param contentDescription Accessibility description for the icon
 * @param modifier Optional modifier for additional styling
 * @param intent Positive (Seagrass) or Destructive (DustyMauve)
 */
@Composable
fun AppFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    intent: FabIntent = FabIntent.Positive
) {
    val containerColor = when (intent) {
        FabIntent.Positive -> Seagrass
        FabIntent.Destructive -> DustyMauve
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = containerColor
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}
