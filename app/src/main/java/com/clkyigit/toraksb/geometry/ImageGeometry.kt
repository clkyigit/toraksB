package com.clkyigit.toraksb.geometry

import kotlin.math.abs
import kotlin.math.hypot

/** Görüntü pikseli (fotoğraf koordinatı: x sağa, y aşağı). */
data class Px(val x: Double, val y: Double) {
    operator fun minus(o: Px) = Px(x - o.x, y - o.y)
    infix fun dot(o: Px) = x * o.x + y * o.y
    fun len() = hypot(x, y)
}

/**
 * Konsol ekranı fotoğrafından ölçüm.
 *
 * Varsayımlar (ekranda gösterilir):
 *  - Görüntü standart aksiyel gösterimde: hastanın SAĞI ekranın SOLUNDA, masa altta.
 *  - Fotoğraf ekrana karşıdan çekilmiş (perspektif bozulması ihmal edilebilir).
 *  - Ölçek: görüntüde uzunluğu bilinen bir çizginin iki ucu (konsol cetveli veya
 *    konsolda çizilmiş bir mesafe ölçümü) ve gerçek uzunluğu.
 *  - Masa hattı (isteğe bağlı): masanın üst kenarı boyunca iki nokta. Verilmezse
 *    fotoğrafın yatayı masaya paralel kabul edilir. Verilirse fotoğraf eğik çekilmiş
 *    olsa bile yatay/dikey masaya göre alınır.
 */
data class ImageMarks(
    val entry: Px,
    val target: Px,
    val scaleA: Px,
    val scaleB: Px,
    val scaleCm: Double,
    val tableA: Px? = null,
    val tableB: Px? = null,
    val midline: Px? = null,
)

data class ImageMeasurement(
    /** + = hedef girişin hastanın SOLUNDA. */
    val dxCm: Double,
    /** Giriş seviyesinden hedefe, masaya dik derinlik. */
    val verticalDepthCm: Double,
    /** Giriş noktasının orta hatta uzaklığı (orta hat işaretlendiyse). */
    val entryFromMidlineCm: Double?,
    val entrySide: Side?,
    val cmPerPx: Double,
    /** Masa hattının fotoğraf yatayına göre eğimi (derece). */
    val tableTiltDeg: Double,
)

object ImageGeometry {
    fun measure(m: ImageMarks): ImageMeasurement {
        val scalePx = (m.scaleB - m.scaleA).len()
        require(scalePx > 2.0) { "Ölçek noktaları çok yakın" }
        require(m.scaleCm > 0) { "Ölçek uzunluğu 0'dan büyük olmalı" }
        val cmPerPx = m.scaleCm / scalePx

        // Yatay birim vektör (ekranın sağı = hastanın solu), masa hattından ya da fotoğraftan.
        var h = Px(1.0, 0.0)
        if (m.tableA != null && m.tableB != null) {
            val t = m.tableB - m.tableA
            require(t.len() > 2.0) { "Masa hattı noktaları çok yakın" }
            h = Px(t.x / t.len(), t.y / t.len())
            if (h.x < 0) h = Px(-h.x, -h.y) // yönü her zaman sağa doğru al
        }
        // Dikey birim vektör: masaya doğru (fotoğrafta aşağı)
        val v = Px(-h.y, h.x)

        val delta = m.target - m.entry
        val dx = (delta dot h) * cmPerPx
        val d = (delta dot v) * cmPerPx

        val midline = m.midline?.let { (m.entry - it) dot h }?.let { it * cmPerPx }

        return ImageMeasurement(
            dxCm = dx,
            verticalDepthCm = d,
            entryFromMidlineCm = midline?.let { abs(it) },
            entrySide = midline?.let { if (it >= 0) Side.LEFT else Side.RIGHT },
            cmPerPx = cmPerPx,
            tableTiltDeg = Math.toDegrees(kotlin.math.atan2(h.y, h.x)),
        )
    }
}
