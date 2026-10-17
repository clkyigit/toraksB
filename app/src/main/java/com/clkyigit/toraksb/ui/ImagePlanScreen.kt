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
import com.clkyigit.toraksb.geometry.NeedlePlan
import com.clkyigit.toraksb.geometry.PlanCalculator
import com.clkyigit.toraksb.geometry.Px
import com.clkyigit.toraksb.geometry.TableCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Görüntüde işaretlenen iki nokta. */
enum class Mark(val label: String, val color: Color) {
    ENTRY("Giriş", Color(0xFF00E5FF)),
    TARGET("Lezyon", Color(0xFFFF4081)),
}

enum class Step(val title: String, val hint: String) {
    PHOTO("BT görüntüsü", "Konsolda lezyonun ve giriş noktasının göründüğü aksiyel kesiti açın, ekrana tam karşıdan fotoğraflayın."),
    ENTRY("Cilt giriş noktası", "İğnenin cilde gireceği noktaya dokunun."),
    TARGET("Lezyon", "Lezyonun ortasına dokunun."),
    VALUES("Ölçümler", "Konsolda ölçtüğünüz değerleri girin."),
    DONE("Plan hazır", ""),
}

class ImagePlanState {
    var imagePath by mutableStateOf<String?>(null)
    val marks = mutableStateMapOf<Mark, Px>()
    val history = mutableStateListOf<Mark>()

    /** Lezyon derinliği (cm) ve ölçüm biçimi. */
    var depthCm by mutableStateOf("")
    var depthAlongPath by mutableStateOf(true)
    /** Lezyon boyutu (cm) → tolerans açısı. */
    var lesionCm by mutableStateOf("")
    /** Orta hattan giriş noktasına (cm) ve taraf. */
    var midlineCm by mutableStateOf("")
    var midlineLeft by mutableStateOf(false)

    var ccCm by mutableStateOf("0")
    var ccCaudal by mutableStateOf(true)
    var manualAngle by mutableStateOf(false)
    var angleOverride by mutableStateOf("")
    var angleTowardLeft by mutableStateOf(true)

    val step: Step
        get() = when {
            imagePath == null -> Step.PHOTO
            Mark.ENTRY !in marks -> Step.ENTRY
            Mark.TARGET !in marks -> Step.TARGET
            depthCm.num()?.takeIf { it > 0 } == null -> Step.VALUES
            else -> Step.DONE
        }

    val placing: Mark?
        get() = when (step) {
            Step.ENTRY -> Mark.ENTRY
            Step.TARGET -> Mark.TARGET
            else -> null
        }

    /** Değerler girildikten sonra da noktalar yeniden yerleştirilebilir: seçili nokta. */
    var editing by mutableStateOf<Mark?>(null)

    fun place(p: Px) {
        val m = editing ?: placing ?: return
        marks[m] = p
        history.remove(m); history.add(m)
        editing = null
    }

    fun undo() {
        if (history.isNotEmpty()) marks.remove(history.removeAt(history.lastIndex))
        editing = null
    }

    fun newImage(path: String) {
        imagePath = path
        marks.clear(); history.clear(); editing = null
    }

    fun plan(): NeedlePlan? {
        val e = marks[Mark.ENTRY] ?: return null
        val t = marks[Mark.TARGET] ?: return null
        val depth = depthCm.num()?.takeIf { it > 0 } ?: return null
        val cc = (ccCm.num() ?: 0.0) * if (ccCaudal) 1 else -1
        val angle = if (manualAngle) {
            (angleOverride.num() ?: return null) * if (angleTowardLeft) 1 else -1
        } else null
        return runCatching {
            PlanCalculator.fromImageAngle(
                dxPx = t.x - e.x, dyPx = t.y - e.y,
                depthCm = depth, depthAlongPath = depthAlongPath,
                lesionSizeCm = lesionCm.num()?.takeIf { it > 0 },
                craniocaudalCm = cc, axialAngleOverrideDeg = angle,
            )
        }.getOrNull()
    }

    val lesionAboveEntry: Boolean
        get() {
            val e = marks[Mark.ENTRY] ?: return false
            val t = marks[Mark.TARGET] ?: return false
            return t.y <= e.y
        }

