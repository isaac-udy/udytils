package dev.isaacudy.udytils.android

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import dev.enro.NavigationKey
import dev.enro.annotations.ExperimentalEnroApi
import dev.enro.annotations.NavigationDestination
import kotlinx.serialization.Serializable


@Serializable
data object ApplicationSettingsDestination : NavigationKey.WithResult<Unit>

@OptIn(ExperimentalEnroApi::class)
@NavigationDestination(ApplicationSettingsDestination::class)
val applicationSettingsDestination = activityResultDestination(
    ApplicationSettingsDestination::class
) {
    ActivityResultContracts.StartActivityForResult()
        .withInput(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                val uri = Uri.fromParts("package", activity.packageName, null)
                setData(uri)
            }
        )
        .withMappedResult {}
}