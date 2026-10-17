package com.clkyigit.toraksb.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import com.clkyigit.toraksb.geometry.Dab
import com.clkyigit.toraksb.geometry.LesionAnalysis
import com.clkyigit.toraksb.geometry.LesionMask
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.PlanCalculator
import com.clkyigit.toraksb.geometry.Px
import com.clkyigit.toraksb.geometry.TableCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Görüntüde dokunularak işaretlenen nokta (lezyon boyanır). */
enum class Mark(val label: String, val color: Color) {
    ENTRY("Giriş", Color(0xFF00E5FF)),
}

private val LesionColor = Color(0xFFFF4081)

enum class Step(val title: String, val hint: String) {
    PHOTO("BT görüntüsü", "Konsolda lezyonun ve giriş noktasının göründüğü aksiyel kesiti açın, ekrana tam karşıdan fotoğraflayın."),
    ENTRY("Cilt giriş noktası", "İğnenin cilde gireceği noktaya dokunun."),
    PAINT("Lezyonu boyayın", "Lezyonu tek parmakla boyayın. İki parmakla yakınlaştırın. Bitince \"Boyama bitti\"ye basın."),
    VALUES("Ölçümler", "Konsolda ölçtüğünüz lezyon derinliğini girin."),
    DONE("Plan hazır", ""),
}

class ImagePlanState {
    var imagePath by mutableStateOf<String?>(null)
    val marks = mutableStateMapOf<Mark, Px>()

    /** Boyama: fırça damgaları ve her fırça darbesinin başladığı indeks (geri alma için). */
    val dabs = mutableStateListOf<Dab>()
    val strokeStarts = mutableStateListOf<Int>()
    var paintDone by mutableStateOf(false)
    var erasing by mutableStateOf(false)
    var brushDp by mutableFloatStateOf(14f)
    /** Maske her değiştiğinde artar (yeniden çizim/analiz tetikler). */
    var maskVersion by mutableIntStateOf(0)
    private var mask: LesionMask? = null

    /** Lezyon derinliği (cm) ve ölçüm biçimi. */
    var depthCm by mutableStateOf("")
    var depthAlongPath by mutableStateOf(true)
    /** Orta hattan giriş noktasına (cm) ve taraf. */
    var midlineCm by mutableStateOf("")
    var midlineLeft by mutableStateOf(false)

    var ccCm by mutableStateOf("0")
    var ccCaudal by mutableStateOf(true)
    var manualAngle by mutableStateOf(false)
    var angleOverride by mutableStateOf("")
    var angleTowardLeft by mutableStateOf(true)

    /** Giriş noktasını yeniden yerleştirme modu. */
    var movingEntry by mutableStateOf(false)

    fun ensureMask(w: Int, h: Int) {
        val m = mask
        if (m == null || m.imageWidth != w || m.imageHeight != h) {
            mask = LesionMask(w, h).also { it.stampAll(dabs) }
            maskVersion++
        }
    }

    fun maskOrNull(): LesionMask? = mask

    private var cacheKey: Pair<Int, Px?>? = null
    private var cacheVal: LesionAnalysis? = null

    fun analysis(): LesionAnalysis? {
        val v = maskVersion // okunarak değişiklikte yeniden hesaplanması sağlanır
        val e = marks[Mark.ENTRY] ?: return null
        val key = v to e
        if (key != cacheKey) {
            cacheVal = mask?.analyze(e)
            cacheKey = key
        }
        return cacheVal
    }

    val step: Step
        get() = when {
            imagePath == null -> Step.PHOTO
            Mark.ENTRY !in marks -> Step.ENTRY
            !paintDone || analysis() == null -> Step.PAINT
            depthCm.num()?.takeIf { it > 0 } == null -> Step.VALUES
            else -> Step.DONE
        }

    val painting: Boolean get() = !movingEntry && step == Step.PAINT && imagePath != null

    fun tap(p: Px) {
        if (movingEntry || step == Step.ENTRY) {
            marks[Mark.ENTRY] = p
            movingEntry = false
            maskVersion++
        }
    }

