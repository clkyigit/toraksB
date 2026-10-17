package com.clkyigit.toraksb.platform

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private fun photoFile(context: Context): File =
    File(context.cacheDir, "photos").apply { mkdirs() }.resolve("bt_${System.currentTimeMillis()}.jpg")

private fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    val out = photoFile(context)
    context.contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
    out.absolutePath
}.getOrNull()

@Composable
actual fun rememberPhotoSource(onImage: (path: String) -> Unit, onError: () -> Unit): PhotoSource {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = pending
        if (ok && f != null && f.length() > 0) onImage(f.absolutePath)
        pending = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) copyToCache(context, uri)?.let(onImage) ?: onError()
    }
    return remember(camera, gallery) {
        object : PhotoSource {
            override fun takePhoto() {
                runCatching {
                    val f = photoFile(context)
                    pending = f
                    camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
                }.onFailure { onError() }
            }

            override fun pickPhoto() {
                runCatching {
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }.onFailure { onError() }
            }
        }
    }
}

actual suspend fun decodeImage(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > 2048) {
                val k = 2048.0 / longest
                decoder.setTargetSize(
                    (info.size.width * k).toInt().coerceAtLeast(1),
                    (info.size.height * k).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }.asImageBitmap()
    }.getOrNull()
}

@Composable
actual fun rememberStudyStore(): StudyStore {
    val context = LocalContext.current
    return remember(context) {
        object : StudyStore {
            override fun append(values: Map<String, String>) = StudyLog.append(context, values)
            override fun records(): List<Map<String, String>> = StudyLog.records(context)
            override fun clear() = StudyLog.clear(context)
            override fun share(): String? = runCatching {
                val i = StudyLog.shareIntent(context) ?: return@runCatching "Paylaşılacak kayıt yok."
                context.startActivity(i)
                null
            }.getOrElse { "Paylaşılamadı: ${it.message}" }
        }
    }
}

@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

actual fun nowTimestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

@Composable
actual fun rememberCrashReport(): CrashReport? {
    val context = LocalContext.current
    return remember(context) {
        CrashLog.read(context)?.let { t ->
            object : CrashReport {
                override val text = t
                override fun clear() = CrashLog.clear(context)
            }
        }
    }
}
