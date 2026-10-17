package com.clkyigit.toraksb.sensor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMAttitudeReferenceFrameXArbitraryZVertical
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue

/**
 * CoreMotion cihaz hareketi: jiroskop + ivmeölçer füzyonu, manyetometre YOK
 * (xArbitraryZVertical başvuru çerçevesi). Eksenler Android ile aynı: x sağ, y üst kenar, z ekrandan dışarı.
 * CoreMotion yerçekimi AŞAĞI yönü g biriminde verir; uygulama YUKARI yönü m/s² olarak bekler.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberGravity(): GravityState {
    val state = remember { mutableStateOf(Gravity.ZERO) }
    val manager = remember { CMMotionManager() }
    val available = manager.deviceMotionAvailable
    DisposableEffect(manager) {
        if (manager.deviceMotionAvailable) {
            manager.deviceMotionUpdateInterval = 1.0 / 60.0
            manager.startDeviceMotionUpdatesUsingReferenceFrame(
                CMAttitudeReferenceFrameXArbitraryZVertical,
                NSOperationQueue.mainQueue,
            ) { motion, _ ->
                motion?.gravity?.useContents {
                    state.value = Gravity(-x * 9.81, -y * 9.81, -z * 9.81)
                }
            }
        }
        onDispose { manager.stopDeviceMotionUpdates() }
    }
    return remember { GravityState(state, available, if (available) SensorSource.GYRO_FUSION else null) }
}
