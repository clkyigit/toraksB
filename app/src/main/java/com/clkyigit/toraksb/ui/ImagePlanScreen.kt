@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.clkyigit.toraksb.geometry.TableCalibration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/** Görüntüde işaretlenen noktalar; sıra, otomatik ilerleme sırasıdır. */
enum class Mark(val label: String, val color: Color, val required: Boolean) {
    SCALE_A("Ölçek 1", Color(0xFFFFC400), true),
    SCALE_B("Ölçek 2", Color(0xFFFFC400), true),
    ENTRY("Giriş", Color(0xFF00E5FF), true),
    TARGET("Kitle", Color(0xFFFF4081), true),
    MIDLINE("Orta hat", Color(0xFFB2FF59), false),
    TABLE_A("Masa 1", Color(0xFFBDBDBD), false),
    TABLE_B("Masa 2", Color(0xFFBDBDBD), false),
}

class ImagePlanState {
    var imagePath by mutableStateOf<String?>(null)
    val marks = mutableStateMapOf<Mark, Px>()
    var active by mutableStateOf(Mark.SCALE_A)
    var scaleCm by mutableStateOf("")
    var ccCm by mutableStateOf("0")
    var ccCaudal by mutableStateOf(true)
    var manualDepth by mutableStateOf(false)
    var depthOverride by mutableStateOf("")
    var manualAngle by mutableStateOf(false)
    var angleOverride by mutableStateOf("")
    var angleTowardLeft by mutableStateOf(true)

    fun place(p: Px) {
        marks[active] = p
        Mark.entries.firstOrNull { it.required && it !in marks }?.let { active = it }
    }

    fun measurement(): ImageMeasurement? {
        val e = marks[Mark.ENTRY] ?: return null
        val t = marks[Mark.TARGET] ?: return null
        val a = marks[Mark.SCALE_A] ?: return null
        val b = marks[Mark.SCALE_B] ?: return null
        val cm = scaleCm.num()?.takeIf { it > 0 } ?: return null
        return runCatching {
            ImageGeometry.measure(
                ImageMarks(e, t, a, b, cm, marks[Mark.TABLE_A], marks[Mark.TABLE_B], marks[Mark.MIDLINE])
            )
        }.getOrNull()
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
        return PlanCalculator.fromDeltas(m.dxCm, m.verticalDepthCm, cc, depth, angle)
    }

    fun newImage(path: String) {
        imagePath = path
        marks.clear()
        active = Mark.SCALE_A
    }

    companion object {
        val Saver = listSaver<ImagePlanState, Any>(
            save = { s ->
                val flat = Mark.entries.flatMap { m -> s.marks[m]?.let { listOf(it.x, it.y) } ?: listOf(Double.NaN, Double.NaN) }
                listOf(s.imagePath ?: "", s.active.name, s.scaleCm, s.ccCm, s.ccCaudal, s.manualDepth,
                    s.depthOverride, s.manualAngle, s.angleOverride, s.angleTowardLeft) + flat
            },
            restore = { l ->
                ImagePlanState().apply {
                    imagePath = (l[0] as String).ifEmpty { null }
                    active = Mark.valueOf(l[1] as String)
                    scaleCm = l[2] as String; ccCm = l[3] as String; ccCaudal = l[4] as Boolean
                    manualDepth = l[5] as Boolean; depthOverride = l[6] as String
                    manualAngle = l[7] as Boolean; angleOverride = l[8] as String
                    angleTowardLeft = l[9] as Boolean
                    Mark.entries.forEachIndexed { i, m ->
                        val x = l[10 + 2 * i] as Double; val y = l[11 + 2 * i] as Double
                        if (!x.isNaN()) marks[m] = Px(x, y)
                    }
                }
            },
        )
    }
}

private fun photoFile(context: Context): File =
    File(context.cacheDir, "photos").apply { mkdirs() }
        .resolve("bt_${System.currentTimeMillis()}.jpg")

private fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    val out = photoFile(context)
    context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
    out.absolutePath
}.getOrNull()

