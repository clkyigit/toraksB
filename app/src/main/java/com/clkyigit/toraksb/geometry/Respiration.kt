package com.clkyigit.toraksb.geometry

import kotlin.math.abs
import kotlin.math.floor

/** Planlama BT'sinin çekildiği solunum fazı. */
enum class BreathPhase(val label: String, val csv: String, val reminder: String?) {
    FREE("Serbest solunum", "serbest", null),
    EXP_HOLD("Ekspiryum sonu nefes tutma", "ekspiryum_sonu", "Hastaya nefesini verip tutturun (ekspiryum sonu)"),
    INSP_HOLD("İnspiryum sonu nefes tutma", "inspiryum_sonu", "Hastaya nefes alıp tutturun (inspiryum sonu)"),
}

data class RespStats(
    /** Penceredeki en küçük / en büyük açı (titreme süzülmüş). */
    val min: Double,
    val max: Double,
    /** Yarım genlik: (max − min) / 2. */
    val amplitude: Double,
    /** Sinyalin en uzun durduğu açı: solunum duraklaması (çoğunlukla ekspiryum sonu). */
    val plateau: Double,
    /** Düzenli salınım saptandı mı (en az bir tam döngü, makul genlik). */
    val breathing: Boolean,
    val windowSec: Double,
)

/**
 * İğne göğüs duvarına tutunduktan sonra açı solunumla salınır. Bu sınıf son birkaç saniyelik
 * açı örneklerinden salınım bandını ve plato (duraklama) açısını çıkarır.
 *
 * - El titremesi kısa hareketli ortalama ile süzülür.
 * - "Solunum" yalnız sinyal ortalaması etrafında en az iki kez yön değiştirip
 *   (histerezisli sıfır geçişi) genlik makul aralıktaysa kabul edilir; böylece doktorun
 *   iğneyi tek yöne çevirmesi solunum sanılmaz.
 * - Plato: açı değerlerinin histogramında en kalabalık bölme. Solunumda sinyal duraklama
 *   evresinde en uzun kalır; hangi uçta olduğunu bilmeye gerek yoktur.
 */
class RespiratoryTracker(
    private val windowSec: Double = 8.0,
    private val smoothN: Int = 5,
    private val binDeg: Double = 0.25,
    private val minAmplitude: Double = 0.4,
    private val maxAmplitude: Double = 10.0,
) {
    private val times = ArrayDeque<Double>()
    private val values = ArrayDeque<Double>()
    private val raw = ArrayDeque<Double>()

    fun reset() { times.clear(); values.clear(); raw.clear() }

    fun add(tSec: Double, value: Double) {
        raw.addLast(value)
        while (raw.size > smoothN) raw.removeFirst()
        times.addLast(tSec)
        values.addLast(raw.average())
        while (times.isNotEmpty() && tSec - times.first() > windowSec) {
            times.removeFirst(); values.removeFirst()
        }
    }

    fun stats(): RespStats? {
        if (values.size < 20) return null
        val span = times.last() - times.first()
        if (span < 3.0) return null
        val mn = values.min()
        val mx = values.max()
        val amp = (mx - mn) / 2
        val mean = values.average()

        // Histerezisli yön değiştirme sayımı
        val hyst = maxOf(amp * 0.3, 0.1)
        var state = 0
        var crossings = 0
        for (v in values) {
            val s = when {
                v > mean + hyst -> 1
                v < mean - hyst -> -1
                else -> 0
            }
            if (s != 0 && s != state) {
                if (state != 0) crossings++
                state = s
            }
        }

        // Plato: en kalabalık bölme ve komşularının ortalaması
        val bins = HashMap<Int, Int>()
        for (v in values) {
            val b = floor((v - mn) / binDeg).toInt()
            bins[b] = (bins[b] ?: 0) + 1
        }
        val top = bins.maxByOrNull { it.value }!!.key
        val center = mn + (top + 0.5) * binDeg
        val near = values.filter { abs(it - center) <= binDeg * 1.5 }
        val plateau = if (near.isEmpty()) center else near.average()

        return RespStats(
            min = mn, max = mx, amplitude = amp, plateau = plateau,
            breathing = crossings >= 2 && amp in minAmplitude..maxAmplitude,
            windowSec = span,
        )
    }
}
