import androidx.compose.ui.window.ComposeUIViewController
import dev.isaacudy.udytils.samples.SamplesApplicationContent
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    return ComposeUIViewController {
        SamplesApplicationContent()
    }
}