/** EXIF yönünü uygular; çok büyük fotoğrafları en uzun kenar 2560 px olacak şekilde küçültür. */
private fun decode(path: String): ImageBitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > 2560) {
            val k = 2560.0 / longest
            decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
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

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pendingCamera
        if (ok && f != null && f.length() > 0) state.newImage(f.absolutePath)
        pendingCamera = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) copyToCache(context, uri)?.let(state::newImage)
    }

    LaunchedEffect(state.imagePath) {
        val p = state.imagePath
        image = if (p == null) null else withContext(Dispatchers.IO) { decode(p) }
        loadError = p != null && image == null
    }

    val measurement = state.measurement()
    val plan = state.plan(measurement)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "toraksB — BT görüntüsünden plan", Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onManual) { Text("Elle giriş") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val f = photoFile(context)
                pendingCamera = f
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                camera.launch(uri)
            }) { Text("Ekranı fotoğrafla") }
            OutlinedButton(onClick = {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text("Galeriden seç") }
        }

        val img = image
        if (img == null) {
            Card {
                Text(
                    if (loadError) "Fotoğraf açılamadı. Tekrar çekin ya da başka bir görüntü seçin."
                    else "BT konsolunda giriş noktasının ve kitlenin göründüğü aksiyel kesiti açın. " +
                        "Ekrana tam karşıdan, eğmeden fotoğraf çekin. Görüntüde konsol cetveli ya da " +
                        "konsolda çizdiğiniz bir mesafe ölçümü görünsün; ölçek için kullanılacak.",
                    Modifier.padding(16.dp),
                )
            }
        } else {
            MarkChips(state)
            Text(stepHint(state), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            MarkCanvas(img, state, measurement)
            Text(
                "İki parmakla yakınlaştırıp kaydırın; seçili noktayı yerleştirmek için dokunun.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.scaleCm,
                onValueChange = { v -> state.scaleCm = v.filter { it.isDigit() || it == ',' || it == '.' } },
                label = { Text("Ölçek çizgisinin gerçek uzunluğu (cm)") },
                isError = state.scaleCm.isNotEmpty() && state.scaleCm.num() == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            MeasurementCard(measurement, plan)
            if (measurement != null && measurement.verticalDepthCm <= 0) {
                Text(
                    "Kitle giriş noktasının üstünde görünüyor. Noktaları ya da görüntü yönünü kontrol edin.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            ExtraInputs(state)
        }

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
        DisclaimerCard()
        Spacer(Modifier.height(24.dp))
    }
}

private fun stepHint(s: ImagePlanState): String = when (s.active) {
    Mark.SCALE_A -> "Ölçek: uzunluğu bilinen çizginin bir ucuna dokunun"
    Mark.SCALE_B -> "Ölçek: aynı çizginin diğer ucuna dokunun"
    Mark.ENTRY -> "Cilt giriş noktasına dokunun"
    Mark.TARGET -> "Kitlenin hedeflenecek noktasına dokunun"
    Mark.MIDLINE -> "İsteğe bağlı: orta hatta (ör. vertebra korpusu ortası) dokunun"
    Mark.TABLE_A -> "İsteğe bağlı: masanın üst kenarında bir noktaya dokunun"
    Mark.TABLE_B -> "İsteğe bağlı: masanın üst kenarında ikinci bir noktaya dokunun"
}

@Composable
private fun MarkChips(state: ImagePlanState) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Mark.entries.forEach { m ->
            FilterChip(
                selected = state.active == m,
                onClick = { state.active = m },
                label = { Text(if (m in state.marks) "✓ ${m.label}" else m.label) },
                leadingIcon = { Box(Modifier.padding(2.dp).height(10.dp).aspectRatio(1f).background(m.color)) },
            )
        }
        TextButton(onClick = { state.marks.remove(state.active) }, enabled = state.active in state.marks) {
            Text("Seçiliyi sil")
        }
    }
}

