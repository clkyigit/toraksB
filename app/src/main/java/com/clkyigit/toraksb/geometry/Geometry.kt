package com.clkyigit.toraksb.geometry

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Koordinat kuralları (tüm uygulamada aynı):
 *
 * Kullanıcı masanın AYAK ucunda durur ve BT aksiyel görüntüsüne baktığı yönden bakar
 * (standart aksiyel gösterim: hastanın SAĞI ekranın SOLUNDA).
 *
 *  x  : yatay (lateral), + = ekranın sağı = hastanın SOLU
 *  z  : kranyokaudal,    + = kaudal (kullanıcıya doğru, ayak tarafı)
 *  d  : dikey derinlik,  + = masaya doğru (cilt giriş seviyesinden aşağı)
 *
 * Açılar derece cinsindendir ve MASAYA göre tanımlıdır (BT görüntüsünün dikeyi = masaya dik).
 */

enum class Side(val label: String, val sign: Double) {
    RIGHT("Sağ", -1.0),
    LEFT("Sol", +1.0),
}

/** Radyoloğun BT konsolunda ölçüp girdiği değerler (cm). */
data class PlanInput(
    /** Cilt giriş noktasının orta hatta uzaklığı. */
    val entryFromMidlineCm: Double,
    val entrySide: Side,
    /** Kitle (hedef) merkezinin orta hatta uzaklığı. */
    val targetFromMidlineCm: Double,
    val targetSide: Side,
    /** Giriş noktası seviyesinden hedefe dikey (masaya dik) mesafe. */
    val verticalDepthCm: Double,
    /** Giriş ile hedef kesitleri arası mesafe; + = hedef girişten daha KAUDAL. Aksiyel girişte 0. */
    val craniocaudalCm: Double = 0.0,
    /** Doktor derinliği kendisi girmek isterse; null ise hesaplanan kullanılır. */
    val depthOverrideCm: Double? = null,
    /** Doktor aksiyel açıyı konsolda ölçtüyse; null ise hesaplanan kullanılır. */
    val axialAngleOverrideDeg: Double? = null,
    /** Yüzüstü (sırttan giriş): ayak ucundan bakınca hastanın sağı ekranın SAĞINDA. */
    val prone: Boolean = false,
)

data class NeedlePlan(
    /** Aksiyel düzlemde, masaya dik eksenden sapma. + = iğne ucu EKRANIN SAĞINA (sırtüstüde hastanın solu). */
    val axialAngleDeg: Double,
    /** Kranyokaudal açı. + = iğne ucu KAUDALE doğru. */
    val craniocaudalAngleDeg: Double,
    /** Ciltten hedefe iğne yolu uzunluğu. */
    val depthCm: Double,
    val calculatedDepthCm: Double,
    val calculatedAxialAngleDeg: Double,
    val depthIsManual: Boolean,
    val axialAngleIsManual: Boolean,
    /** Lezyon boyutundan hesaplanan izin verilen açı sapması (derece); bilinmiyorsa null. */
    val toleranceDeg: Double? = null,
    val lesionSizeCm: Double? = null,
    /** Yüzüstü hasta (sırttan giriş): sağ/sol yazıları ters çevrilir; açılar ekran yönünde aynıdır. */
    val prone: Boolean = false,
    /** Cilt işareti: giriş noktasının orta hatta uzaklığı ve hastanın hangi tarafı. */
    val skinMarkCm: Double? = null,
    val skinMarkLeft: Boolean = false,
    /** Plan girdileri ve görüntüden çıkan veriler (sütun adı → değer); çalışma kaydı ve özet için. */
    val details: Map<String, String> = emptyMap(),
) : java.io.Serializable

object PlanCalculator {
    /**
     * İzin verilen açı sapması: iğne ucu, yol boyunca L cm ilerlediğinde lezyon yarıçapı r içinde
     * kalmalı → L·tan(θ) ≤ r → θ = atan(r / L). 0,5°–10° aralığına sınırlanır.
     */
    fun toleranceFor(lesionSizeCm: Double?, pathCm: Double): Double? {
        if (lesionSizeCm == null || lesionSizeCm <= 0 || pathCm <= 0) return null
        return Math.toDegrees(atan2(lesionSizeCm / 2, pathCm)).coerceIn(0.5, 10.0)
    }

