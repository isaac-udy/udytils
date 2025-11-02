package dev.isaacudy.udytils.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha

/**
 * Applying this modifier to a Composable will cause that Composable to animate it's alpha
 * according to the [animationSpec] whenever the Composable enters the Composition for the
 * first time.
 */
fun Modifier.animateAlphaOnEnter(
    animationSpec: AnimationSpec<Float> = tween(durationMillis = 125),
): Modifier {
    return composed {
        val target = rememberSaveable { mutableStateOf(false) }
        val alpha = animateFloatAsState(
            targetValue = if (target.value) 1f else 0f,
            animationSpec = animationSpec,
        )
        SideEffect { target.value = true }
        alpha(
            alpha = alpha.value,
        )
    }
}