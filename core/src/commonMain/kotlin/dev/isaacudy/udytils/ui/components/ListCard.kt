package dev.isaacudy.udytils.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ListCard(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val modifierWithPadding = Modifier
        .padding(vertical = 1.dp)
        .then(modifier)
    when {
        onClick != null -> {
            Card(
                modifier = modifierWithPadding,
                enabled = enabled,
                elevation = elevation,
                border = border,
                colors = colors,
                onClick = onClick,
            ) {
                ListCardContent(
                    leading = leading,
                    title = title,
                    subtitle = subtitle,
                    trailing = trailing,
                )
            }
        }

        else -> {
            Card(
                modifier = modifierWithPadding,
                colors = when {
                    enabled -> colors
                    else -> colors.copy(
                        containerColor = colors.disabledContainerColor,
                        contentColor = colors.disabledContentColor,
                    )
                },
                elevation = elevation,
                border = border,
            ) {
                ListCardContent(
                    leading = leading,
                    title = title,
                    subtitle = subtitle,
                    trailing = trailing,
                )
            }
        }
    }
}

@Composable
private fun ListCardContent(
    leading: (@Composable RowScope.() -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .heightIn(
                min = 56.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> Row(
                modifier = Modifier
                    .padding(
                        start = 8.dp,
                        end = 14.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    )
                    .sizeIn(
                        minWidth = 24.dp,
                        minHeight = 56.dp,
                        maxHeight = 56.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading.invoke(this)
            }

            else -> Spacer(modifier = Modifier.size(16.dp))
        }
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .weight(1f),
        ) {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.titleMedium,
            ) {
                title()
            }
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
            ) {
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    subtitle.invoke()
                }
            }
        }
        when {
            trailing != null -> Row(
                modifier = Modifier
                    .padding(
                        start = 8.dp,
                        end = 8.dp,
                    )
                    .sizeIn(
                        minWidth = 24.dp,
                        minHeight = 56.dp,
                        maxHeight = 56.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                trailing.invoke(this)
            }

            else -> Spacer(modifier = Modifier.size(16.dp))
        }
    }
}