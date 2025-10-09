package dev.isaacudy.udytils.android

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import dev.enro.NavigationKey
import dev.enro.complete
import dev.enro.ui.NavigationDestinationProvider
import dev.enro.ui.navigationDestination
import dev.enro.ui.scenes.directOverlay
import kotlin.reflect.KClass


class ActivityResultParameters<I, O : Any, R : Any> internal constructor(
    internal val contract: ActivityResultContract<I, out O?>,
    internal val input: I,
    internal val outputToResult: (O) -> R
)

fun <I, O : Any> ActivityResultContract<I, out O?>.withInput(input: I): ActivityResultParameters<I, O, O> =
    ActivityResultParameters(
        contract = this,
        input = input,
        outputToResult = { it }
    )

fun <I, O : Any, R: Any> ActivityResultParameters<I, O, O>.withMappedResult(block: (O) -> R): ActivityResultParameters<I, O, R> =
    ActivityResultParameters(
        contract = contract,
        input = input,
        outputToResult = block
    )

class ActivityResultDestinationScope<T : NavigationKey> internal constructor(
    val instance: NavigationKey.Instance<T>,
    val activity: ComponentActivity,
) {
    val key: T get() = instance.key
}

@dev.enro.annotations.ExperimentalEnroApi
fun <R: Any, Key: NavigationKey.WithResult<R>> activityResultDestination(
    @Suppress("UNUSED_PARAMETER") // used to infer types
    keyType: KClass<Key>,
    block: ActivityResultDestinationScope<Key>.() -> ActivityResultParameters<*, *, R>
): NavigationDestinationProvider<Key> = navigationDestination(
    metadata = { directOverlay() }
) {
    val activity = LocalActivity.current
    requireNotNull(activity)
    require(activity is ComponentActivity)
    val scope = remember {
        ActivityResultDestinationScope(
            instance = navigation.instance,
            activity = activity,
        )
    }
    @Suppress("UNCHECKED_CAST")
    val parameters = remember {
        scope.block() as ActivityResultParameters<Any, Any, R>
    }

    val synchronousResult = remember {
        parameters.contract.getSynchronousResult(activity, parameters.input)
    }
    if (synchronousResult != null) {
        LaunchedEffect(Unit) {
            navigation.complete(parameters.outputToResult(synchronousResult))
        }
        return@navigationDestination
    }

    val launched = rememberSaveable { mutableStateOf(false) }
    val resultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        navigation.complete(
            parameters.outputToResult(result)
        )
    }
    LaunchedEffect(Unit) {
        if (launched.value) return@LaunchedEffect
        resultLauncher.launch(
            parameters.contract.createIntent(activity, parameters.input)
        )
        launched.value = true
    }
}
