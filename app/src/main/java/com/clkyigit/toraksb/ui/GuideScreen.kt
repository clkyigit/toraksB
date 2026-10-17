package com.clkyigit.toraksb.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.OrientationMath
import com.clkyigit.toraksb.geometry.TableCalibration
import com.clkyigit.toraksb.sensor.SensorSource
import com.clkyigit.toraksb.sensor.rememberGravity
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val OkGreen = Color(0xFF1B8A3A)
private val WarnAmber = Color(0xFFC77700)
private val OffRed = Color(0xFFC62828)

@Composable
fun GuideScreen(
    plan: NeedlePlan,
    calibration: TableCalibration,
    calibrated: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = rememberGravity()
    val gravity by g.gravity
    val o = OrientationMath.needleOrientation(gravity.x, gravity.y, gravity.z, calibration)
    var tolerance by rememberSaveable(plan) { mutableFloatStateOf((plan.toleranceDeg ?: 1.0).toFloat().coerceIn(0.5f, 10f)) }

    val axialDiff = plan.axialAngleDeg - o.axialDeg
    val ccDiff = plan.craniocaudalAngleDeg - o.craniocaudalDeg
    // Histerezis: hedefe girince yeşil yanar, ancak toleransın 1,5 katı aşılınca söner (titreme olmaz)
    val axialOk = rememberWithin(abs(axialDiff), tolerance)
    val ccOk = rememberWithin(abs(ccDiff), tolerance)
    val allOk = o.reliable && axialOk && ccOk

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(allOk) {
        if (allOk) {
            val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
            else HapticFeedbackConstants.LONG_PRESS
            view.performHapticFeedback(c)
        }
    }

    val statusColor = when {
        !o.reliable -> OffRed
        allOk -> OkGreen
        abs(axialDiff) <= 3 * tolerance && abs(ccDiff) <= 3 * tolerance -> WarnAmber
        else -> OffRed
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().background(statusColor, RoundedCornerShape(12.dp)).padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    !o.reliable -> "Telefonu dik tutun"
                    allOk -> "HEDEF AÇIDA"
                    else -> "Açıyı ayarlayın"
                },
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            )
        }

        Text(
            "Derinlik: ${plan.depthCm.f1()} cm",
            fontSize = 34.sp, fontWeight = FontWeight.Bold,
        )

        AxialGauge(
            targetDeg = plan.axialAngleDeg,
            currentDeg = o.axialDeg,
            needleColor = if (axialOk) OkGreen else statusColor,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hastanın SAĞI", style = MaterialTheme.typography.labelMedium)
            Text("Hastanın SOLU", style = MaterialTheme.typography.labelMedium)
        }

        AngleRow(
            title = "Aksiyel",
            target = plan.axialAngleDeg, current = o.axialDeg, ok = axialOk,
            instruction = if (axialDiff > 0) "Ucu hastanın SOLUNA (ekranın sağına) yatırın"
            else "Ucu hastanın SAĞINA (ekranın soluna) yatırın",
            word = ::lateralWord,
        )
        AngleRow(
            title = "Kranyokaudal",
            target = plan.craniocaudalAngleDeg, current = o.craniocaudalDeg, ok = ccOk,
            instruction = if (ccDiff > 0) "Ucu KAUDALE (kendinize doğru) eğin"
            else "Ucu KRANİALE (gantriye doğru) eğin",
            word = ::ccWord,
        )

        Text(
            "Sensör: ${g.source?.label ?: "bulunamadı"}",
            style = MaterialTheme.typography.bodySmall,
            color = if (g.source == SensorSource.ACCELEROMETER) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Tolerans: ±${tolerance.toDouble().f1()}°" +
                (plan.lesionSizeCm?.let { " (${it.f1()} cm lezyona göre)" }
                    ?: if (plan.toleranceDeg != null) " (boyanan lezyona göre)" else ""),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(value = tolerance, onValueChange = { tolerance = it }, valueRange = 0.5f..10f, steps = 18)

        Text(
            "Telefonun uzun kenarını iğneye hizalayın; alt kenar iğne ucu tarafında, ekran ayak ucuna bakmalı " +
                "(ekran düzlemi gantri lazeriyle aynı aksiyel düzlemde). Ekrandaki görünüm BT aksiyel görüntüsüyle aynı yöndedir.",
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
        )
        if (!calibrated) {
            Text(
                "Masa kalibrasyonu yapılmadı: açılar gerçek dikeye göre ölçülüyor.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            )
        }
        DisclaimerCard()
        OutlinedButton(onClick = onBack) { Text("Plana dön") }
    }
}

@Composable
private fun AngleRow(
    title: String, target: Double, current: Double, ok: Boolean,
    instruction: String, word: (Double) -> String,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("Hedef ${abs(target).f1()}°  ·  Şu an ${abs(current).f1()}° ${shortWord(current, word)}")
        }
        Text(
            if (ok) "✓ Tamam (fark ${abs(target - current).f1()}°)"
            else "$instruction — fark ${abs(target - current).f1()}°",
            color = if (ok) OkGreen else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (ok) FontWeight.Normal else FontWeight.Bold,
        )
    }
}

private fun shortWord(deg: Double, word: (Double) -> String) =
    if (abs(deg) < 0.05) "" else "(${word(deg).lowercase()})"

/**
 * Yarım daire gösterge: merkez üstte, aşağı doğru dikey çizgi = masaya dik eksen.
 * Kesikli yeşil = hedef açı, kalın çizgi = iğnenin şu anki açısı. + açı ekranın sağına.
 */
@Composable
private fun AxialGauge(targetDeg: Double, currentDeg: Double, needleColor: Color, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val center = Offset(size.width / 2, 12f)
        val r = minOf(size.width / 2, size.height) - 16f
        drawArc(
            color = outline, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(center.x - r, center.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r),
            style = Stroke(width = 2f),
        )
        for (deg in -80..80 step 10) {
            val inner = if (deg % 30 == 0) r - 28f else r - 14f
            drawLine(outline, polar(center, inner, deg.toDouble()), polar(center, r, deg.toDouble()), 2f)
        }
        drawLine(outline, center, polar(center, r, 0.0), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f)))
        drawLine(OkGreen, center, polar(center, r, targetDeg), 6f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f)))
        needle(center, r, currentDeg.coerceIn(-89.0, 89.0), needleColor)
    }
}

private fun DrawScope.needle(center: Offset, r: Float, deg: Double, color: Color) {
    drawLine(color, center, polar(center, r * 0.95f, deg), 10f)
    drawCircle(color, 10f, center)
}

/** deg: aşağı yönden, ekranın sağına + . Ekran koordinatında y aşağı doğru artar. */
private fun polar(c: Offset, r: Float, deg: Double): Offset {
    val t = Math.toRadians(deg)
    return Offset(c.x + r * sin(t).toFloat(), c.y + r * cos(t).toFloat())
}

@Composable
private fun rememberWithin(diff: Double, tolerance: Float): Boolean {
    // Gözlenmeyen tutucu: kompozisyon sırasında yazmak yeniden çizim döngüsü başlatmaz
    val inside = remember { BooleanArray(1) }
    inside[0] = if (inside[0]) diff <= tolerance * 1.5 else diff <= tolerance
    return inside[0]
}
