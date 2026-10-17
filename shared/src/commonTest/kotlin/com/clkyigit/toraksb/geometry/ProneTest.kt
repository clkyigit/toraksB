package com.clkyigit.toraksb.geometry

import kotlin.test.assertEquals
import kotlin.test.Test

class ProneTest {
    /** Aynı hasta-tarafı girdileri yüzüstüde ekran yönünde ters açı verir. */
    @Test
    fun proneFlipsScreenDirection() {
        val supine = PlanCalculator.compute(PlanInput(8.0, Side.RIGHT, 4.0, Side.RIGHT, 4.0))
        val prone = PlanCalculator.compute(PlanInput(8.0, Side.RIGHT, 4.0, Side.RIGHT, 4.0, prone = true))
        assertEquals(45.0, supine.axialAngleDeg, 1e-9)
        assertEquals(-45.0, prone.axialAngleDeg, 1e-9)
        assertEquals(supine.depthCm, prone.depthCm, 1e-9)
    }
}
