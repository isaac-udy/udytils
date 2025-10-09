package dev.isaacudy.udytils.ui.destinations

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import dev.enro.NavigationKey
import dev.enro.annotations.NavigationDestination
import dev.enro.complete
import dev.enro.requestClose
import dev.enro.ui.navigationDestination
import dev.enro.ui.scenes.directOverlay
import dev.isaacudy.udytils.ui.generated.resources.Res
import dev.isaacudy.udytils.ui.generated.resources.close
import dev.isaacudy.udytils.ui.generated.resources.confirm
import dev.isaacudy.udytils.ui.StringOrResource
import dev.isaacudy.udytils.ui.asString
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

@Serializable
object ConfirmationDestination {

    operator fun invoke(
        title: StringOrResource,
        message: StringOrResource? = null,
        confirmText: StringOrResource = StringOrResource(Res.string.confirm),
        dismissText: StringOrResource = StringOrResource(Res.string.close),
        destructive: Boolean = false,
    ): Default {
        return Default(
            title = title,
            message = message,
            confirmText = confirmText,
            dismissText = dismissText,
            destructive = destructive,
        )
    }

    operator fun invoke(
        title: StringResource,
        message: StringResource? = null,
        confirmText: StringResource = Res.string.confirm,
        dismissText: StringResource = Res.string.close,
        destructive: Boolean = false,
    ): Default {
        return invoke(
            title = StringOrResource(title),
            message = message?.let { StringOrResource(it) },
            confirmText = StringOrResource(confirmText),
            dismissText = StringOrResource(dismissText),
            destructive = destructive,
        )
    }


    operator fun invoke(
        title: String,
        message: String? = null,
        confirmText: String? = null,
        dismissText: String? = null,
        destructive: Boolean = false,
    ): Default {
        return invoke(
            title = StringOrResource(title),
            message = message?.let { StringOrResource(it) },
            confirmText = when {
                confirmText != null -> StringOrResource(confirmText)
                else -> StringOrResource(Res.string.confirm)
            },
            dismissText = when {
                dismissText != null -> StringOrResource(dismissText)
                else -> StringOrResource(Res.string.close)
            },
            destructive = destructive,
        )
    }

    @Serializable
    @ConsistentCopyVisibility
    data class Default internal constructor(
        val title: StringOrResource,
        val message: StringOrResource? = null,
        val confirmText: StringOrResource = StringOrResource(Res.string.confirm),
        val dismissText: StringOrResource = StringOrResource(Res.string.close),
        val destructive: Boolean = false,
    ) : NavigationKey
}

@NavigationDestination(ConfirmationDestination.Default::class)
val defaultConfirmationDestination = navigationDestination<ConfirmationDestination.Default>(
    metadata = {
        directOverlay()
    }
) {
    AlertDialog(
        onDismissRequest = {
            navigation.requestClose()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    navigation.complete()
                }
            ) {
                Text(
                    text = navigation.key.confirmText.asString(),
                    color = when {
                        navigation.key.destructive -> MaterialTheme.colorScheme.error
                        else -> Color.Unspecified
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    navigation.requestClose()
                }
            ) {
                Text(
                    text = navigation.key.dismissText.asString(),
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.66f
                    ),
                )
            }
        },
        title = {
            Text(
                text = navigation.key.title.asString(),
            )
        },
        text = when (val text = navigation.key.message) {
            null -> null
            else -> {
                {
                    Text(
                        text = text.asString(),
                    )
                }
            }
        },
    )
}