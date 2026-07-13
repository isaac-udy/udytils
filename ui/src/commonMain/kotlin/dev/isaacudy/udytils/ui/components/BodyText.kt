package dev.isaacudy.udytils.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color


/**
 * [Text] presets for the Material3 body typography scale.
 *
 * Invoke the object directly for the medium size, or call [Large] / [Medium] / [Small]
 * explicitly. Color defaults to the current content color. See also [HeadlineText] and
 * [LabelText].
 *
 * ```
 * BodyText("Saved to your library")
 * BodyText.Small("Last updated 5 minutes ago")
 * ```
 */
object BodyText {

    /** Renders [text] in `bodyMedium`; equivalent to [Medium]. */
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
