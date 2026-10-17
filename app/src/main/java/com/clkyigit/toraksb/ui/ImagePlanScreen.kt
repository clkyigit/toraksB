package com.clkyigit.toraksb.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import com.clkyigit.toraksb.geometry.ImageGeometry
import com.clkyigit.toraksb.geometry.ImageMarks
import com.clkyigit.toraksb.geometry.ImageMeasurement
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.PlanCalculator
import com.clkyigit.toraksb.geometry.Px
import com.clkyigit.toraksb.geometry.Side
import com.clkyigit.toraksb.geometry.TableCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Görüntüde işaretlenen noktalar, işaretleme sırasıyla. */
enum class Mark(val label: String, val color: Color) {
    SCALE_A("Cetvel 1", Color(0xFFFFC400)),
    SCALE_B("Cetvel 2", Color(0xFFFFC400)),
    ENTRY("Giriş", Color(0xFF00E5FF)),
    TARGET("Kitle", Color(0xFFFF4081)),
    MIDLINE("Orta hat", Color(0xFFB2FF59)),
}

/** Sihirbaz adımları. */
enum class Step(val title: String, val hint: String) {
    PHOTO("BT görüntüsü", "Konsolda kitlenin ve giriş noktasının göründüğü aksiyel kesiti açın, ekrana tam karşıdan fotoğraflayın."),
    SCALE_A("Cetvelin bir ucu", "Uzunluğunu bildiğiniz çizginin (konsol cetveli ya da konsolda çizdiğiniz ölçüm) bir ucuna dokunun."),
    SCALE_B("Cetvelin diğer ucu", "Aynı çizginin diğer ucuna dokunun."),
    SCALE_LEN("Cetvel uzunluğu", "Bu çizginin gerçek uzunluğunu santimetre olarak yazın."),
    ENTRY("Cilt giriş noktası", "İğnenin cilde gireceği noktaya dokunun."),
    TARGET("Kitle", "İğne ucunun ulaşacağı noktaya dokunun."),
    MIDLINE("Orta hat – giriş mesafesi", "Orta hatta (vertebra korpusu ortası) dokunun, mesafeyi elle yazın ya da atlayın."),
    DONE("Plan hazır", ""),
}

class ImagePlanState {
    var imagePath by mutableStateOf<String?>(null)
    val marks = mutableStateMapOf<Mark, Px>()
    /** Geri alma için yerleştirme sırası. */
    val history = mutableStateListOf<Mark>()
    var scaleCm by mutableStateOf("")
    var scaleConfirmed by mutableStateOf(false)
    var midlineSkipped by mutableStateOf(false)
    /** Orta hat – giriş mesafesi elle girildiyse (görüntüden ölçülenin yerine geçer). */
    var midlineManual by mutableStateOf(false)
    var midlineCm by mutableStateOf("")
    var midlineLeft by mutableStateOf(false)
    var ccCm by mutableStateOf("0")
    var ccCaudal by mutableStateOf(true)
    var manualDepth by mutableStateOf(false)
    var depthOverride by mutableStateOf("")
    var manualAngle by mutableStateOf(false)
    var angleOverride by mutableStateOf("")
    var angleTowardLeft by mutableStateOf(true)

    val step: Step
        get() = when {
            imagePath == null -> Step.PHOTO
            Mark.SCALE_A !in marks -> Step.SCALE_A
            Mark.SCALE_B !in marks -> Step.SCALE_B
            !scaleConfirmed || scaleCm.num()?.takeIf { it > 0 } == null -> Step.SCALE_LEN
            Mark.ENTRY !in marks -> Step.ENTRY
            Mark.TARGET !in marks -> Step.TARGET
            Mark.MIDLINE !in marks && !midlineSkipped && !midlineManual -> Step.MIDLINE
            else -> Step.DONE
        }

    /** Şu an dokunuşla yerleştirilecek nokta (yoksa null). */
    val placing: Mark?
        get() = when (step) {
            Step.SCALE_A -> Mark.SCALE_A
            Step.SCALE_B -> Mark.SCALE_B
            Step.ENTRY -> Mark.ENTRY
            Step.TARGET -> Mark.TARGET
            Step.MIDLINE -> Mark.MIDLINE
            else -> null
        }

    fun place(p: Px) {
        val m = placing ?: return
        marks[m] = p
        history.remove(m)
        history.add(m)
    }

    fun undo() {
        when {
            midlineManual -> midlineManual = false
            midlineSkipped -> midlineSkipped = false
            history.isNotEmpty() -> {
                val last = history.removeAt(history.lastIndex)
                marks.remove(last)
                if (last == Mark.SCALE_B) scaleConfirmed = false
            }
        }
    }