    /**
     * BT görüntüsünden yalnız AÇI alınır (giriş→lezyon piksel farkı, ölçek gerekmez);
     * uzunluk doktorun girdiği lezyon derinliğinden gelir.
     *
     * @param dxPx yatay piksel farkı (+ = ekranın sağı = hastanın solu)
     * @param dyPx dikey piksel farkı (+ = aşağı, masaya doğru); 0'dan büyük olmalı
     * @param depthCm doktorun girdiği lezyon derinliği
     * @param depthAlongPath true: derinlik iğne yolu boyunca (ciltten lezyona); false: dikey
     */
    fun fromImageAngle(
        dxPx: Double,
        dyPx: Double,
        depthCm: Double,
        depthAlongPath: Boolean,
        lesionSizeCm: Double? = null,
        craniocaudalCm: Double = 0.0,
        axialAngleOverrideDeg: Double? = null,
    ): NeedlePlan {
        require(dyPx > 0) { "Lezyon giriş noktasının altında olmalı" }
        require(depthCm > 0) { "Derinlik 0'dan büyük olmalı" }
        val tan = dxPx / dyPx
        val dz = craniocaudalCm
        val vertical = if (depthAlongPath) {
            // yol² = (d·tan)² + d² + dz²
            val inPlane2 = depthCm * depthCm - dz * dz
            require(inPlane2 > 0) { "Kesit farkı derinlikten büyük olamaz" }
            sqrt(inPlane2 / (1 + tan * tan))
        } else depthCm
        val base = fromDeltas(
            dxCm = vertical * tan,
            verticalDepthCm = vertical,
            craniocaudalCm = dz,
            depthOverrideCm = null,
            axialAngleOverrideDeg = axialAngleOverrideDeg,
        )
        return base.copy(
            toleranceDeg = toleranceFor(lesionSizeCm, base.depthCm),
            lesionSizeCm = lesionSizeCm,
        )
    }

    fun compute(input: PlanInput): NeedlePlan {
        require(input.verticalDepthCm > 0) { "Dikey derinlik 0'dan büyük olmalı" }
        // Ekran koordinatı (+ = ekranın sağı). Sırtüstüde hastanın solu sağda, yüzüstüde tersi.
        val k = if (input.prone) -1.0 else 1.0
        val xEntry = k * input.entrySide.sign * input.entryFromMidlineCm
        val xTarget = k * input.targetSide.sign * input.targetFromMidlineCm
        return fromDeltas(
            dxCm = xTarget - xEntry,
            verticalDepthCm = input.verticalDepthCm,
            craniocaudalCm = input.craniocaudalCm,
            depthOverrideCm = input.depthOverrideCm,
            axialAngleOverrideDeg = input.axialAngleOverrideDeg,
        )
    }

    /** dx: + = hedef girişin hastanın SOLUNDA; d: dikey derinlik; dz: + = hedef kaudalde. */
    fun fromDeltas(
        dxCm: Double,
        verticalDepthCm: Double,
        craniocaudalCm: Double = 0.0,
        depthOverrideCm: Double? = null,
        axialAngleOverrideDeg: Double? = null,
    ): NeedlePlan {
        require(verticalDepthCm > 0) { "Dikey derinlik 0'dan büyük olmalı" }
        val dx = dxCm
        val d = verticalDepthCm
        val dz = craniocaudalCm

        val axial = Math.toDegrees(atan2(dx, d))
        val cc = Math.toDegrees(atan2(dz, hypot(dx, d)))
        val length = sqrt(dx * dx + d * d + dz * dz)

        return NeedlePlan(
            axialAngleDeg = axialAngleOverrideDeg ?: axial,
            craniocaudalAngleDeg = cc,
            depthCm = depthOverrideCm ?: length,
            calculatedDepthCm = length,
            calculatedAxialAngleDeg = axial,
            depthIsManual = depthOverrideCm != null,
            axialAngleIsManual = axialAngleOverrideDeg != null,
        )
    }
}


/** Masa koordinatında (x: lateral +sol, y: masaya dik yukarı, z: kaudal) 3B vektör. */
private data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm() = sqrt(this dot this)
    fun unit() = this * (1.0 / norm())
}

/**
 * Masa kalibrasyonu: telefon EKRANI YUKARI bakacak şekilde masaya düz konur,
 * üst kenarı hastanın BAŞINA doğru. O andaki yerçekimi okuması, gerçek "yukarı"
 * yönünün MASA koordinatındaki karşılığını verir (upX, upY, upZ; birim vektör).
 */
