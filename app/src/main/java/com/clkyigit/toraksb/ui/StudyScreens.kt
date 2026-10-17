package com.clkyigit.toraksb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clkyigit.toraksb.BuildInfo
import com.clkyigit.toraksb.data.StudyLog
import com.clkyigit.toraksb.geometry.NeedleOrientation
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.RespStats
import com.clkyigit.toraksb.geometry.TableCalibration
import com.clkyigit.toraksb.sensor.SensorSource

/**
 * Yönlendirme ekranında, isteğe bağlı: bu vakayı çalışma verisine kaydet.
 * Basıldığı andaki ölçülen açı da kaydedilir (plan ile gerçekleşen karşılaştırması için).
 */
@Composable
fun StudySaveCard(
    plan: NeedlePlan,
    current: NeedleOrientation,
    inTolerance: Boolean,
    calibration: TableCalibration,
    calibrated: Boolean,
    source: SensorSource?,
    axResp: RespStats? = null,
    ccResp: RespStats? = null,
) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var caseCode by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var savedMsg by remember { mutableStateOf<String?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { open = !open }) {
                Text(if (open) "Çalışma kaydını gizle" else "Çalışma verisine kaydet (isteğe bağlı)")
            }
            if (!open) {
                savedMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                return@Column
            }
            Text(
                "Hasta adı ya da protokol numarası girmeyin; yalnız kendi vaka kodunuzu kullanın. Fotoğraf kaydedilmez.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = caseCode, onValueChange = { caseCode = it.take(40) },
                label = { Text("Vaka kodu (ör. TB-012)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text("BT kontrol sonucu", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("lezyonda" to "Lezyonda", "lezyon_disi" to "Dışında", "bilinmiyor" to "Bilinmiyor").forEach { (k, label) ->
                    if (result == k) Button(onClick = { result = k }, modifier = Modifier.weight(1f)) { Text(label) }
                    else OutlinedButton(onClick = { result = k }, modifier = Modifier.weight(1f)) { Text(label) }
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(300) },
                label = { Text("Not") }, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Şu an ölçülen: aksiyel ${current.axialDeg.f1()}°, kranyokaudal ${current.craniocaudalDeg.f1()}° " +
                    "(kaydete bastığınız andaki değer kaydedilir)",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    fun n(v: Double?) = StudyLog.num(v)
                    val row = plan.details + linkedMapOf(
                        "kayit_zamani" to StudyLog.now(),
                        "vaka_kodu" to caseCode.trim(),
                        "masa_yana_derece" to n(calibration.lateralOffsetDeg),
                        "masa_bas_ayak_derece" to n(calibration.craniocaudalOffsetDeg),
                        "masa_kalibre" to if (calibrated) "evet" else "hayir",
                        "sensor" to (source?.name ?: ""),
                        "aksiyel_aci_olculen" to n(current.axialDeg),
                        "kk_aci_olculen" to n(current.craniocaudalDeg),
                        "aksiyel_sapma" to n(current.axialDeg - plan.axialAngleDeg),
                        "hedefte" to if (inTolerance) "evet" else "hayir",
                        "solunum_saptandi" to if (axResp?.breathing == true || ccResp?.breathing == true) "evet" else "hayir",
                        "solunum_aksiyel_genlik" to n(axResp?.amplitude),
                        "solunum_kk_genlik" to n(ccResp?.amplitude),
                        "plato_aksiyel_aci" to n(axResp?.takeIf { it.breathing }?.plateau),
                        "plato_kk_aci" to n(ccResp?.takeIf { it.breathing }?.plateau),
                        "plato_aksiyel_sapma" to n(axResp?.takeIf { it.breathing }?.let { it.plateau - plan.axialAngleDeg }),
                        "sonuc_bt" to result,
                        "not" to note.replace('\n', ' ').replace('\r', ' ').trim(),
                        "uygulama_surumu" to BuildInfo.VERSION,
                    )
                    savedMsg = runCatching {
                        StudyLog.append(context, row)
                        "Kaydedildi. Toplam ${StudyLog.count(context)} kayıt."
                    }.getOrElse { "Kaydedilemedi: ${it.message}" }
                    open = false
                    note = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Kaydet") }
        }
    }
}

/** Kayıtlı çalışma verileri: sayı, son kayıtlar, CSV paylaşma, silme. */
@Composable
fun StudyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val count = remember(version) { StudyLog.count(context) }
    val recent = remember(version) { StudyLog.recent(context, 20) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Çalışma verileri", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "$count kayıt. Her kayıt planlanan değerleri, görüntüden çıkan verileri, kaydetme anında ölçülen açıyı " +
                "ve BT kontrol sonucunu içerir. CSV dosyası Excel, SPSS, R ya da Python ile açılabilir.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    error = runCatching { StudyLog.shareIntent(context)?.let(context::startActivity) }
                        .exceptionOrNull()?.let { "Paylaşılamadı: ${it.message}" }
                },
                enabled = count > 0,
            ) { Text("CSV'yi paylaş") }
            OutlinedButton(onClick = { confirmDelete = true }, enabled = count > 0) { Text("Tümünü sil") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (confirmDelete) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$count kayıt kalıcı olarak silinecek. Önce CSV'yi paylaşıp yedeklediğinizden emin olun.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirmDelete = false }) { Text("Vazgeç") }
                        Button(onClick = { StudyLog.clear(context); confirmDelete = false; version++ }) { Text("Sil") }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) Text("Son kayıtlar", fontWeight = FontWeight.SemiBold)
        recent.forEach { r ->
            Card {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${r["vaka_kodu"].orEmpty().ifEmpty { "(kodsuz)" }} · ${r["kayit_zamani"].orEmpty()}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Plan ${r["aksiyel_aci_planlanan"].orEmpty()}° · ölçülen ${r["aksiyel_aci_olculen"].orEmpty()}° · " +
                            "tolerans ±${r["tolerans_derece"].orEmpty()}° · yol ${r["igne_yolu_cm"].orEmpty()} cm",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val res = r["sonuc_bt"].orEmpty()
                    if (res.isNotEmpty()) Text("BT: $res", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = onBack) { Text("Geri") }
        Spacer(Modifier.height(24.dp))
    }
}
