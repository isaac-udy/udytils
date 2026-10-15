package dev.isaacudy.udytils.ui.html

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The CSS classes the udytils Compose HTML destinations put on each element. The defaults are
 * `udytils-*` names; provide your design system's classes through [LocalUdytilsHtmlClasses].
 *
 * ```
 * CompositionLocalProvider(
 *     LocalUdytilsHtmlClasses provides UdytilsHtmlClasses(
 *         title = listOf("app-title"),
 *         confirmButton = listOf("app-button", "app-button-primary"),
 *     ),
 * ) {
 *     HtmlNavigationDisplay(container)
 * }
 * ```
 */
data class UdytilsHtmlClasses(
    val dialogContent: List<String> = listOf("udytils-dialog-content"),
    val title: List<String> = listOf("udytils-title"),
    val body: List<String> = listOf("udytils-body"),
    val caption: List<String> = listOf("udytils-caption"),
    val actions: List<String> = listOf("udytils-dialog-actions"),
    val confirmButton: List<String> = listOf("udytils-button", "udytils-button-confirm"),
    val destructiveButton: List<String> = listOf("udytils-button", "udytils-button-destructive"),
    val dismissButton: List<String> = listOf("udytils-button", "udytils-button-dismiss"),
)

val LocalUdytilsHtmlClasses = staticCompositionLocalOf { UdytilsHtmlClasses() }
