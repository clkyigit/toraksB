package com.clkyigit.toraksb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clkyigit.toraksb.data.StudyLog
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.PlanCalculator
import com.clkyigit.toraksb.geometry.PlanInput
import com.clkyigit.toraksb.geometry.Side
import com.clkyigit.toraksb.geometry.TableCalibration
import java.util.Locale
import kotlin.math.abs

/** Form alanları metin olarak tutulur; virgül veya nokta ondalık ayırıcı olabilir. */
class PlanFormState {
    var entryCm by mutableStateOf("")
    var entrySide by mutableStateOf(Side.RIGHT)
    var targetCm by mutableStateOf("")
    var targetSide by mutableStateOf(Side.RIGHT)
    var verticalDepthCm by mutableStateOf("")
    var ccCm by mutableStateOf("0")
    var ccCaudal by mutableStateOf(true)
    var manualDepth by mutableStateOf(false)
    var depthOverride by mutableStateOf("")
    var manualAngle by mutableStateOf(false)
    var angleOverride by mutableStateOf("")
    var angleTowardLeft by mutableStateOf(true)
    var lesionCm by mutableStateOf("")
    var prone by mutableStateOf(false)

    fun toInput(): PlanInput? {
        val entry = entryCm.num() ?: return null
        val target = targetCm.num() ?: return null
        val depth = verticalDepthCm.num()?.takeIf { it > 0 } ?: return null
        val cc = (ccCm.num() ?: 0.0) * if (ccCaudal) 1 else -1
        val dOverride = if (manualDepth) {
            depthOverride.num()?.takeIf { it > 0 } ?: return null
        } else null
        val aOverride = if (manualAngle) {
            // Elle açı hastaya göre girilir; ekran yönüne çevir
            (angleOverride.num() ?: return null) * (if (angleTowardLeft) 1 else -1) * (if (prone) -1 else 1)
        } else null
        return PlanInput(entry, entrySide, target, targetSide, depth, cc, dOverride, aOverride, prone)
    }

    companion object {
        val Saver = listSaver<PlanFormState, Any>(
            save = {
                listOf(it.entryCm, it.entrySide.name, it.targetCm, it.targetSide.name,
                    it.verticalDepthCm, it.ccCm, it.ccCaudal, it.manualDepth, it.depthOverride,
                    it.manualAngle, it.angleOverride, it.angleTowardLeft, it.lesionCm, it.prone)
            },
            restore = { l ->
                PlanFormState().apply {
                    entryCm = l[0] as String; entrySide = Side.valueOf(l[1] as String)
                    targetCm = l[2] as String; targetSide = Side.valueOf(l[3] as String)
                    verticalDepthCm = l[4] as String; ccCm = l[5] as String; ccCaudal = l[6] as Boolean
                    manualDepth = l[7] as Boolean; depthOverride = l[8] as String
                    manualAngle = l[9] as Boolean; angleOverride = l[10] as String
                    angleTowardLeft = l[11] as Boolean
                    lesionCm = l.getOrNull(12) as? String ?: ""
                    prone = l.getOrNull(13) as? Boolean ?: false
                }
            },
        )
    }
}

internal fun String.num(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }

internal fun Double.f1(): String = String.format(Locale("tr"), "%.1f", this)

/**
 * + açı = ekranın sağı. Sırtüstü hastada ekranın sağı hastanın SOLU, yüzüstüde (sırttan giriş) SAĞI.
 */
internal fun lateralWord(deg: Double, prone: Boolean = false): String {
    val towardScreenRight = deg >= 0
    val patientLeft = if (prone) !towardScreenRight else towardScreenRight
    return if (patientLeft) "hastanın SOLUNA" else "hastanın SAĞINA"
}

/** Ekranın sağ / sol kenarı hastanın hangi tarafı. */
internal fun screenRightSide(prone: Boolean) = if (prone) "Hastanın SAĞI" else "Hastanın SOLU"
internal fun screenLeftSide(prone: Boolean) = if (prone) "Hastanın SOLU" else "Hastanın SAĞI"
internal fun ccWord(deg: Double) = if (deg >= 0) "KAUDALE" else "KRANİALE"

