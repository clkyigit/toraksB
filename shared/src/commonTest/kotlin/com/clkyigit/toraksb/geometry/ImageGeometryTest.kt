package com.clkyigit.toraksb.geometry

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.math.cos
import kotlin.math.sin

class ImageGeometryTest {
    private val eps = 1e-6

    /** 10 px = 1 cm. Giriş hastanın sağında (ekranın solu), hedef 4 cm içeride, 4 cm derinde. */
    @Test
    fun straightPhoto() {
        val m = ImageGeometry.measure(
            ImageMarks(
                entry = Px(100.0, 100.0), target = Px(140.0, 140.0),
                scaleA = Px(0.0, 0.0), scaleB = Px(0.0, 50.0), scaleCm = 5.0,
                midline = Px(180.0, 300.0),
            )
        )
        assertEquals(0.1, m.cmPerPx, eps)
        assertEquals(4.0, m.dxCm, eps)
        assertEquals(4.0, m.verticalDepthCm, eps)
        assertEquals(8.0, m.entryFromMidlineCm!!, eps)
        assertEquals(Side.RIGHT, m.entrySide)
        val plan = PlanCalculator.fromDeltas(m.dxCm, m.verticalDepthCm)
        assertEquals(45.0, plan.axialAngleDeg, eps)
    }

    /** Fotoğraf 10° döndürülmüş çekilmiş; masa hattı işaretlenince sonuç değişmemeli. */
    @Test
    fun rotatedPhotoWithTableLine() {
        val a = toRad(10.0)
        fun rot(x: Double, y: Double) = Px(x * cos(a) - y * sin(a), x * sin(a) + y * cos(a))
        val m = ImageGeometry.measure(
            ImageMarks(
                entry = rot(100.0, 100.0), target = rot(140.0, 140.0),
                scaleA = rot(0.0, 0.0), scaleB = rot(0.0, 50.0), scaleCm = 5.0,
                tableA = rot(0.0, 400.0), tableB = rot(300.0, 400.0),
                midline = rot(180.0, 300.0),
            )
        )
        assertEquals(4.0, m.dxCm, eps)
        assertEquals(4.0, m.verticalDepthCm, eps)
        assertEquals(8.0, m.entryFromMidlineCm!!, eps)
        assertEquals(10.0, m.tableTiltDeg, eps)
    }

    /** Masa noktaları sağdan sola işaretlense de aynı sonuç. */
    @Test
    fun tableLineDirectionIndependent() {
        val m = ImageGeometry.measure(
            ImageMarks(
                entry = Px(200.0, 100.0), target = Px(170.0, 140.0),
                scaleA = Px(0.0, 0.0), scaleB = Px(10.0, 0.0), scaleCm = 1.0,
                tableA = Px(300.0, 400.0), tableB = Px(0.0, 400.0),
            )
        )
        assertEquals(-3.0, m.dxCm, eps)
        assertEquals(4.0, m.verticalDepthCm, eps)
    }
}