    fun beginStroke() { strokeStarts.add(dabs.size) }

    fun addDab(d: Dab) {
        dabs.add(d)
        mask?.stamp(d)
        maskVersion++
    }

    /** İkinci parmak gelince yanlışlıkla başlayan fırça darbesini iptal et. */
    fun cancelStroke() {
        val start = strokeStarts.removeLastOrNull() ?: return
        while (dabs.size > start) dabs.removeAt(dabs.lastIndex)
        rebuild()
    }

    fun undoStroke() = cancelStroke()

    fun clearPaint() {
        dabs.clear(); strokeStarts.clear(); rebuild()
    }

    private fun rebuild() {
        mask?.stampAll(dabs)
        maskVersion++
    }

    fun newImage(path: String) {
        imagePath = path
        marks.clear(); dabs.clear(); strokeStarts.clear()
        paintDone = false; movingEntry = false; erasing = false
        mask = null; maskVersion++
    }

    fun plan(): NeedlePlan? {
        val e = marks[Mark.ENTRY] ?: return null
        val a = analysis() ?: return null
        val depth = depthCm.num()?.takeIf { it > 0 } ?: return null
        val cc = (ccCm.num() ?: 0.0) * if (ccCaudal) 1 else -1
        val angle = if (manualAngle) {
            (angleOverride.num() ?: return null) * if (angleTowardLeft) 1 else -1
        } else null
        return runCatching {
            PlanCalculator.fromImageAngle(
                dxPx = a.centroid.x - e.x, dyPx = a.centroid.y - e.y,
                depthCm = depth, depthAlongPath = depthAlongPath,
                craniocaudalCm = cc, axialAngleOverrideDeg = angle,
            ).copy(toleranceDeg = a.toleranceDeg.coerceIn(0.5, 10.0))
        }.getOrNull()
    }

    companion object {
        val Saver = listSaver<ImagePlanState, Any>(
            save = { s ->
                val e = s.marks[Mark.ENTRY]
                val flat = FloatArray(s.dabs.size * 4).also { a ->
                    s.dabs.forEachIndexed { i, d ->
                        a[4 * i] = d.x.toFloat(); a[4 * i + 1] = d.y.toFloat()
                        a[4 * i + 2] = d.r.toFloat(); a[4 * i + 3] = if (d.erase) 1f else 0f
                    }
                }
                listOf(
                    s.imagePath ?: "", e?.x ?: Double.NaN, e?.y ?: Double.NaN,
                    flat, s.strokeStarts.toIntArray(), s.paintDone, s.brushDp,
                    s.depthCm, s.depthAlongPath, s.midlineCm, s.midlineLeft,
                    s.ccCm, s.ccCaudal, s.manualAngle, s.angleOverride, s.angleTowardLeft,
                )
            },
            restore = { l ->
                runCatching {
                    ImagePlanState().apply {
                        imagePath = (l[0] as String).ifEmpty { null }
                        val ex = l[1] as Double; val ey = l[2] as Double
                        if (!ex.isNaN()) marks[Mark.ENTRY] = Px(ex, ey)
                        val f = l[3] as FloatArray
                        for (i in 0 until f.size / 4) {
                            dabs.add(Dab(f[4 * i].toDouble(), f[4 * i + 1].toDouble(), f[4 * i + 2].toDouble(), f[4 * i + 3] > 0.5f))
                        }
                        (l[4] as IntArray).forEach { strokeStarts.add(it) }
                        paintDone = l[5] as Boolean; brushDp = l[6] as Float
                        depthCm = l[7] as String; depthAlongPath = l[8] as Boolean
                        midlineCm = l[9] as String; midlineLeft = l[10] as Boolean
                        ccCm = l[11] as String; ccCaudal = l[12] as Boolean
                        manualAngle = l[13] as Boolean; angleOverride = l[14] as String; angleTowardLeft = l[15] as Boolean
                    }
                }.getOrElse { ImagePlanState() }
            },
        )
    }
}

