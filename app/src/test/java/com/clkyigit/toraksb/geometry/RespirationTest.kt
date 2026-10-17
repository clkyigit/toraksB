package com.clkyigit.toraksb.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

class RespirationTest {
    /**
     * 0,25 Hz solunum; döngünün yarısı ekspiryum sonu duraklaması (açı 30°),
     * kalan yarısında 34°'ye çıkıp geri iner. Plato 30° bulunmalı, genlik ≈ 2°.
     */
    @Test
    fun detectsBreathingAndPlateau() {
        val t = RespiratoryTracker()
        var time = 0.0
        while (time < 10.0) {
            val phase = (time * 0.25) % 1.0
            val v = 30.0 + if (phase < 0.5) 0.0 else 4.0 * sin((phase - 0.5) * 2 * PI).let { max(it, 0.0) }
            t.add(time, v)
            time += 1.0 / 30
        }
        val s = t.stats()!!
        assertTrue(s.breathing)
        assertEquals(30.0, s.plateau, 0.3)
        assertEquals(2.0, s.amplitude, 0.4)
    }

    /** Doktor iğneyi tek yönde yavaşça çeviriyor: solunum sayılmamalı. */
    @Test
    fun monotonicTurnIsNotBreathing() {
        val t = RespiratoryTracker()
        var time = 0.0
        while (time < 8.0) {
            t.add(time, 20.0 + time * 1.5)
            time += 1.0 / 30
        }
        assertFalse(t.stats()!!.breathing)
    }

    /** Sabit tutulan telefon: genlik çok küçük, solunum yok. */
    @Test
    fun stillIsNotBreathing() {
        val t = RespiratoryTracker()
        var time = 0.0
        while (time < 8.0) {
            t.add(time, 15.0 + 0.05 * sin(time * 40))
            time += 1.0 / 30
        }
        assertFalse(t.stats()!!.breathing)
    }
}
