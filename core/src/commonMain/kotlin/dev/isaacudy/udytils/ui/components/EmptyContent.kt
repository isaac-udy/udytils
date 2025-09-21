package dev.isaacudy.udytils.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

object EmptyContent {
    @Composable
    fun Progress(
        progress: Float? = null,
        title: String,
        subtitle: String? = null,
        buttonText: String? = null,
        onButtonClick: (() -> Unit)? = null
    ) {
        EmptyContent(
            icon = {
                if (progress != null) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress ?: 0f,
                        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        progress = { animatedProgress },
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            title = { Text(title) },
            subtitle = subtitle?.let {
                { Text(it) }
            },
            button = buttonText?.let { buttonText ->
                {
                    Button(
                        onClick = {
                            onButtonClick?.invoke()
                        }
                    ) {
                        Text(buttonText)
                    }
                }
            }
        )
    }

    @Composable
    fun Icon(
        icon: ImageVector,
        iconTint: Color = LocalContentColor.current.copy(alpha = 0.6f),
        title: String,
        subtitle: String? = null,
        buttonText: String? = null,
        onButtonClick: (() -> Unit)? = null
    ) {
        EmptyContent(
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text(title) },
            subtitle = subtitle?.let {
                { Text(it) }
            },
            button = buttonText?.let { buttonText ->
                {
                    Button(
                        onClick = {
                            onButtonClick?.invoke()
                        }
                    ) {
                        Text(buttonText)
                    }
                }
            }
        )
    }

    @Composable
    fun Default(
        title: String,
        subtitle: String? = null,
        buttonText: String? = null,
        onButtonClick: (() -> Unit)? = null
    ) {
        EmptyContent(
            icon = null,
            title = { Text(title) },
            subtitle = subtitle?.let {
                { Text(it) }
            },
            button = buttonText?.let { buttonText ->
                {
                    Button(
                        onClick = {
                            onButtonClick?.invoke()
                        }
                    ) {
                        Text(buttonText)
                    }
                }
            }
        )
    }
}

@Composable
fun EmptyContent(
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    button: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.33f))
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.height(16.dp))
            }
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                LocalTextStyle provides MaterialTheme.typography.headlineSmall,
            ) {
                title()
            }
            if (subtitle != null) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    subtitle()
                }
            }
            if (button != null) {
                Spacer(modifier = Modifier.height(24.dp))
                button()
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
