package com.clkyigit.toraksb.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Telefon koordinatında "yukarı" yönünü gösteren yerçekimi vektörü (m/s²). */
data class Gravity(val x: Double, val y: Double, val z: Double) {
    companion object { val ZERO = Gravity(0.0, 0.0, 0.0) }
}

enum class SensorSource(val label: String) {
    GYRO_FUSION("Jiroskop + ivmeölçer"),
    GRAVITY("Yerçekimi sensörü"),
    ACCELEROMETER("Yalnız ivmeölçer"),
}

/**
 * Yön kaynağı, en iyiden başlayarak:
 *  1. TYPE_GAME_ROTATION_VECTOR: jiroskop + ivmeölçer füzyonu, manyetometre YOK
 *     (BT odasındaki metalden etkilenmez). Hızlı ve titremesiz; yerçekimi yönü
 *     dönüş matrisinin üçüncü satırından alınır.
 *  2. TYPE_GRAVITY
 *  3. Filtreli TYPE_ACCELEROMETER
 * Yaklaşık 100 Hz örneklenir; ekran güncellemesi saniyede ~30 kez ile sınırlanır.
 */
class GravitySensor(context: Context, private val onUpdate: (Gravity) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gravity: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accel: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val source: SensorSource? = when {
        rotation != null -> SensorSource.GYRO_FUSION
        gravity != null -> SensorSource.GRAVITY
        accel != null -> SensorSource.ACCELEROMETER
        else -> null
    }
    private val sensor: Sensor? = rotation ?: gravity ?: accel

    /** Füzyon kaynakları zaten temiz; yalnız ivmeölçerde güçlü filtre gerekir. */
    private val alpha = when (source) {
        SensorSource.GYRO_FUSION -> 0.6
        SensorSource.GRAVITY -> 0.35
        else -> 0.08
    }
    private val r = FloatArray(9)
    private var filtered: DoubleArray? = null
    private var lastEmitNs = 0L

    val available: Boolean get() = sensor != null

    fun start() {
        sensor?.let { manager.registerListener(this, it, 10_000) } // ~100 Hz
    }

    fun stop() {
        manager.unregisterListener(this)
        filtered = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val vx: Double; val vy: Double; val vz: Double
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(r, event.values)
            // Dünyanın "yukarı" ekseninin telefon koordinatındaki karşılığı = R'nin 3. satırı
            vx = r[6] * SensorManager.GRAVITY_EARTH.toDouble()
            vy = r[7] * SensorManager.GRAVITY_EARTH.toDouble()
            vz = r[8] * SensorManager.GRAVITY_EARTH.toDouble()
        } else {
            vx = event.values[0].toDouble(); vy = event.values[1].toDouble(); vz = event.values[2].toDouble()
        }
        val f = filtered
        if (f == null) {
            filtered = doubleArrayOf(vx, vy, vz)
        } else {
            f[0] += alpha * (vx - f[0]); f[1] += alpha * (vy - f[1]); f[2] += alpha * (vz - f[2])
        }
        if (event.timestamp - lastEmitNs >= 33_000_000L) {
            lastEmitNs = event.timestamp
            val o = filtered!!
            onUpdate(Gravity(o[0], o[1], o[2]))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

class GravityState(val gravity: State<Gravity>, val available: Boolean, val source: SensorSource?) {
    /** Eski çağrılar için: füzyonlu (temiz) kaynak mı. */
    val fused: Boolean get() = source != SensorSource.ACCELEROMETER
}

@Composable
fun rememberGravity(): GravityState {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Gravity.ZERO) }
    val sensor = remember { GravitySensor(context) { state.value = it } }
    DisposableEffect(sensor) {
        sensor.start()
        onDispose { sensor.stop() }
    }
    return remember { GravityState(state, sensor.available, sensor.source) }
}
