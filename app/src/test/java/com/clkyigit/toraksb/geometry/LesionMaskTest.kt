package com.clkyigit.toraksb.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LesionMaskTest {
    /** Girişin tam altında, 400 px uzakta, yarıçapı 40 px daire: tolerans ≈ asin(40/400) ≈ 5,74°. */
    @Test
    fun circleBelowEntry() {
        val m = LesionMask(1000, 1000, gridLong = 1000)
        m.stamp(Dab(500.0, 500.0, 40.0, erase = false))
        val a = m.analyze(Px(500.0, 100.0))!!
        assertEquals(500.0, a.centroid.x, 0.5)
        assertEquals(500.0, a.centroid.y, 0.5)
        assertEquals(0.0, a.centerAngleDeg, 0.1)
        val expected = Math.toDegrees(Math.asin(40.0 / 400.0))
        assertEquals(expected, a.marginLeftDeg, 0.3)
        assertEquals(expected, a.marginRightDeg, 0.3)
        assertEquals(expected, a.toleranceDeg, 0.3)
    }

    /** Asimetrik şekil: tolerans dar olan tarafa göre. */
    @Test
    fun asymmetricUsesNarrowSide() {
        val m = LesionMask(1000, 1000, gridLong = 1000)
        m.stamp(Dab(500.0, 500.0, 40.0, erase = false))
        m.stamp(Dab(580.0, 500.0, 40.0, erase = false))
        val a = m.analyze(Px(540.0, 100.0))!!
        assertTrue(a.marginRightDeg > 0 && a.marginLeftDeg > 0)
        assertEquals(minOf(a.marginLeftDeg, a.marginRightDeg), a.toleranceDeg, 1e-9)
    }

    @Test
    fun eraseRemoves() {
        val m = LesionMask(500, 500)
        m.stamp(Dab(250.0, 250.0, 30.0, erase = false))
        m.stamp(Dab(250.0, 250.0, 60.0, erase = true))
        assertEquals(0, m.filledCount)
        assertNull(m.analyze(Px(250.0, 10.0)))
    }

    @Test
    fun obliqueTarget() {
        val m = LesionMask(1000, 1000, gridLong = 1000)
        m.stamp(Dab(500.0, 500.0, 20.0, erase = false))
        val a = m.analyze(Px(100.0, 100.0))!! // 45° sağ-aşağı
        assertEquals(45.0, a.centerAngleDeg, 0.2)
    }
}
