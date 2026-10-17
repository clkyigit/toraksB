package com.clkyigit.toraksb.sensor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/** Telefon koordinatında "yukarı" yönünü gösteren yerçekimi vektörü (m/s²). x sağ, y üst kenar, z ekrandan dışarı. */
data class Gravity(val x: Double, val y: Double, val z: Double) {
    companion object { val ZERO = Gravity(0.0, 0.0, 0.0) }
}

enum class SensorSource(val label: String) {
    GYRO_FUSION("Jiroskop + ivmeölçer"),
    GRAVITY("Yerçekimi sensörü"),
    ACCELEROMETER("Yalnız ivmeölçer"),
}

class GravityState(val gravity: State<Gravity>, val available: Boolean, val source: SensorSource?) {
    /** Füzyonlu (temiz) kaynak mı. */
    val fused: Boolean get() = source != SensorSource.ACCELEROMETER
}

/**
 * Manyetometre kullanmadan (BT odasındaki metalden etkilenmez) jiroskop + ivmeölçer füzyonundan
 * yerçekimi yönü. Android: TYPE_GAME_ROTATION_VECTOR; iOS: CoreMotion (xArbitraryZVertical).
 */
@Composable
expect fun rememberGravity(): GravityState