@Composable
fun PlanScreen(
    form: PlanFormState,
    calibrated: Boolean,
    calibration: TableCalibration,
    onCalibrate: () -> Unit,
    onStart: (NeedlePlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = form.toInput()
    val plan = input?.let { runCatching { PlanCalculator.compute(it) }.getOrNull() }?.let { p ->
        val lesion = form.lesionCm.num()?.takeIf { it > 0 }
        fun n(v: Double?) = StudyLog.num(v)
        p.copy(
            toleranceDeg = PlanCalculator.toleranceFor(lesion, p.depthCm),
            lesionSizeCm = lesion,
            prone = form.prone,
            skinMarkCm = form.entryCm.num(),
            skinMarkLeft = form.entrySide == Side.LEFT,
            details = linkedMapOf(
                "kaynak" to "elle",
                "hasta_pozisyonu" to if (form.prone) "yuzustu" else "sirtustu",
                "giris_orta_hat_cm" to n(form.entryCm.num()),
                "giris_taraf" to if (form.entrySide == Side.LEFT) "sol" else "sag",
                "hedef_orta_hat_cm" to n(form.targetCm.num()),
                "hedef_taraf" to if (form.targetSide == Side.LEFT) "sol" else "sag",
                "lezyon_boyutu_cm" to n(lesion),
                "derinlik_girilen_cm" to n(form.verticalDepthCm.num()),
                "derinlik_turu" to "dikey",
                "igne_yolu_cm" to n(p.depthCm),
                "igne_yolu_hesaplanan_cm" to n(p.calculatedDepthCm),
                "kk_fark_cm" to n(input?.craniocaudalCm),
                "aksiyel_aci_planlanan" to n(p.axialAngleDeg),
                "aksiyel_aci_elle" to if (p.axialAngleIsManual) "evet" else "hayir",
                "kk_aci_planlanan" to n(p.craniocaudalAngleDeg),
                "tolerans_derece" to n(PlanCalculator.toleranceFor(lesion, p.depthCm) ?: 1.0),
            ),
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Elle giriş (yedek)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("BT konsolunda ölçtüğünüz değerleri girin. Görüntüden planlamaya dönmek için geri tuşunu kullanın.", style = MaterialTheme.typography.bodySmall)
        DisclaimerCard()
        PositionSelector(form.prone) { form.prone = it }

        SectionTitle("Cilt giriş noktası")
        CmField("Orta hatta uzaklık (cm)", form.entryCm) { form.entryCm = it }
        SideSelector(form.entrySide) { form.entrySide = it }

        SectionTitle("Kitle (hedef)")
        CmField("Orta hatta uzaklık (cm)", form.targetCm) { form.targetCm = it }
        SideSelector(form.targetSide) { form.targetSide = it }
        CmField(
            "Dikey derinlik (cm)", form.verticalDepthCm,
            hint = "Giriş noktası seviyesinden hedefe, masaya dik mesafe",
        ) { form.verticalDepthCm = it }
        CmField(
            "Lezyon boyutu (cm)", form.lesionCm,
            hint = "Tolerans açısını belirler; boş bırakılırsa ±1°",
        ) { form.lesionCm = it }

        SectionTitle("Kranyokaudal fark (isteğe bağlı)")
        CmField(
            "Kesitler arası mesafe (cm)", form.ccCm,
            hint = "Aksiyel girişte 0 bırakın",
        ) { form.ccCm = it }
        TwoChoice("Hedef girişe göre", "Kaudalde", "Kranialde", form.ccCaudal) { form.ccCaudal = it }

        SectionTitle("Elle giriş (konsol ölçümü)")
        ToggleRow("Derinliği elle gir", form.manualDepth) { form.manualDepth = it }
        if (form.manualDepth) {
            CmField("İğne yolu uzunluğu (cm)", form.depthOverride) { form.depthOverride = it }
        }
        ToggleRow("Aksiyel açıyı elle gir", form.manualAngle) { form.manualAngle = it }
        if (form.manualAngle) {
            CmField("Açı (derece, masaya dik eksenden)", form.angleOverride) { form.angleOverride = it }
            TwoChoice("İğne ucu", "Hastanın soluna", "Hastanın sağına", form.angleTowardLeft) {
                form.angleTowardLeft = it
            }
        }

        ResultCard(plan)
        plan?.let { PlanDataCard(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onCalibrate) { Text(if (calibrated) "Yeniden kalibre et" else "Masayı kalibre et") }
            Text(
                if (calibrated) "Masa: yana ${calibration.lateralOffsetDeg.f1()}°, baş-ayak ${calibration.craniocaudalOffsetDeg.f1()}°"
                else "Kalibrasyon yapılmadı",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = { plan?.let(onStart) },
            enabled = plan != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Yönlendirmeye geç") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ResultCard(plan: NeedlePlan?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (plan == null) {
                Text("Hesap için giriş noktası, hedef ve dikey derinliği girin.")
                return@Column
            }
            Text("Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Aksiyel açı: ${abs(plan.axialAngleDeg).f1()}° — iğne ucu ${lateralWord(plan.axialAngleDeg, plan.prone)}" +
                    if (plan.axialAngleIsManual) "  (elle; hesaplanan ${plan.calculatedAxialAngleDeg.f1()}°)" else ""
            )
            if (abs(plan.craniocaudalAngleDeg) >= 0.05) {
                Text("Kranyokaudal açı: ${abs(plan.craniocaudalAngleDeg).f1()}° — ${ccWord(plan.craniocaudalAngleDeg)}")
            }
            Text(
                "Derinlik: ${plan.depthCm.f1()} cm" +
                    if (plan.depthIsManual) "  (elle; hesaplanan ${plan.calculatedDepthCm.f1()} cm)" else "",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun DisclaimerCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(
            "Araştırma prototipi. Klinik karar için tek başına kullanılmaz; " +
                "iğne konumu her zaman BT kontrolüyle doğrulanmalıdır.",
            Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CmField(label: String, value: String, hint: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        supportingText = if (hint != null) {
            { Text(hint) }
        } else null,
        isError = value.isNotEmpty() && value.num() == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SideSelector(side: Side, onChange: (Side) -> Unit) {
    TwoChoice("Taraf", "Hastanın sağı", "Hastanın solu", side == Side.RIGHT) {
        onChange(if (it) Side.RIGHT else Side.LEFT)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoChoice(label: String, first: String, second: String, firstSelected: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            SegmentedButton(
                selected = firstSelected, onClick = { onChange(true) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(first) }
            SegmentedButton(
                selected = !firstSelected, onClick = { onChange(false) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(second) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Hasta pozisyonu: sağ/sol yazılarını belirler. Açı hesabını değiştirmez. */
@Composable
internal fun PositionSelector(prone: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Hasta pozisyonu", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!prone) Button(onClick = { onChange(false) }, modifier = Modifier.weight(1f)) { Text("Sırtüstü") }
            else OutlinedButton(onClick = { onChange(false) }, modifier = Modifier.weight(1f)) { Text("Sırtüstü") }
            if (prone) Button(onClick = { onChange(true) }, modifier = Modifier.weight(1f)) { Text("Yüzüstü (sırttan)") }
            else OutlinedButton(onClick = { onChange(true) }, modifier = Modifier.weight(1f)) { Text("Yüzüstü (sırttan)") }
        }
    }
}