@Composable
private fun MarkCanvas(img: ImageBitmap, state: ImagePlanState, m: ImageMeasurement?) {
    var zoom by remember(img) { mutableFloatStateOf(1f) }
    var pan by remember(img) { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clipToBounds().background(Color.Black)
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInputTransform(img, { zoom }, { pan }) { z, p -> zoom = z; pan = p }
                .pointerInputTap(img, { zoom }, { pan }) { px -> state.place(px) },
        ) {
            val v = Viewport(size.width, size.height, img.width, img.height, zoom, pan)
            val tl = v.toScreen(Px(0.0, 0.0))
            drawImage(
                img,
                dstOffset = IntOffset(tl.x.toInt(), tl.y.toInt()),
                dstSize = IntSize((img.width * v.scale).toInt(), (img.height * v.scale).toInt()),
                filterQuality = if (v.scale > 2f) FilterQuality.None else FilterQuality.Medium,
            )
            drawOverlay(v, state, m, measurer)
        }
    }
}

/** Görüntü ↔ ekran dönüşümü: sığdır, sonra kullanıcı yakınlaştırması ve kaydırması. */
private class Viewport(w: Float, h: Float, iw: Int, ih: Int, zoom: Float, pan: Offset) {
    val scale = minOf(w / iw, h / ih) * zoom
    private val cx = w / 2 + pan.x
    private val cy = h / 2 + pan.y
    private val icx = iw / 2.0
    private val icy = ih / 2.0
    fun toScreen(p: Px) = Offset((cx + (p.x - icx) * scale).toFloat(), (cy + (p.y - icy) * scale).toFloat())
    fun toImage(o: Offset) = Px(icx + (o.x - cx) / scale, icy + (o.y - cy) / scale)
}

private fun Modifier.pointerInputTransform(
    key: Any, zoom: () -> Float, pan: () -> Offset, onChange: (Float, Offset) -> Unit,
) = pointerInput(key) {
    detectTransformGestures { centroid, dPan, dZoom, _ ->
        val oldZ = zoom()
        val newZ = (oldZ * dZoom).coerceIn(1f, 16f)
        val f = newZ / oldZ
        val c = centroid - Offset(size.width / 2f, size.height / 2f)
        onChange(newZ, c - (c - pan()) * f + dPan)
    }
}

private fun Modifier.pointerInputTap(
    img: ImageBitmap, zoom: () -> Float, pan: () -> Offset, onTap: (Px) -> Unit,
) = pointerInput(img) {
    detectTapGestures { o ->
        val v = Viewport(size.width.toFloat(), size.height.toFloat(), img.width, img.height, zoom(), pan())
        val p = v.toImage(o)
        if (p.x in 0.0..img.width.toDouble() && p.y in 0.0..img.height.toDouble()) onTap(p)
    }
}