private fun photoFile(context: Context): File =
    File(context.cacheDir, "photos").apply { mkdirs() }.resolve("bt_${System.currentTimeMillis()}.jpg")

private fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    val out = photoFile(context)
    context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
    out.absolutePath
}.getOrNull()

/** EXIF yönünü uygular; en uzun kenarı 2048 px'e küçültür. */
private fun decode(path: String): ImageBitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > 2048) {
            val k = 2048.0 / longest
            decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }.asImageBitmap()
}.getOrNull()

@Composable
fun ImagePlanScreen(
    state: ImagePlanState,
    calibrated: Boolean,
    calibration: TableCalibration,
    onCalibrate: () -> Unit,
    onManual: () -> Unit,
    onStart: (NeedlePlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCamera
        if (ok && f != null && f.length() > 0) state.newImage(f.absolutePath)
        pendingCamera = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) copyToCache(context, uri)?.let(state::newImage)
    }
    fun takePhoto() {
        runCatching {
            val f = photoFile(context)
            pendingCamera = f
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
        }.onFailure { loadError = true }
    }
    fun pickPhoto() {
        runCatching { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            .onFailure { loadError = true }
    }

    LaunchedEffect(state.imagePath) {
        val p = state.imagePath
        image = if (p == null) null else withContext(Dispatchers.IO) { decode(p) }
        loadError = p != null && image == null
        image?.let { state.ensureMask(it.width, it.height) }
    }

    val step = if (image == null) Step.PHOTO else state.step
    val analysis = state.analysis()
    val plan = state.plan()
    val stepNo = Step.entries.indexOf(step) + 1

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = !state.painting).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("toraksB", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onManual) { Text("Görüntüsüz giriş") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        state.movingEntry -> "Giriş noktasını yeniden yerleştirin"
                        step == Step.DONE -> "Plan hazır"
                        else -> "Adım $stepNo / ${Step.entries.size - 1} · ${step.title}"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                if (state.movingEntry) Text("Görüntüde yeni yerine dokunun.")
                else if (step.hint.isNotEmpty()) Text(step.hint)
                if (loadError) Text("Fotoğraf açılamadı. Tekrar deneyin.", color = MaterialTheme.colorScheme.error)
            }
        }

        val img = image
        if (img == null) {
            Button(onClick = ::takePhoto, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Ekranı fotoğrafla") }
            OutlinedButton(onClick = ::pickPhoto, modifier = Modifier.fillMaxWidth()) { Text("Galeriden seç") }
        } else {
            MarkCanvas(img, state, analysis)

            if (state.painting) {
                PaintTools(state, analysis)
            } else if (Mark.ENTRY in state.marks) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { state.movingEntry = true }) { Text("Girişi taşı") }
                    if (state.paintDone) OutlinedButton(onClick = { state.paintDone = false }) { Text("Lezyonu düzenle") }
                }
            }
            TextButton(onClick = ::takePhoto) { Text("Yeni fotoğraf") }

            if (step == Step.VALUES || step == Step.DONE) ValuesForm(state)

            if (step == Step.DONE) {
                ResultCard(plan, analysis, state)
                TextButton(onClick = { showMore = !showMore }) {
                    Text(if (showMore) "Ek ayarları gizle" else "Ek ayarlar: açıyı elle gir, kesit farkı")
                }
                if (showMore) MoreSettings(state)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onCalibrate) { Text(if (calibrated) "Yeniden kalibre et" else "Masayı kalibre et") }
                    Text(
                        if (calibrated) "Masa kalibre edildi" else "Önce masayı kalibre edin",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (calibrated) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { plan?.let(onStart) },
                    enabled = plan != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Yönlendirmeye geç") }
            }
        }
        DisclaimerCard()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PaintTools(s: ImagePlanState, a: LesionAnalysis?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton("Boya", !s.erasing, Modifier.weight(1f)) { s.erasing = false }
            ChoiceButton("Sil", s.erasing, Modifier.weight(1f)) { s.erasing = true }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fırça", Modifier.padding(end = 8.dp))
            Slider(
                value = s.brushDp, onValueChange = { s.brushDp = it },
                valueRange = 4f..40f, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = s::undoStroke, enabled = s.strokeStarts.isNotEmpty()) { Text("Geri al") }
            OutlinedButton(onClick = s::clearPaint, enabled = s.dabs.isNotEmpty()) { Text("Temizle") }
        }
        if (a != null) {
            Text(
                "Tolerans şu an ±${a.toleranceDeg.f1()}° (sağa ${a.marginRightDeg.f1()}°, sola ${a.marginLeftDeg.f1()}°)",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (a.partlyAboveEntry) {
                Text("Boyamanın bir kısmı giriş noktasının üstünde; hesapta yok sayıldı.", color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = { s.paintDone = true },
            enabled = a != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Boyama bitti") }
    }
}

@Composable
private fun ValuesForm(s: ImagePlanState) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Lezyon derinliği (cm)", s.depthCm) { s.depthCm = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("İğne yolu boyunca", s.depthAlongPath, Modifier.weight(1f)) { s.depthAlongPath = true }
                ChoiceButton("Dikey", !s.depthAlongPath, Modifier.weight(1f)) { s.depthAlongPath = false }
            }
            NumberField("Orta hattan giriş noktasına (cm)", s.midlineCm, hint = "Cilt işareti için, isteğe bağlı") { s.midlineCm = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Hastanın sağı", !s.midlineLeft, Modifier.weight(1f)) { s.midlineLeft = false }
                ChoiceButton("Hastanın solu", s.midlineLeft, Modifier.weight(1f)) { s.midlineLeft = true }
            }
        }
    }
}

