import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.application
import dev.enro.close
import dev.enro.platform.desktop.GenericRootWindow
import dev.enro.platform.desktop.RootWindow
import dev.enro.platform.desktop.openWindow
import dev.enro.ui.EnroApplicationContent
import dev.isaacudy.udytils.samples.SamplesApplicationContent
import dev.isaacudy.udytils.samples.SamplesNavigation
import dev.isaacudy.udytils.samples.installNavigationController

fun main() {
    val controller = SamplesNavigation.installNavigationController(Unit)
    controller.openWindow(
        GenericRootWindow(
            windowConfiguration = {
                RootWindow.WindowConfiguration(
                    title = "Udytils",
                    onCloseRequest = { navigation.close() },
                    onKeyEvent = {
                        if (it.type == KeyEventType.KeyDown && it.key == Key.W && it.isMetaPressed) {
                            navigation.close()
                            true
                        }
                        // TODO: wire up a back-navigation keystroke once the Enro API is sorted.
                        false
                    }
                )
            }
        ) {
            MenuBar {
                Menu("Window") {
                    Item(
                        "Close",
                        shortcut = androidx.compose.ui.input.key.KeyShortcut(
                            key = Key.W,
                            meta = true
                        )
                    ) {
                        navigation.close()
                    }
                }
            }
            SamplesApplicationContent()
        }
    )
    application {
        EnroApplicationContent(controller)
    }
}
