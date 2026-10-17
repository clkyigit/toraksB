package com.clkyigit.toraksb.data

import kotlin.math.abs
import kotlin.math.round

/**
 * Çalışma verisi biçimi (platformdan bağımsız): sütunlar, sayı biçimi, CSV satırı üretme/ayrıştırma.
 * Hasta kimliği tutulmaz (yalnız vaka kodu), fotoğraf saklanmaz. Sayılar nokta ondalıklı.
 */
object StudyFormat {
    /** Sütun kimliği → ekranda gösterilen ad. Sıra CSV sütun sırasıdır. */
    val COLUMNS: List<Pair<String, String>> = listOf(
        "kayit_zamani" to "Kayıt zamanı",
        "vaka_kodu" to "Vaka kodu",
        "kaynak" to "Plan kaynağı",
        "hasta_pozisyonu" to "Hasta pozisyonu",
        "solunum_fazi" to "Planlama BT solunum fazı",
        "goruntu_genislik_px" to "Görüntü genişliği (px)",
        "goruntu_yukseklik_px" to "Görüntü yüksekliği (px)",
        "giris_x_px" to "Giriş x (px)",
        "giris_y_px" to "Giriş y (px)",
        "hedef_x_px" to "Hedef x (px)",
        "hedef_y_px" to "Hedef y (px)",
        "agirlik_merkezi_x_px" to "Lezyon ağırlık merkezi x (px)",
        "agirlik_merkezi_y_px" to "Lezyon ağırlık merkezi y (px)",
        "hedef_elle_secildi" to "Hedef elle seçildi",
        "boyanan_alan_px2" to "Boyanan alan (px²)",
        "giris_orta_hat_cm" to "Giriş–orta hat (cm)",
        "giris_taraf" to "Giriş tarafı",
        "hedef_orta_hat_cm" to "Hedef–orta hat (cm)",
        "hedef_taraf" to "Hedef tarafı",
        "lezyon_boyutu_cm" to "Lezyon boyutu (cm)",
        "derinlik_girilen_cm" to "Girilen derinlik (cm)",
        "derinlik_turu" to "Derinlik türü",
        "igne_yolu_cm" to "İğne yolu (cm)",
        "igne_yolu_hesaplanan_cm" to "İğne yolu, hesaplanan (cm)",
        "kk_fark_cm" to "Kesit farkı (cm)",
        "aksiyel_aci_planlanan" to "Aksiyel açı, plan (°; + hastanın solu)",
        "aksiyel_aci_goruntuden" to "Aksiyel açı, görüntüden (°)",
        "aksiyel_aci_elle" to "Aksiyel açı elle girildi",
        "kk_aci_planlanan" to "Kranyokaudal açı, plan (°; + kaudal)",
        "pay_sag_derece" to "Pay, sağa (°)",
        "pay_sol_derece" to "Pay, sola (°)",
        "tolerans_derece" to "Tolerans (°)",
        "masa_yana_derece" to "Masa eğimi, yana (°)",
        "masa_bas_ayak_derece" to "Masa eğimi, baş-ayak (°)",
        "masa_kalibre" to "Masa kalibre edildi",
        "sensor" to "Sensör",
        "aksiyel_aci_olculen" to "Aksiyel açı, ölçülen (°)",
        "kk_aci_olculen" to "Kranyokaudal açı, ölçülen (°)",
        "aksiyel_sapma" to "Aksiyel sapma (°)",
        "hedefte" to "Kayıt anında tolerans içinde",
        "kullanim_modu" to "Kullanım modu",
        "sonuc_bt" to "BT kontrol sonucu",
        "not" to "Not",
        "uygulama_surumu" to "Uygulama sürümü",
    )
    private val LABELS = COLUMNS.toMap()

    const val BOM = "\uFEFF"

    fun label(key: String) = LABELS[key] ?: key

    /** Sabit ondalıklı, nokta ayraçlı sayı (yerel ayardan bağımsız). */
    fun num(v: Double?, digits: Int = 2): String {
        if (v == null || v.isNaN() || v.isInfinite()) return ""
        var factor = 1L
        repeat(digits) { factor *= 10 }
        val r = round(abs(v) * factor).toLong()
        val intPart = r / factor
        val frac = r % factor
        val sign = if (v < 0 && r != 0L) "-" else ""
        return if (digits == 0) "$sign$intPart"
        else "$sign$intPart." + frac.toString().padStart(digits, '0')
    }

    fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun headerLine(): String = COLUMNS.joinToString(",") { it.first }

    fun rowLine(values: Map<String, String>): String = COLUMNS.joinToString(",") { esc(values[it.first].orEmpty()) }

    /** Dosya içeriğinden kayıtlar (eski → yeni). */
    fun records(text: String): List<Map<String, String>> {
        val lines = text.removePrefix(BOM).lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val header = parse(lines[0])
        return lines.drop(1).map { header.zip(parse(it)).toMap() }
    }

    /** Basit CSV satır ayrıştırıcı (tırnaklı alanlar dahil). */
    fun parse(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var q = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                q && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                ch == '"' -> q = !q
                ch == ',' && !q -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(ch)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