@Composable
private fun ResultCard(plan: NeedlePlan?, a: LesionAnalysis?, s: ImagePlanState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plan == null || a == null) {
                Text("Plan hesaplanamadı. Giriş noktasını, boyamayı ve derinliği kontrol edin.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            BigValue("İğne yolu", "${plan.depthCm.f1()} cm", if (s.depthAlongPath) "elle girildi" else "dikey derinlikten")
            BigValue(
                "Açı", "${abs(plan.axialAngleDeg).f1()}°",
                "iğne ucu ${lateralWord(plan.axialAngleDeg).lowercase()}" +
                    if (plan.axialAngleIsManual) " · elle" else " · lezyon merkezine",
            )
            BigValue(
                "Tolerans", "±${(plan.toleranceDeg ?: 1.0).f1()}°",
                "boyanan lezyondan · sağa ${a.marginRightDeg.f1()}°, sola ${a.marginLeftDeg.f1()}°",
            )
            if (a.toleranceDeg < 1.0) {
                Text("Tolerans çok dar: lezyon küçük ya da derin. Dikkatli ilerleyin.", color = MaterialTheme.colorScheme.error)
            }
            if (abs(plan.craniocaudalAngleDeg) >= 0.05) {
                BigValue("Kranyokaudal", "${abs(plan.craniocaudalAngleDeg).f1()}°", ccWord(plan.craniocaudalAngleDeg).lowercase())
            }
            s.midlineCm.num()?.let {
                BigValue("Cilt işareti", "${it.f1()} cm", "orta hattın ${if (s.midlineLeft) "sol" else "sağ"} tarafında")
            }
        }
    }
}

