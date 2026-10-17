package com.clkyigit.toraksb.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageAngleTest {
    private val eps = 1e-6

    @Test
    fun pathDepth_45deg() {
        // Görüntüde 100 px sağa, 100 px aşağı → 45°; iğne yolu 8 cm
        val p = PlanCalculator.fromImageAngle(100.0, 100.0, depthCm = 8.0, depthAlongPath = true)
        assertEquals(45.0, p.axialAngleDeg, eps)
        assertEquals(8.0, p.depthCm, eps)
    }

    @Test
    fun verticalDepth_45deg() {
        val p = PlanCalculator.fromImageAngle(-50.0, 50.0, depthCm = 6.0, depthAlongPath = false)
        assertEquals(-45.0, p.axialAngleDeg, eps)
        assertEquals(6.0 * Math.sqrt(2.0), p.depthCm, eps)
    }

    @Test
    fun scaleIndependent() {
        val a = PlanCalculator.fromImageAngle(30.0, 80.0, 7.0, true)
        val b = PlanCalculator.fromImageAngle(300.0, 800.0, 7.0, true)
        assertEquals(a.axialAngleDeg, b.axialAngleDeg, eps)
        assertEquals(a.depthCm, b.depthCm, eps)
    }

    @Test
    fun toleranceFromLesionSize() {
        // 2 cm lezyon (r = 1), 10 cm yol → atan(0,1) ≈ 5,71°
        val p = PlanCalculator.fromImageAngle(0.0, 100.0, 10.0, true, lesionSizeCm = 2.0)
        assertEquals(Math.toDegrees(Math.atan(0.1)), p.toleranceDeg!!, eps)
        assertEquals(0.5, PlanCalculator.toleranceFor(0.1, 20.0)!!, eps) // alt sınır
        assertNull(PlanCalculator.toleranceFor(null, 10.0))
    }

    @Test
    fun pathDepthWithCraniocaudal() {
        val p = PlanCalculator.fromImageAngle(0.0, 100.0, depthCm = 5.0, depthAlongPath = true, craniocaudalCm = 3.0)
        assertEquals(5.0, p.depthCm, eps)
        assertEquals(Math.toDegrees(Math.atan2(3.0, 4.0)), p.craniocaudalAngleDeg, eps)
    }
}