    fun restart() {
        marks.clear(); history.clear()
        scaleConfirmed = false; midlineSkipped = false; midlineManual = false
    }

    /** Cilt işareti: elle girilen değer varsa o, yoksa görüntüden ölçülen. */
    fun skinMark(m: ImageMeasurement?): Triple<Double, Side, Boolean>? {
        if (midlineManual) {
            val cm = midlineCm.num() ?: return null
            return Triple(cm, if (midlineLeft) Side.LEFT else Side.RIGHT, true)
        }
        val cm = m?.entryFromMidlineCm ?: return null
        val side = m.entrySide ?: return null
        return Triple(cm, side, false)
    }

    fun newImage(path: String) {
        imagePath = path
        restart()
    }

    fun measurement(): ImageMeasurement? {
        val e = marks[Mark.ENTRY] ?: return null
        val t = marks[Mark.TARGET] ?: return null
        val a = marks[Mark.SCALE_A] ?: return null
        val b = marks[Mark.SCALE_B] ?: return null
        val cm = scaleCm.num()?.takeIf { it > 0 } ?: return null
        return runCatching { ImageGeometry.measure(ImageMarks(e, t, a, b, cm, midline = marks[Mark.MIDLINE])) }.getOrNull()
    }

    fun plan(m: ImageMeasurement?): NeedlePlan? {
        if (m == null || m.verticalDepthCm <= 0) return null
        val cc = (ccCm.num() ?: 0.0) * if (ccCaudal) 1 else -1
        val depth = if (manualDepth) {
            depthOverride.num()?.takeIf { it > 0 } ?: return null
        } else null
        val angle = if (manualAngle) {
            (angleOverride.num() ?: return null) * if (angleTowardLeft) 1 else -1
        } else null
        return runCatching { PlanCalculator.fromDeltas(m.dxCm, m.verticalDepthCm, cc, depth, angle) }.getOrNull()
    }