data class TableCalibration(
    val upX: Double = 0.0,
    val upY: Double = 1.0,
    val upZ: Double = 0.0,
) : java.io.Serializable {
    /** Gösterim için: masa dikeyinin gerçek dikeyden ekranın soluna (hastanın sağına) sapması. */
    val lateralOffsetDeg: Double get() = Math.toDegrees(atan2(-upX, upY))
    /** Gösterim için: masa dikeyinin gerçek dikeyden kaudale sapması. */
    val craniocaudalOffsetDeg: Double get() = Math.toDegrees(atan2(upZ, upY))

    companion object {
        val NONE = TableCalibration()

        /**
         * g: Android TYPE_GRAVITY okuması, telefon düz ve ekran yukarıdayken.
         * Telefon x = masa x, telefon y = kraniyal (-z), telefon z = masa yukarı (+y).
         */
        fun fromFlatGravity(gx: Double, gy: Double, gz: Double): TableCalibration {
            val u = V3(gx, gz, -gy).unit()
            return TableCalibration(u.x, u.y, u.z)
        }
    }
}

/**
 * Ölçüm sırasında telefon dik tutulur: uzun kenarı iğneye hizalı, ALT kenarı iğne ucu
 * tarafında, ekran kullanıcıya (ayak ucuna) bakar. Ekran düzlemi aksiyel düzlemdedir
 * (gantri lazeriyle hizalanabilir) ve ekrandaki görünüm BT aksiyel görüntüsüyle aynı yöndedir.
 */
data class NeedleOrientation(
    /** İğne ucunun masaya dik eksenden ekranın sağına (hastanın soluna) sapması. */
    val axialDeg: Double,
    /** İğne ucunun kaudale (kullanıcıya) eğimi. */
    val craniocaudalDeg: Double,
    /** Telefon yataya yakınsa açı güvenilmez. */
    val reliable: Boolean,
)

object OrientationMath {
    /**
     * Tutuş kuralı: telefonun alt kenarı iğne ucu yönünde (telefon -y = iğne), ekran kullanıcıya
     * mümkün olduğunca dönük, yani telefonun x ekseni masanın aksiyel düzleminde kalır
     * (telefon iğne etrafında bükülmez).
     *
     * Masa düz ise kapalı çözüm vardır: iğne n = (a, -b, c) birim vektörü için okuma
     * g ∝ (a/s, b, b·c/s), s = √(1-c²)  ⇒  kk = atan2(gz, gy), aksiyel = atan2(gx·cos(kk), gy).
     * Masa eğikse aynı modeli eğik "yukarı" vektörüyle kurup birkaç sabit-nokta adımıyla çözeriz.
     */
    fun needleOrientation(
        gx: Double, gy: Double, gz: Double,
        calibration: TableCalibration,
    ): NeedleOrientation {
        val g = sqrt(gx * gx + gy * gy + gz * gz)
        if (g == 0.0) return NeedleOrientation(0.0, 0.0, false)
        val measured = rawAngles(gx, gy, gz)
        val up = V3(calibration.upX, calibration.upY, calibration.upZ).unit()

        // Başlangıç: masa düzmüş gibi; sonra modelin öngördüğü okumayı ölçülene yaklaştır.
        var ax = measured.first
        var cc = measured.second
        for (i in 0 until 30) {
            val p = predictedReading(ax, cc, up)
            val pr = rawAngles(p.x, p.y, p.z)
            val dAx = measured.first - pr.first
            val dCc = measured.second - pr.second
            ax += dAx
            cc += dCc
            if (abs(dAx) < 1e-10 && abs(dCc) < 1e-10) break
        }
        return NeedleOrientation(
            axialDeg = Math.toDegrees(ax),
            craniocaudalDeg = Math.toDegrees(cc),
            reliable = gy / g > 0.5, // iğne gerçek dikeyden ~60°'den fazla yatık değil
        )
    }

    /** Masa düzken geçerli kapalı çözüm (radyan). */
    private fun rawAngles(gx: Double, gy: Double, gz: Double): Pair<Double, Double> {
        val cc = atan2(gz, gy)
        return atan2(gx * cos(cc), gy) to cc
    }

    /** Masa koordinatında iğne açıları verilince, tutuş kuralına göre telefonun okuyacağı "yukarı". */
    private fun predictedReading(axial: Double, cc: Double, up: V3): V3 {
        val s = cos(cc)
        val n = V3(s * sin(axial), -s * cos(axial), sin(cc))
        val y = n * -1.0
        val zRef = V3(0.0, 0.0, 1.0)
        val z = (zRef - y * (zRef dot y)).unit()
        val x = y cross z
        return V3(x dot up, y dot up, z dot up)
    }
}
