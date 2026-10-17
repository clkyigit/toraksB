package com.clkyigit.toraksb.geometry

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Boyama fırçasının bir damgası (görüntü pikseli cinsinden). */
data class Dab(val x: Double, val y: Double, val r: Double, val erase: Boolean)

data class LesionAnalysis(
    /** Boyanan alanın ağırlık merkezi. */
    val centroid: Px,
    /** Nişan alınan nokta: doktor merkezi taşıdıysa o, yoksa ağırlık merkezi. */
    val aim: Px,
    val aimIsCustom: Boolean,
    /** Girişten merkeze doğrultu (derece; + = ekranın sağı = hastanın solu). */
    val centerAngleDeg: Double,
    /** Merkez doğrultudan lezyonun sağ/sol kenarına kalan açı. */
    val marginRightDeg: Double,
    val marginLeftDeg: Double,
    /** Boyanan alanın bir kısmı giriş noktasının üstündeyse. */
    val partlyAboveEntry: Boolean,
) {
    /** Güvenli (simetrik) tolerans: iki kenardan yakın olanı. */
    val toleranceDeg: Double get() = min(marginRightDeg, marginLeftDeg)
}

/**
 * Lezyon boyaması: görüntünün uzun kenarı en çok [gridLong] hücre olacak bir ızgaraya
 * fırça damgaları basılır, analiz bu ızgara üzerinden yapılır.
 *
 * Tolerans mantığı: giriş noktasından çıkan bir doğrultu boyanan bölgenin herhangi bir
 * hücresinden geçiyorsa iğne lezyona isabet eder. Bölgenin girişten görünen açısal aralığı
 * [enAz, enÇok] ise, merkez doğrultudan sapma bu aralıkta kaldıkça lezyon içindeyiz.
 * Ölçek gerekmez: yalnız açı kullanılır.
 */
class LesionMask(val imageWidth: Int, val imageHeight: Int, gridLong: Int = 512) {
    val cell: Double = max(imageWidth, imageHeight).toDouble() / gridLong
    val cols: Int = ceil(imageWidth / cell).toInt().coerceAtLeast(1)
    val rows: Int = ceil(imageHeight / cell).toInt().coerceAtLeast(1)
    private val bits = BooleanArray(cols * rows)

    fun clear() = bits.fill(false)

    fun stamp(d: Dab) {
        val r2 = d.r * d.r
        val c0 = floor((d.x - d.r) / cell).toInt().coerceAtLeast(0)
        val c1 = floor((d.x + d.r) / cell).toInt().coerceAtMost(cols - 1)
        val r0 = floor((d.y - d.r) / cell).toInt().coerceAtLeast(0)
        val r1 = floor((d.y + d.r) / cell).toInt().coerceAtMost(rows - 1)
        for (row in r0..r1) {
            val cy = (row + 0.5) * cell - d.y
            for (col in c0..c1) {
                val cx = (col + 0.5) * cell - d.x
                if (cx * cx + cy * cy <= r2) bits[row * cols + col] = !d.erase
            }
        }
    }

    fun stampAll(dabs: List<Dab>) {
        clear()
        dabs.forEach(::stamp)
    }

    val filledCount: Int get() = bits.count { it }

    fun isFilled(col: Int, row: Int) = bits[row * cols + col]

    /** @param target doktorun seçtiği hedef; null ise ağırlık merkezi kullanılır. */
    fun analyze(entry: Px, target: Px? = null): LesionAnalysis? {
        var n = 0; var sx = 0.0; var sy = 0.0
        for (row in 0 until rows) for (col in 0 until cols) if (bits[row * cols + col]) {
            n++; sx += (col + 0.5) * cell; sy += (row + 0.5) * cell
        }
        if (n == 0) return null
        val centroid = Px(sx / n, sy / n)
        val aim = target ?: centroid
        if (aim.y <= entry.y) return null
        val center = atan2(aim.x - entry.x, aim.y - entry.y)

        var minA = Double.MAX_VALUE; var maxA = -Double.MAX_VALUE
        var above = false
        val h = cell / 2
        for (row in 0 until rows) for (col in 0 until cols) if (bits[row * cols + col]) {
            val x = (col + 0.5) * cell; val y = (row + 0.5) * cell
            // Hücrenin dört köşesi: açısal aralık hücre boyutunu da kapsasın
            for ((ox, oy) in CORNERS) {
                val dy = y + oy * h - entry.y
                if (dy <= 0) { above = true; continue }
                val a = atan2(x + ox * h - entry.x, dy) - center
                if (a < minA) minA = a
                if (a > maxA) maxA = a
            }
        }
        if (minA == Double.MAX_VALUE) return null
        return LesionAnalysis(
            centroid = centroid,
            aim = aim,
            aimIsCustom = target != null,
            centerAngleDeg = Math.toDegrees(center),
            marginRightDeg = Math.toDegrees(max(0.0, maxA)),
            marginLeftDeg = Math.toDegrees(max(0.0, -minA)),
            partlyAboveEntry = above,
        )
    }

    private companion object {
        val CORNERS = listOf(-1.0 to -1.0, 1.0 to -1.0, -1.0 to 1.0, 1.0 to 1.0)
    }
}