    companion object {
        val Saver = listSaver<ImagePlanState, Any>(
            save = { s ->
                val flat = Mark.entries.flatMap { m ->
                    s.marks[m]?.let { listOf(it.x, it.y) } ?: listOf(Double.NaN, Double.NaN)
                }
                listOf(
                    s.imagePath ?: "", s.history.joinToString(",") { it.name }, s.scaleCm, s.scaleConfirmed,
                    s.midlineSkipped, s.ccCm, s.ccCaudal, s.manualDepth, s.depthOverride, s.manualAngle,
                    s.angleOverride, s.angleTowardLeft, s.midlineManual, s.midlineCm, s.midlineLeft,
                ) + flat
            },
            restore = { l ->
                runCatching {
                    ImagePlanState().apply {
                        imagePath = (l[0] as String).ifEmpty { null }
                        (l[1] as String).split(',').filter { it.isNotEmpty() }.forEach { history.add(Mark.valueOf(it)) }
                        scaleCm = l[2] as String; scaleConfirmed = l[3] as Boolean; midlineSkipped = l[4] as Boolean
                        ccCm = l[5] as String; ccCaudal = l[6] as Boolean
                        manualDepth = l[7] as Boolean; depthOverride = l[8] as String
                        manualAngle = l[9] as Boolean; angleOverride = l[10] as String
                        angleTowardLeft = l[11] as Boolean
                        midlineManual = l[12] as Boolean; midlineCm = l[13] as String; midlineLeft = l[14] as Boolean
                        Mark.entries.forEachIndexed { i, m ->
                            val x = l[15 + 2 * i] as Double
                            val y = l[16 + 2 * i] as Double
                            if (!x.isNaN()) marks[m] = Px(x, y)
                        }
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
    var showCorrections by remember { mutableStateOf(false) }

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
    }

    val step = if (image == null) Step.PHOTO else state.step
    val measurement = state.measurement()
    val plan = state.plan(measurement)
    val stepNo = Step.entries.indexOf(step) + 1

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("toraksB", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onManual) { Text("Elle giriş") }
        }

        // Tek talimat: şu an ne yapılacağı
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (step == Step.DONE) "Plan hazır" else "Adım $stepNo / ${Step.entries.size - 1} · ${step.title}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                if (step.hint.isNotEmpty()) Text(step.hint)
                if (loadError) Text("Fotoğraf açılamadı. Tekrar deneyin.", color = MaterialTheme.colorScheme.error)
            }
        }

        val img = image
        if (img == null) {
            Button(onClick = ::takePhoto, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Ekranı fotoğrafla") }
            OutlinedButton(onClick = ::pickPhoto, modifier = Modifier.fillMaxWidth()) { Text("Galeriden seç") }
        } else {
            MarkCanvas(img, state)

            when (step) {
                Step.SCALE_LEN -> ScaleLengthInput(state)
                Step.MIDLINE -> MidlineInput(state)
                else -> Unit
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = state::undo,
                    enabled = state.history.isNotEmpty() || state.midlineSkipped || state.midlineManual,
                ) { Text("Geri al") }
                OutlinedButton(onClick = ::takePhoto) { Text("Yeni fotoğraf") }
            }

            if (step == Step.DONE) {
                ResultCard(measurement, plan, state.skinMark(measurement))
                TextButton(onClick = { showCorrections = !showCorrections }) {
                    Text(if (showCorrections) "Düzeltmeleri gizle" else "Derinliği / açıyı düzelt, kesit farkı ekle")
                }
                if (showCorrections) Corrections(state)

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
private fun ScaleLengthInput(state: ImagePlanState) {
    val valid = state.scaleCm.num()?.let { it > 0 } == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.scaleCm,
            onValueChange = { v -> state.scaleCm = v.filter { it.isDigit() || it == ',' || it == '.' } },
            label = { Text("Uzunluk (cm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { state.scaleConfirmed = true }, enabled = valid) { Text("Devam") }
    }
}

@Composable
private fun ResultCard(m: ImageMeasurement?, plan: NeedlePlan?, skin: Triple<Double, Side, Boolean>?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (m == null || plan == null) {
                Text(
                    if (m != null && m.verticalDepthCm <= 0)
                        "Kitle giriş noktasının üstünde görünüyor. Noktaları kontrol edin (Geri al)."
                    else "Plan hesaplanamadı. Noktaları kontrol edin.",
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            BigValue("Derinlik", "${plan.depthCm.f1()} cm", if (plan.depthIsManual) "elle girildi" else null)
            BigValue(
                "Açı", "${abs(plan.axialAngleDeg).f1()}°",
                "iğne ucu ${lateralWord(plan.axialAngleDeg).lowercase()}" + if (plan.axialAngleIsManual) " · elle girildi" else "",
            )
            if (abs(plan.craniocaudalAngleDeg) >= 0.05) {
                BigValue("Kranyokaudal", "${abs(plan.craniocaudalAngleDeg).f1()}°", ccWord(plan.craniocaudalAngleDeg).lowercase())
            }
            skin?.let { (cm, side, manual) ->
                BigValue(
                    "Cilt işareti", "${cm.f1()} cm",
                    "orta hattın ${side.label.lowercase()} tarafında" + if (manual) " · elle girildi" else "",
                )
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
private fun Corrections(s: ImagePlanState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ToggleLine("Orta hat – giriş mesafesini elle gir", s.midlineManual) { s.midlineManual = it }
        if (s.midlineManual) {
            NumberField("Orta hattan giriş noktasına (cm)", s.midlineCm) { s.midlineCm = it }
            ToggleLine("Giriş hastanın solunda (kapalı: sağında)", s.midlineLeft) { s.midlineLeft = it }
        }
        ToggleLine("Derinliği elle gir", s.manualDepth) { s.manualDepth = it }
        if (s.manualDepth) NumberField("İğne yolu uzunluğu (cm)", s.depthOverride) { s.depthOverride = it }
        ToggleLine("Açıyı elle gir", s.manualAngle) { s.manualAngle = it }
        if (s.manualAngle) {
            NumberField("Açı (derece)", s.angleOverride) { s.angleOverride = it }
            ToggleLine("İğne ucu hastanın soluna (kapalı: sağına)", s.angleTowardLeft) { s.angleTowardLeft = it }
        }
        NumberField("Kitle ile giriş arası kesit farkı (cm)", s.ccCm) { s.ccCm = it }
        ToggleLine("Kitle girişten kaudalde (kapalı: kranialde)", s.ccCaudal) { s.ccCaudal = it }
    }
}

@Composable
private fun ToggleLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        isError = value.isNotEmpty() && value.num() == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MarkCanvas(img: ImageBitmap, state: ImagePlanState) {
    var zoom by remember(img) { mutableFloatStateOf(1f) }
    var pan by remember(img) { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds()
                .background(Color.Black, RoundedCornerShape(8.dp))
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(img) {
                        detectTransformGestures { centroid, dPan, dZoom, _ ->
                            val oldZ = zoom
                            val newZ = (oldZ * dZoom).coerceIn(1f, 12f)
                            val f = newZ / oldZ
                            val c = centroid - Offset(size.width / 2f, size.height / 2f)
                            zoom = newZ
                            pan = c - (c - pan) * f + dPan
                        }
                    }
                    .pointerInput(img) {
                        detectTapGestures { o ->
                            val v = Viewport(size.width.toFloat(), size.height.toFloat(), img.width, img.height, zoom, pan)
                            val p = v.toImage(o)
                            if (p.x in 0.0..img.width.toDouble() && p.y in 0.0..img.height.toDouble()) state.place(p)
                        }
                    },
            ) {
                if (size.width < 1f || size.height < 1f) return@Canvas
                val v = Viewport(size.width, size.height, img.width, img.height, zoom, pan)
                val tl = v.toScreen(Px(0.0, 0.0))
                val w = (img.width * v.scale).toInt().coerceAtLeast(1)
                val h = (img.height * v.scale).toInt().coerceAtLeast(1)
                drawImage(
                    img,
                    dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                    dstSize = IntSize(w, h),
                    filterQuality = if (v.scale > 2f) FilterQuality.None else FilterQuality.Medium,
                )
                drawOverlay(v, state, measurer)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "İki parmakla yakınlaştırın, hassas dokunun.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            )
            if (zoom > 1.01f) TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Sığdır") }
        }
    }
}

/** Görüntü ↔ ekran dönüşümü. */
private class Viewport(w: Float, h: Float, iw: Int, ih: Int, zoom: Float, pan: Offset) {
    val scale = minOf(w / iw, h / ih) * zoom
    private val cx = w / 2 + pan.x
    private val cy = h / 2 + pan.y
    private val icx = iw / 2.0
    private val icy = ih / 2.0
    fun toScreen(p: Px) = Offset((cx + (p.x - icx) * scale).toFloat(), (cy + (p.y - icy) * scale).toFloat())
    fun toImage(o: Offset) = Px(icx + (o.x - cx) / scale, icy + (o.y - cy) / scale)
}

private fun DrawScope.drawOverlay(v: Viewport, s: ImagePlanState, tm: TextMeasurer) {
    fun pt(k: Mark) = s.marks[k]?.let(v::toScreen)
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))

    val sa = pt(Mark.SCALE_A); val sb = pt(Mark.SCALE_B)
    if (sa != null && sb != null) drawLine(Mark.SCALE_A.color, sa, sb, 3f)
    val e = pt(Mark.ENTRY); val t = pt(Mark.TARGET)
    if (e != null) drawLine(Color.White.copy(alpha = 0.6f), e, Offset(e.x, size.height), 2f, pathEffect = dash)
    if (e != null && t != null) drawLine(Mark.TARGET.color, e, t, 4f)
    pt(Mark.MIDLINE)?.let { m ->
        drawLine(Mark.MIDLINE.color.copy(alpha = 0.8f), Offset(m.x, 0f), Offset(m.x, size.height), 2f, pathEffect = dash)
    }

    Mark.entries.forEach { k ->
        val p = pt(k) ?: return@forEach
        drawCircle(Color.Black.copy(alpha = 0.6f), 18f, p, style = Stroke(6f))
        drawCircle(k.color, 18f, p, style = Stroke(3f))
        drawCircle(k.color, 2.5f, p)
        safeLabel(tm, k.label, p + Offset(22f, -30f), k.color)
    }
}

/**
 * Ekran dışına taşan etiketler çizilmez. (drawText'e kalan genişlik negatif verilirse
 * Compose istisna fırlatıp uygulamayı kapatır; önceki sürümdeki çökme buydu.)
 */
private fun DrawScope.safeLabel(tm: TextMeasurer, text: String, at: Offset, color: Color) {
    val layout = tm.measure(
        text,
        TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, background = Color.Black.copy(alpha = 0.55f)),
    )
    val x = at.x.coerceIn(0f, max(0f, size.width - layout.size.width))
    val y = at.y.coerceIn(0f, max(0f, size.height - layout.size.height))
    if (at.x > size.width || at.y > size.height || at.x < -layout.size.width || at.y < -layout.size.height) return
    drawText(layout, topLeft = Offset(x, y))
}

@Composable
private fun MidlineInput(s: ImagePlanState) {
    var cm by remember { mutableStateOf(s.midlineCm) }
    var left by remember { mutableStateOf(s.midlineLeft) }
    val valid = cm.num() != null
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ya da mesafeyi elle girin", fontWeight = FontWeight.SemiBold)
            NumberField("Orta hattan giriş noktasına (cm)", cm) { cm = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SideButton("Hastanın sağı", !left, Modifier.weight(1f)) { left = false }
                SideButton("Hastanın solu", left, Modifier.weight(1f)) { left = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { s.midlineSkipped = true }, modifier = Modifier.weight(1f)) { Text("Atla") }
                Button(
                    onClick = { s.midlineCm = cm; s.midlineLeft = left; s.midlineManual = true },
                    enabled = valid, modifier = Modifier.weight(1f),
                ) { Text("Kaydet") }
            }
        }
    }
}

@Composable
private fun SideButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}