    companion object {
        val Saver = listSaver<ImagePlanState, Any>(
            save = { s ->
                val flat = Mark.entries.flatMap { m ->
                    s.marks[m]?.let { listOf(it.x, it.y) } ?: listOf(Double.NaN, Double.NaN)
                }
                listOf(
                    s.imagePath ?: "", s.history.joinToString(",") { it.name },
                    s.depthCm, s.depthAlongPath, s.lesionCm, s.midlineCm, s.midlineLeft,
                    s.ccCm, s.ccCaudal, s.manualAngle, s.angleOverride, s.angleTowardLeft,
                ) + flat
            },
            restore = { l ->
                runCatching {
                    ImagePlanState().apply {
                        imagePath = (l[0] as String).ifEmpty { null }
                        (l[1] as String).split(',').filter { it.isNotEmpty() }.forEach { history.add(Mark.valueOf(it)) }
                        depthCm = l[2] as String; depthAlongPath = l[3] as Boolean; lesionCm = l[4] as String
                        midlineCm = l[5] as String; midlineLeft = l[6] as Boolean
                        ccCm = l[7] as String; ccCaudal = l[8] as Boolean
                        manualAngle = l[9] as Boolean; angleOverride = l[10] as String; angleTowardLeft = l[11] as Boolean
                        Mark.entries.forEachIndexed { i, m ->
                            val x = l[12 + 2 * i] as Double
                            val y = l[13 + 2 * i] as Double
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
    }

    val step = if (image == null) Step.PHOTO else state.step
    val plan = state.plan()
    val stepNo = Step.entries.indexOf(step) + 1

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("toraksB", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onManual) { Text("Görüntüsüz giriş") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val editing = state.editing
                Text(
                    when {
                        editing != null -> "${editing.label} noktasını yeniden yerleştirin"
                        step == Step.DONE -> "Plan hazır"
                        else -> "Adım $stepNo / ${Step.entries.size - 1} · ${step.title}"
                    },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
                if (editing != null) Text("Görüntüde yeni yerine dokunun.")
                else if (step.hint.isNotEmpty()) Text(step.hint)
                if (loadError) Text("Fotoğraf açılamadı. Tekrar deneyin.", color = MaterialTheme.colorScheme.error)
            }
        }

        val img = image
        if (img == null) {
            Button(onClick = ::takePhoto, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Ekranı fotoğrafla") }
            OutlinedButton(onClick = ::pickPhoto, modifier = Modifier.fillMaxWidth()) { Text("Galeriden seç") }
        } else {
            MarkCanvas(img, state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = state::undo, enabled = state.history.isNotEmpty()) { Text("Geri al") }
                if (Mark.ENTRY in state.marks && Mark.TARGET in state.marks) {
                    OutlinedButton(onClick = { state.editing = Mark.ENTRY }) { Text("Girişi taşı") }
                    OutlinedButton(onClick = { state.editing = Mark.TARGET }) { Text("Lezyonu taşı") }
                }
            }
            TextButton(onClick = ::takePhoto) { Text("Yeni fotoğraf") }

            if (state.lesionAboveEntry) {
                Text(
                    "Lezyon giriş noktasının üstünde işaretlenmiş. Noktaları kontrol edin.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (step == Step.VALUES || step == Step.DONE) {
                ValuesForm(state)
            }

            if (step == Step.DONE) {
                ResultCard(plan, state)
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
private fun ValuesForm(s: ImagePlanState) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField("Lezyon derinliği (cm)", s.depthCm) { s.depthCm = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("İğne yolu boyunca", s.depthAlongPath, Modifier.weight(1f)) { s.depthAlongPath = true }
                ChoiceButton("Dikey", !s.depthAlongPath, Modifier.weight(1f)) { s.depthAlongPath = false }
            }
            NumberField(
                "Lezyon boyutu (cm)", s.lesionCm,
                hint = "Tolerans açısını belirler. Boş bırakılırsa ±1°.",
            ) { s.lesionCm = it }
            NumberField("Orta hattan giriş noktasına (cm)", s.midlineCm, hint = "Cilt işareti için, isteğe bağlı") { s.midlineCm = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceButton("Hastanın sağı", !s.midlineLeft, Modifier.weight(1f)) { s.midlineLeft = false }
                ChoiceButton("Hastanın solu", s.midlineLeft, Modifier.weight(1f)) { s.midlineLeft = true }
            }
        }
    }
}

@Composable
private fun ResultCard(plan: NeedlePlan?, s: ImagePlanState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (plan == null) {
                Text("Plan hesaplanamadı. Noktaları ve derinliği kontrol edin.", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            BigValue("İğne yolu", "${plan.depthCm.f1()} cm", if (s.depthAlongPath) "elle girildi" else "dikey derinlikten")
            BigValue(
                "Açı", "${abs(plan.axialAngleDeg).f1()}°",
                "iğne ucu ${lateralWord(plan.axialAngleDeg).lowercase()}" + if (plan.axialAngleIsManual) " · elle" else " · görüntüden",
            )
            BigValue(
                "Tolerans", "±${(plan.toleranceDeg ?: 1.0).f1()}°",
                plan.lesionSizeCm?.let { "${it.f1()} cm lezyon, ${plan.depthCm.f1()} cm yol" } ?: "varsayılan",
            )
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
                drawImage(
                    img,
                    dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                    dstSize = IntSize((img.width * v.scale).toInt().coerceAtLeast(1), (img.height * v.scale).toInt().coerceAtLeast(1)),
                    filterQuality = if (v.scale > 2f) FilterQuality.None else FilterQuality.Medium,
                )
                drawOverlay(v, state, measurer)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("İki parmakla yakınlaştırın, hassas dokunun.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            if (zoom > 1.01f) TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Sığdır") }
        }
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

private fun DrawScope.drawOverlay(v: Viewport, s: ImagePlanState, tm: TextMeasurer) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
    val e = s.marks[Mark.ENTRY]?.let(v::toScreen)
    val t = s.marks[Mark.TARGET]?.let(v::toScreen)
    if (e != null) drawLine(Color.White.copy(alpha = 0.6f), e, Offset(e.x, size.height), 2f, pathEffect = dash)
    if (e != null && t != null) drawLine(Mark.TARGET.color, e, t, 4f)
    Mark.entries.forEach { k ->
        val p = s.marks[k]?.let(v::toScreen) ?: return@forEach
        val active = s.editing == k
        drawCircle(Color.Black.copy(alpha = 0.6f), 18f, p, style = Stroke(6f))
        drawCircle(if (active) Color.White else k.color, 18f, p, style = Stroke(3f))
        drawCircle(k.color, 2.5f, p)
        safeLabel(tm, k.label, p + Offset(22f, -30f), k.color)
    }
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
