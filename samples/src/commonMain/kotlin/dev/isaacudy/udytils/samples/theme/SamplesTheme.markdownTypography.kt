package dev.isaacudy.udytils.samples.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownTypography

@Suppress("UnusedReceiverParameter")
val SamplesTheme.markdownTypography: MarkdownTypography
    @Composable
    @ReadOnlyComposable
    get() {
        val text = MaterialTheme.typography.bodyMedium
        val link = text.copy(
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            color = when {
                isSystemInDarkTheme() -> Color(0xFF4493F8)
                else -> Color(0xFF0969DA)
            },
        )

        return DefaultMarkdownTypography(
            h1 = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            h2 = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            h3 = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            h4 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            h5 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            h6 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            text = text,
            code = text.copy(
                fontSize = text.fontSize.times(0.8f),
                fontFamily = FontFamily.Monospace
            ),
            inlineCode = text.copy(fontFamily = FontFamily.Monospace),
            quote = text.copy(
                fontSize = text.fontSize.times(0.8f),
                fontStyle = FontStyle.Italic,
            ),
            paragraph = text,
            ordered = text,
            bullet = text,
            list = text,
            link = link,
            textLink = TextLinkStyles(style = link.toSpanStyle()),
            table = text,
        )
    }