private fun DrawScope.drawOverlay(v: Viewport, s: ImagePlanState, m: ImageMeasurement?, tm: TextMeasurer) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
    fun pt(k: Mark) = s.marks[k]?.let(v::toScreen)

    val sa = pt(Mark.SCALE_A); val sb = pt(Mark.SCALE_B)
    if (sa != null && sb != null) drawLine(Mark.SCALE_A.color, sa, sb, 3f)
    val ta = pt(Mark.TABLE_A); val tb = pt(Mark.TABLE_B)
    if (ta != null && tb != null) drawLine(Mark.TABLE_A.color, ta, tb, 2f, pathEffect = dash)

    val e = pt(Mark.ENTRY); val t = pt(Mark.TARGET)
    if (e != null) {
        // Masaya dik referans: masa hattı varsa ona dik, yoksa fotoğrafın dikeyi
        val dir = if (ta != null && tb != null) {
            val d = tb - ta; val len = d.getDistance()
            val hx = if (d.x >= 0) d.x / len else -d.x / len
            val hy = if (d.x >= 0) d.y / len else -d.y / len
            Offset(-hy, hx)
        } else Offset(0f, 1f)
        drawLine(Color.White.copy(alpha = 0.7f), e, e + dir * (size.height), 2f, pathEffect = dash)
    }
    if (e != null && t != null) drawLine(Mark.TARGET.color, e, t, 4f)
    pt(Mark.MIDLINE)?.let { mid ->
        drawLine(Mark.MIDLINE.color.copy(alpha = 0.8f), Offset(mid.x, 0f), Offset(mid.x, size.height), 2f, pathEffect = dash)
    }

    Mark.entries.forEach { k ->
        val p = pt(k) ?: return@forEach
        val active = s.active == k
        drawCircle(Color.Black.copy(alpha = 0.6f), if (active) 20f else 16f, p, style = Stroke(6f))
        drawCircle(k.color, if (active) 20f else 16f, p, style = Stroke(3f))
        drawCircle(k.color, 2.5f, p)
        drawText(
            tm, k.label, topLeft = p + Offset(22f, -34f),
            style = TextStyle(color = k.color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                background = Color.Black.copy(alpha = 0.55f)),
        )
    }
    if (m != null && e != null && t != null) {
        val mid = (e + t) / 2f
        drawText(
            tm, "${(kotlin.math.sqrt(m.dxCm * m.dxCm + m.verticalDepthCm * m.verticalDepthCm)).f1()} cm",
            topLeft = mid + Offset(12f, 0f),
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                background = Color.Black.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun MeasurementCard(m: ImageMeasurement?, plan: NeedlePlan?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (m == null || plan == null) {
                Text("Ölçek iki ucu, ölçek uzunluğu, giriş noktası ve kitle işaretlenince plan burada görünür.")
                return@Column
            }
            Text("Plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            m.entryFromMidlineCm?.let {
                Text("Cilt işareti: orta hattan ${it.f1()} cm, hastanın ${m.entrySide?.label?.lowercase()} tarafı")
            }
            Text("Yatay mesafe: ${abs(m.dxCm).f1()} cm ${lateralWord(m.dxCm).lowercase()} · dikey derinlik: ${m.verticalDepthCm.f1()} cm")
            Text(
                "Aksiyel açı: ${abs(plan.axialAngleDeg).f1()}° — iğne ucu ${lateralWord(plan.axialAngleDeg)}" +
                    if (plan.axialAngleIsManual) "  (elle; görüntüden ${plan.calculatedAxialAngleDeg.f1()}°)" else ""
            )
            if (abs(plan.craniocaudalAngleDeg) >= 0.05) {
                Text("Kranyokaudal açı: ${abs(plan.craniocaudalAngleDeg).f1()}° — ${ccWord(plan.craniocaudalAngleDeg)}")
            }
            Text(
                "Derinlik: ${plan.depthCm.f1()} cm" +
                    if (plan.depthIsManual) "  (elle; görüntüden ${plan.calculatedDepthCm.f1()} cm)" else "",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            if (abs(m.tableTiltDeg) > 0.05) {
                Text("Fotoğraf masaya göre ${abs(m.tableTiltDeg).f1()}° eğik; düzeltildi.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ExtraInputs(s: ImagePlanState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kranyokaudal fark (isteğe bağlı)", fontWeight = FontWeight.SemiBold)
        NumberField("Giriş ile kitle kesitleri arası (cm)", s.ccCm) { s.ccCm = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kitle girişe göre", Modifier.weight(1f))
            FilterChip(selected = s.ccCaudal, onClick = { s.ccCaudal = true }, label = { Text("Kaudalde") })
            Spacer(Modifier.padding(4.dp))
            FilterChip(selected = !s.ccCaudal, onClick = { s.ccCaudal = false }, label = { Text("Kranialde") })
        }
        Text("Doktor düzeltmesi", fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Derinliği elle gir", Modifier.weight(1f))
            Switch(checked = s.manualDepth, onCheckedChange = { s.manualDepth = it })
        }
        if (s.manualDepth) NumberField("İğne yolu uzunluğu (cm)", s.depthOverride) { s.depthOverride = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Aksiyel açıyı elle gir", Modifier.weight(1f))
            Switch(checked = s.manualAngle, onCheckedChange = { s.manualAngle = it })
        }
        if (s.manualAngle) {
            NumberField("Açı (derece, masaya dik eksenden)", s.angleOverride) { s.angleOverride = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("İğne ucu", Modifier.weight(1f))
                FilterChip(selected = s.angleTowardLeft, onClick = { s.angleTowardLeft = true }, label = { Text("Hastanın soluna") })
                Spacer(Modifier.padding(4.dp))
                FilterChip(selected = !s.angleTowardLeft, onClick = { s.angleTowardLeft = false }, label = { Text("Hastanın sağına") })
            }
        }
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
