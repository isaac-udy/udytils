package dev.isaacudy.udytils.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color


object BodyText {

    @Composable
    operator fun invoke(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
    ) {
        Medium(
            text = text,
            modifier = modifier,
            color = color,
        )
    }

    @Composable
    fun Large(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
            color = color,
        )
    }

    @Composable
    fun Medium(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier,
            color = color,
        )
    }


    @Composable
    fun Small(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
            color = color,
        )
    }
}
