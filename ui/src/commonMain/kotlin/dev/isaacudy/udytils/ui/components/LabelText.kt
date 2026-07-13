package dev.isaacudy.udytils.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color


/**
 * [Text] presets for the Material3 label typography scale — small utility text such as captions,
 * badges and section labels.
 *
 * Invoke the object directly for the medium size, or call [Large] / [Medium] / [Small]
 * explicitly. Color defaults to the current content color. See also [BodyText] and
 * [HeadlineText].
 *
 * ```
 * LabelText("Last result: none")
 * LabelText.Small("v2.4.1")
 * ```
 */
object LabelText {

    /** Renders [text] in `labelMedium`; equivalent to [Medium]. */
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
            style = MaterialTheme.typography.labelLarge,
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
            style = MaterialTheme.typography.labelMedium,
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
            style = MaterialTheme.typography.labelSmall,
            modifier = modifier,
            color = color,
        )
    }
}
