package dev.isaacudy.udytils.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A Material3 [Card] with standard content padding, laying out [content] in a [Column].
 *
 * Applies 16dp horizontal / 12dp vertical inner padding, plus 1dp of vertical outer padding so
 * adjacent cards in a list don't visually touch. Use it for freeform card content; prefer
 * [ListCard] for the leading/title/subtitle/trailing row pattern.
 *
 * ```
 * ContentCard(modifier = Modifier.fillMaxWidth()) {
 *     HeadlineText.Small("Account")
 *     BodyText("Manage your account settings")
 * }
 * ```
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val modifierWithPadding = Modifier
        .padding(vertical = 1.dp)
        .then(modifier)
    Card(
        modifier = modifierWithPadding,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
        ) {
            content()
        }
    }
}