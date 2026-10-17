package com.clkyigit.toraksb.geometry

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {
    private val eps = 1e-6

    @Test
    fun verticalPath_zeroAngle() {
        val p = PlanCalculator.compute(
            PlanInput(5.0, Side.RIGHT, 5.0, Side.RIGHT, verticalDepthCm = 6.0)
        )
        assertEquals(0.0, p.axialAngleDeg, eps)
        assertEquals(6.0, p.depthCm, eps)
    }

    @Test
    fun rightEntry_targetTowardMidline_tipGoesToPatientLeft() {
        // Giriş sağda 8 cm, hedef sağda 4 cm, 4 cm derinde -> 45°, hastanın soluna doğru
        val p = PlanCalculator.compute(
            PlanInput(8.0, Side.RIGHT, 4.0, Side.RIGHT, verticalDepthCm = 4.0)
        )
        assertEquals(45.0, p.axialAngleDeg, eps)
        assertEquals(kotlin.math.sqrt(32.0), p.depthCm, eps)
    }

    @Test
    fun crossingMidline() {
        val p = PlanCalculator.compute(
            PlanInput(1.0, Side.LEFT, 2.0, Side.RIGHT, verticalDepthCm = 3.0)
        )
        assertEquals(-45.0, p.axialAngleDeg, eps)
    }

    @Test
    fun craniocaudal() {
        val p = PlanCalculator.compute(
            PlanInput(0.0, Side.RIGHT, 0.0, Side.RIGHT, verticalDepthCm = 3.0, craniocaudalCm = 3.0)
        )
        assertEquals(45.0, p.craniocaudalAngleDeg, eps)
    }

    @Test
    fun overridesWin() {
        val p = PlanCalculator.compute(
            PlanInput(8.0, Side.RIGHT, 4.0, Side.RIGHT, 4.0,
                depthOverrideCm = 7.5, axialAngleOverrideDeg = 40.0)
        )
        assertEquals(7.5, p.depthCm, eps)
        assertEquals(40.0, p.axialAngleDeg, eps)
        assertTrue(p.depthIsManual && p.axialAngleIsManual)
        assertEquals(45.0, p.calculatedAxialAngleDeg, eps)
    }

    /** Telefon dik, saat yönünün tersine t derece döndürülmüş: alt kenar (iğne ucu) ekranın sağına gider. */
    @Test
    fun phoneRotatedCounterClockwise_tipToRight() {
        val t = toRad(30.0)
        val o = OrientationMath.needleOrientation(9.81 * sin(t), 9.81 * cos(t), 0.0, TableCalibration.NONE)
        assertEquals(30.0, o.axialDeg, 1e-6)
        assertEquals(0.0, o.craniocaudalDeg, 1e-6)
        assertTrue(o.reliable)
    }

    @Test
    fun tableTiltCancelsOut() {
        // Masa ekranın sağına (hastanın soluna) 3° yatık: sağ kenar alçak.
        val r = toRad(3.0)
        val cal = TableCalibration.fromFlatGravity(-9.81 * sin(r), 0.0, 9.81 * cos(r))
        assertEquals(3.0, cal.lateralOffsetDeg, 1e-6)
        // Telefon masaya tam dik (masayla birlikte 3° sola yatık): masaya göre açı 0 olmalı.
        val o = OrientationMath.needleOrientation(-9.81 * sin(r), 9.81 * cos(r), 0.0, cal)
        assertEquals(0.0, o.axialDeg, 1e-6)
    }

    /** Hem yana hem kaudale açılı iğne (dx=4, d=4, dz=2): sensör açıları plan açılarıyla aynı olmalı. */
    @Test
    fun combinedAngles_matchPlan() {
        val plan = PlanCalculator.compute(
            PlanInput(0.0, Side.RIGHT, 4.0, Side.LEFT, verticalDepthCm = 4.0, craniocaudalCm = 2.0)
        )
        val len = kotlin.math.sqrt(36.0)
        val a = 4 / len; val b = 4 / len; val c = 2 / len
        val s = kotlin.math.sqrt(1 - c * c)
        val o = OrientationMath.needleOrientation(a / s, b, b * c / s, TableCalibration.NONE)
        assertEquals(plan.axialAngleDeg, o.axialDeg, 1e-6)
        assertEquals(plan.craniocaudalAngleDeg, o.craniocaudalDeg, 1e-6)
    }

    @Test
    fun flatPhoneIsUnreliable() {
        val o = OrientationMath.needleOrientation(0.0, 0.5, 9.8, TableCalibration.NONE)
        assertFalse(o.reliable)
    }
}