@Composable
private fun BigValue(label: String, value: String, note: String?) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Column(horizontalAlignment = Alignment.End) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MoreSettings(s: ImagePlanState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleLine("Açıyı elle gir", s.manualAngle) { s.manualAngle = it }
        if (s.manualAngle) {
            NumberField("Açı (derece)", s.angleOverride) { s.angleOverride = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Ucu hastanın sağına", !s.angleTowardLeft, Modifier.weight(1f)) { s.angleTowardLeft = false }
                ChoiceButton("Ucu hastanın soluna", s.angleTowardLeft, Modifier.weight(1f)) { s.angleTowardLeft = true }
            }
        }
        NumberField("Lezyon ile giriş arası kesit farkı (cm)", s.ccCm) { s.ccCm = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceButton("Lezyon kaudalde", s.ccCaudal, Modifier.weight(1f)) { s.ccCaudal = true }
            ChoiceButton("Lezyon kranialde", !s.ccCaudal, Modifier.weight(1f)) { s.ccCaudal = false }
        }
    }
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, hint: String? = null, onChange: (String) -> Unit) {
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
private fun MarkCanvas(img: ImageBitmap, state: ImagePlanState, analysis: LesionAnalysis?) {
    var zoom by remember(img) { mutableFloatStateOf(1f) }
    var pan by remember(img) { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val painting = state.painting

    fun viewport(w: Float, h: Float) = Viewport(w, h, img.width, img.height, zoom, pan)
    fun applyTransform(centroid: Offset, dPan: Offset, dZoom: Float, w: Float, h: Float) {
        val oldZ = zoom
        val newZ = (oldZ * dZoom).coerceIn(1f, 12f)
        val f = newZ / oldZ
        val c = centroid - Offset(w / 2f, h / 2f)
        zoom = newZ
        pan = c - (c - pan) * f + dPan
    }

    val gestures = if (painting) {
        Modifier.pointerInput(img, "paint") {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val w = size.width.toFloat(); val h = size.height.toFloat()
                var multi = false
                var last: Px? = null
                fun dabAt(o: Offset) {
                    val v = viewport(w, h)
                    val p = v.toImage(o)
                    val r = with(density) { state.brushDp.dp.toPx() } / v.scale
                    val prev = last
                    if (prev == null) {
                        state.addDab(Dab(p.x, p.y, r, state.erasing))
                    } else {
                        // Hızlı harekette boşluk kalmasın: aradaki noktaları da damgala
                        val dist = (p - prev).len()
                        val n = (dist / (r * 0.4)).toInt().coerceIn(1, 60)
                        for (i in 1..n) {
                            val t = i.toDouble() / n
                            state.addDab(Dab(prev.x + (p.x - prev.x) * t, prev.y + (p.y - prev.y) * t, r, state.erasing))
                        }
                    }
                    last = p
                }
                state.beginStroke()
                dabAt(down.position)
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.count { it.pressed }
                    if (pressed == 0) break
                    if (pressed >= 2) {
                        if (!multi) { multi = true; state.cancelStroke() }
                        applyTransform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom(), w, h)
                    } else if (!multi) {
                        event.changes.firstOrNull { it.pressed }?.let { dabAt(it.position) }
                    }
                    event.changes.forEach { it.consume() }
                }
            }
        }
    } else {
        Modifier
            .pointerInput(img, "transform") {
                detectTransformGestures { centroid, dPan, dZoom, _ ->
                    applyTransform(centroid, dPan, dZoom, size.width.toFloat(), size.height.toFloat())
                }
            }
            .pointerInput(img, "tap") {
                detectTapGestures { o ->
                    val p = viewport(size.width.toFloat(), size.height.toFloat()).toImage(o)
                    if (p.x in 0.0..img.width.toDouble() && p.y in 0.0..img.height.toDouble()) state.tap(p)
                }
            }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()
                .background(Color.Black, RoundedCornerShape(8.dp))
        ) {
            Canvas(Modifier.fillMaxSize().then(gestures)) {
                if (size.width < 1f || size.height < 1f) return@Canvas
                val v = Viewport(size.width, size.height, img.width, img.height, zoom, pan)
                val tl = v.toScreen(Px(0.0, 0.0))
                drawImage(
                    img,
                    dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                    dstSize = IntSize((img.width * v.scale).toInt().coerceAtLeast(1), (img.height * v.scale).toInt().coerceAtLeast(1)),
                    filterQuality = if (v.scale > 2f) FilterQuality.None else FilterQuality.Medium,
                )
                drawPaint(v, state)
                drawOverlay(v, state, analysis, measurer)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (painting) "Tek parmak boyar, iki parmak yakınlaştırır." else "İki parmakla yakınlaştırın, hassas dokunun.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            )
            if (zoom > 1.01f) TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Sığdır") }
        }
    }
}

