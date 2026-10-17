package com.clkyigit.toraksb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clkyigit.toraksb.geometry.TableCalibration
import com.clkyigit.toraksb.sensor.rememberGravity
import kotlinx.coroutines.flow.takeWhile
import kotlin.math.sqrt
import kotlin.time.TimeSource

private const val CAL_DURATION_MS = 2000L

@Composable
fun CalibrationScreen(
    onDone: (TableCalibration) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = rememberGravity()
    val gravity by g.gravity
    val live = TableCalibration.fromFlatGravity(gravity.x, gravity.y, gravity.z)
    val mag = sqrt(gravity.x * gravity.x + gravity.y * gravity.y + gravity.z * gravity.z)
    val isFlat = mag > 0 && gravity.z / mag > 0.95 // ~18°'den az eğim

    var collecting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(collecting) {
        if (!collecting) return@LaunchedEffect
        val mark = TimeSource.Monotonic.markNow()
        var sx = 0.0; var sy = 0.0; var sz = 0.0; var n = 0
        var minZ = Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        snapshotFlow { g.gravity.value }
            .takeWhile { mark.elapsedNow().inWholeMilliseconds < CAL_DURATION_MS }
            .collect {
                sx += it.x; sy += it.y; sz += it.z; n++
                minZ = minOf(minZ, it.z); maxZ = maxOf(maxZ, it.z)
                progress = (mark.elapsedNow().inWholeMilliseconds.toFloat() / CAL_DURATION_MS).coerceAtMost(1f)
            }
        collecting = false
        progress = 0f
        when {
            n < 10 -> error = "Yeterli sensör verisi alınamadı, tekrar deneyin."
            maxZ - minZ > 0.15 -> error = "Telefon hareket etti. Masaya bırakıp dokunmadan tekrar deneyin."
            else -> {
                error = null
                onDone(TableCalibration.fromFlatGravity(sx / n, sy / n, sz / n))
            }
        }
    }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Masa kalibrasyonu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!g.available) {
            Text("Bu cihazda ivmeölçer bulunamadı.", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onCancel) { Text("Geri") }
            return@Column
        }
        Text(
            "1. Telefonu kılıfsız, EKRANI YUKARI bakacak şekilde hasta masasına düz bırakın.\n" +
                "2. Üst kenarı hastanın BAŞINA (gantriye) doğru olsun.\n" +
                "3. \"Kalibre et\"e basın ve 2 saniye dokunmayın.\n\n" +
                "Böylece açılar gerçek dikeye göre değil, BT görüntüsünün dikeyine (masaya dik eksene) göre ölçülür."
        )
        Text(
            "Anlık masa eğimi — yana: ${live.lateralOffsetDeg.f1()}°, baş-ayak: ${live.craniocaudalOffsetDeg.f1()}°",
            style = MaterialTheme.typography.titleMedium,
        )
        if (!isFlat) {
            Text("Telefon masaya düz yatmıyor gibi görünüyor.", color = MaterialTheme.colorScheme.error)
        }
        if (!g.fused) {
            Text(
                "Not: Bu cihazda füzyon yerçekimi sensörü yok, filtreli ivmeölçer kullanılıyor.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (collecting) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, enabled = !collecting) { Text("Vazgeç") }
            Button(
                onClick = { error = null; collecting = true },
                enabled = !collecting && isFlat,
                modifier = Modifier.height(48.dp),
            ) { Text("Kalibre et") }
        }
    }
}
