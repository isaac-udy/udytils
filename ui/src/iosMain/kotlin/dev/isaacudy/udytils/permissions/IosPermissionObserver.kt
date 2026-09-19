package dev.isaacudy.udytils.permissions

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreLocation.CLAccuracyAuthorization
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal actual fun permissionChanges(): Flow<Unit> = IosPermissionObserver.changes

/** Retains native managers and their weak delegates for the lifetime of permission observation. */
@OptIn(ExperimentalForeignApi::class)
internal object IosPermissionObserver {
    private val revision = MutableStateFlow(0L)
    val changes: Flow<Unit> = revision.map { }
    private var upgradeAfterWhenInUse = false

    private val locationDelegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            changed()
            if (!upgradeAfterWhenInUse) return
            when (manager.authorizationStatus) {
                kCLAuthorizationStatusAuthorizedWhenInUse -> {
                    upgradeAfterWhenInUse = false
                    dispatch_async(dispatch_get_main_queue()) { manager.requestAlwaysAuthorization() }
                }
                kCLAuthorizationStatusDenied,
                kCLAuthorizationStatusRestricted,
                kCLAuthorizationStatusAuthorizedAlways -> upgradeAfterWhenInUse = false
            }
        }
    }

    // Permission checks and requests originate on the UI thread, giving Core Location a main run loop.
    private val locationManager by lazy {
        CLLocationManager().apply { delegate = locationDelegate }
    }

    private val bluetoothDelegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            changed()
        }
    }
    private var bluetoothManager: CBCentralManager? = null

    fun locationState(): IosLocationPermissionState = IosLocationPermissionState(
        authorization = when (locationManager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedAlways -> IosLocationPermissionState.Authorization.Always
            kCLAuthorizationStatusAuthorizedWhenInUse -> IosLocationPermissionState.Authorization.WhenInUse
            kCLAuthorizationStatusDenied -> IosLocationPermissionState.Authorization.Denied
            kCLAuthorizationStatusRestricted -> IosLocationPermissionState.Authorization.Restricted
            else -> IosLocationPermissionState.Authorization.NotDetermined
        },
        precise = locationManager.accuracyAuthorization == CLAccuracyAuthorization.CLAccuracyAuthorizationFullAccuracy,
    )

    fun requestLocation(permission: Permission.Location) {
        when (locationState().request(permission)) {
            IosLocationPermissionState.Request.WhenInUse -> {
                upgradeAfterWhenInUse = permission.requireBackground
                locationManager.requestWhenInUseAuthorization()
            }
            IosLocationPermissionState.Request.Always -> locationManager.requestAlwaysAuthorization()
            // The request destination provides Settings even when iOS silently ignores an
            // Always upgrade after Allow Once or Keep Only While Using.
            IosLocationPermissionState.Request.Settings,
            IosLocationPermissionState.Request.None -> Unit
        }
    }

    fun requestBluetooth() {
        if (bluetoothManager == null) {
            bluetoothManager = CBCentralManager(bluetoothDelegate, dispatch_get_main_queue())
        }
    }

    fun changed() {
        revision.update { it + 1 }
    }
}