/** Boyamayı yarı saydam tek katman olarak çizer; silgi damgaları katmandan oyar. */
private fun DrawScope.drawPaint(v: Viewport, s: ImagePlanState) {
    @Suppress("UNUSED_VARIABLE") val ver = s.maskVersion
    if (s.dabs.isEmpty()) return
    val layer = Paint().apply { alpha = 0.45f }
    drawIntoCanvas { c ->
        c.saveLayer(Rect(Offset.Zero, size), layer)
        s.dabs.forEach { d ->
            val o = v.toScreen(Px(d.x, d.y))
            val r = (d.r * v.scale).toFloat()
            if (d.erase) drawCircle(Color.Black, r, o, blendMode = BlendMode.Clear)
            else drawCircle(LesionColor, r, o)
        }
        c.restore()
    }
}

private class Viewport(w: Float, h: Float, iw: Int, ih: Int, zoom: Float, pan: Offset) {
    val scale = minOf(w / iw, h / ih) * zoom
    private val cx = w / 2 + pan.x
    private val cy = h / 2 + pan.y
    private val icx = iw / 2.0
    private val icy = ih / 2.0
    fun toScreen(p: Px) = Offset((cx + (p.x - icx) * scale).toFloat(), (cy + (p.y - icy) * scale).toFloat())
    fun toImage(o: Offset) = Px(icx + (o.x - cx) / scale, icy + (o.y - cy) / scale)
}

private fun DrawScope.drawOverlay(v: Viewport, s: ImagePlanState, a: LesionAnalysis?, tm: TextMeasurer) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
    val entry = s.marks[Mark.ENTRY] ?: return
    val e = v.toScreen(entry)
    drawLine(Color.White.copy(alpha = 0.6f), e, Offset(e.x, size.height), 2f, pathEffect = dash)
    if (a != null) {
        // Hedef doğrultu ve tolerans konisi (iki kenar), lezyonun ötesine kadar uzatılmış
        val c = v.toScreen(a.centroid)
        val len = (c - e).getDistance() * 1.25f
        fun ray(offsetDeg: Double): Offset {
            val ang = Math.toRadians(a.centerAngleDeg + offsetDeg)
            return e + Offset((sin(ang) * len).toFloat(), (cos(ang) * len).toFloat())
        }
        drawLine(Color.Yellow.copy(alpha = 0.8f), e, ray(a.marginRightDeg), 2f, pathEffect = dash)
        drawLine(Color.Yellow.copy(alpha = 0.8f), e, ray(-a.marginLeftDeg), 2f, pathEffect = dash)
        drawLine(LesionColor, e, c, 4f)
        drawCircle(Color.Black.copy(alpha = 0.6f), 9f, c, style = Stroke(4f))
        drawCircle(Color.White, 9f, c, style = Stroke(2f))
        safeLabel(tm, "Merkez", c + Offset(14f, 8f), Color.White)
    }
    val active = s.movingEntry
    drawCircle(Color.Black.copy(alpha = 0.6f), 18f, e, style = Stroke(6f))
    drawCircle(if (active) Color.White else Mark.ENTRY.color, 18f, e, style = Stroke(3f))
    drawCircle(Mark.ENTRY.color, 2.5f, e)
    safeLabel(tm, Mark.ENTRY.label, e + Offset(22f, -30f), Mark.ENTRY.color)
}

/** Ekran dışına taşan etiketler çizilmez (drawText'e negatif alan verilirse uygulama çöker). */
private fun DrawScope.safeLabel(tm: TextMeasurer, text: String, at: Offset, color: Color) {
    val layout = tm.measure(
        text,
        TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, background = Color.Black.copy(alpha = 0.55f)),
    )
    if (at.x > size.width || at.y > size.height || at.x < -layout.size.width || at.y < -layout.size.height) return
    val x = at.x.coerceIn(0f, max(0f, size.width - layout.size.width))
    val y = at.y.coerceIn(0f, max(0f, size.height - layout.size.height))
    drawText(layout, topLeft = Offset(x, y))
}
