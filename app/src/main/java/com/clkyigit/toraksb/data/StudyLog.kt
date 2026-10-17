package com.clkyigit.toraksb.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Çalışma verisi: her kayıt CSV'ye bir satır. Hasta kimliği tutulmaz (yalnız vaka kodu),
 * fotoğraf saklanmaz. Sayılar nokta ondalıklı (R / Python / SPSS ile doğrudan okunur).
 */
object StudyLog {
    /** Sütun kimliği → ekranda gösterilen ad. Sıra CSV sütun sırasıdır. */
    val COLUMNS: List<Pair<String, String>> = listOf(
        "kayit_zamani" to "Kayıt zamanı",
        "vaka_kodu" to "Vaka kodu",
        "kaynak" to "Plan kaynağı",
        "hasta_pozisyonu" to "Hasta pozisyonu",
        "goruntu_180_donduruldu" to "Görüntü 180° döndürüldü",
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
        "sonuc_bt" to "BT kontrol sonucu",
        "not" to "Not",
        "uygulama_surumu" to "Uygulama sürümü",
    )
    private val LABELS = COLUMNS.toMap()

    fun label(key: String) = LABELS[key] ?: key

    fun num(v: Double?, digits: Int = 2): String =
        v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: ""

    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun dir(c: Context) = File(c.filesDir, "study").apply { mkdirs() }
    fun file(c: Context) = File(dir(c), "toraksB_calisma_verisi.csv")

    private fun esc(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    fun append(c: Context, values: Map<String, String>) {
        val f = file(c)
        val sb = StringBuilder()
        if (!f.exists() || f.length() == 0L) {
            sb.append('﻿') // Excel'de Türkçe karakterler doğru görünsün
            sb.append(COLUMNS.joinToString(",") { it.first }).append('\n')
        }
        sb.append(COLUMNS.joinToString(",") { esc(values[it.first].orEmpty()) }).append('\n')
        f.appendText(sb.toString())
    }

    fun count(c: Context): Int = runCatching {
        val f = file(c)
        if (!f.exists()) 0 else (f.readLines().count { it.isNotBlank() } - 1).coerceAtLeast(0)
    }.getOrDefault(0)

    /** Son kayıtlar (yeni → eski), ekranda kısa özet için. */
    fun recent(c: Context, n: Int = 10): List<Map<String, String>> = runCatching {
        val f = file(c)
        if (!f.exists()) return emptyList()
        val lines = f.readLines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val header = parse(lines[0].removePrefix("﻿"))
        lines.drop(1).takeLast(n).reversed().map { line -> header.zip(parse(line)).toMap() }
    }.getOrDefault(emptyList())

    fun clear(c: Context) {
        runCatching { file(c).delete() }
    }

    fun shareIntent(c: Context): Intent? {
        val f = file(c)
        if (!f.exists()) return null
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "toraksB çalışma verisi")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Çalışma verisini paylaş")
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
