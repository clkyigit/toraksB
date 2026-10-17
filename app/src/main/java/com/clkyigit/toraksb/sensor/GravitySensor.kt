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

/**
 * TYPE_GRAVITY (ivmeölçer + jiroskop füzyonu) varsa onu, yoksa düşük geçiren filtreli
 * ivmeölçeri kullanır. Manyetometre kullanılmaz: BT odasında güvenilmez.
 */
class GravitySensor(context: Context, private val onUpdate: (Gravity) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gravitySensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val sensor: Sensor? = gravitySensor ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val alpha = if (gravitySensor != null) 0.25 else 0.08
    private var filtered: DoubleArray? = null

    val available: Boolean get() = sensor != null
    val usingFusedGravity: Boolean get() = gravitySensor != null

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
        filtered = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val f = filtered
        if (f == null) {
            filtered = doubleArrayOf(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
        } else {
            for (i in 0..2) f[i] += alpha * (v[i] - f[i])
        }
        val r = filtered!!
        onUpdate(Gravity(r[0], r[1], r[2]))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

class GravityState(val gravity: State<Gravity>, val available: Boolean, val fused: Boolean)

@Composable
fun rememberGravity(): GravityState {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Gravity.ZERO) }
    val sensor = remember { GravitySensor(context) { state.value = it } }
    DisposableEffect(sensor) {
        sensor.start()
        onDispose { sensor.stop() }
    }
    return remember { GravityState(state, sensor.available, sensor.usingFusedGravity) }
}
