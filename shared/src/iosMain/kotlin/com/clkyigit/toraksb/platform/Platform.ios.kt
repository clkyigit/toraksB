package com.clkyigit.toraksb.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.clkyigit.toraksb.data.StudyFormat
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min

private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

/** Fotoğrafı EXIF yönü uygulanmış ve en uzun kenarı en çok 2048 px olacak şekilde JPEG olarak kaydeder. */
@OptIn(ExperimentalForeignApi::class)
private fun saveNormalized(image: UIImage): String? {
    val (w, h) = image.size.useContents { width to height }
    if (w <= 0.0 || h <= 0.0) return null
    val k = min(1.0, 2048.0 / max(w, h))
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(w * k, h * k), format = format)
    val normalized = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, w * k, h * k)) }
    val data = UIImageJPEGRepresentation(normalized, 0.92) ?: return null
    val path = NSTemporaryDirectory() + "bt_" + NSDate().timeIntervalSince1970.toLong() + ".jpg"
    return if (data.writeToFile(path, true)) path else null
}

private class PickerDelegate(
    val onImage: (String) -> Unit,
    val onError: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, null)
        val path = image?.let { saveNormalized(it) }
        if (path != null) onImage(path) else onError()
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

@Composable
actual fun rememberPhotoSource(onImage: (path: String) -> Unit, onError: () -> Unit): PhotoSource {
    val currentOnImage by rememberUpdatedState(onImage)
    val currentOnError by rememberUpdatedState(onError)
    return remember {
        object : PhotoSource {
            // UIImagePickerController delegate'i zayıf tutar; burada güçlü referans saklanır
            private val delegate = PickerDelegate({ currentOnImage(it) }, { currentOnError() })

            private fun present(source: UIImagePickerControllerSourceType) {
                if (!UIImagePickerController.isSourceTypeAvailable(source)) {
                    currentOnError(); return
                }
                val picker = UIImagePickerController()
                picker.sourceType = source
                picker.delegate = delegate
                val top = topViewController()
                if (top == null) currentOnError() else top.presentViewController(picker, animated = true, completion = null)
            }

            override fun takePhoto() =
                present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)

            override fun pickPhoto() =
                present(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun decodeImage(path: String): ImageBitmap? = withContext(Dispatchers.Default) {
    runCatching {
        val data = NSData.dataWithContentsOfFile(path) ?: return@runCatching null
        val size = data.length.toInt()
        if (size == 0) return@runCatching null
        val bytes = ByteArray(size)
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private fun studyPath(): String {
    val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
    return "$docs/toraksB_calisma_verisi.csv"
}

@OptIn(ExperimentalForeignApi::class)
private fun readStudy(): String? = NSString.stringWithContentsOfFile(studyPath(), NSUTF8StringEncoding, null)

@OptIn(ExperimentalForeignApi::class)
private fun writeStudy(text: String): Boolean =
    NSString.create(string = text).writeToFile(studyPath(), true, NSUTF8StringEncoding, null)

private object IosStudyStore : StudyStore {
    override fun append(values: Map<String, String>) {
        val existing = readStudy().orEmpty()
        val sb = StringBuilder(existing)
        if (existing.isBlank()) sb.append(StudyFormat.BOM).append(StudyFormat.headerLine()).append('\n')
        sb.append(StudyFormat.rowLine(values)).append('\n')
        check(writeStudy(sb.toString())) { "Dosyaya yazılamadı" }
    }

    override fun records(): List<Map<String, String>> = readStudy()?.let(StudyFormat::records).orEmpty()

    @OptIn(ExperimentalForeignApi::class)
    override fun clear() {
        NSFileManager.defaultManager.removeItemAtPath(studyPath(), null)
    }

    override fun share(): String? {
        if (records().isEmpty()) return "Paylaşılacak kayıt yok."
        val top = topViewController() ?: return "Paylaşım penceresi açılamadı."
        val controller = UIActivityViewController(
            activityItems = listOf(NSURL.fileURLWithPath(studyPath())),
            applicationActivities = null,
        )
        controller.popoverPresentationController?.sourceView = top.view // iPad
        top.presentViewController(controller, animated = true, completion = null)
        return null
    }
}

@Composable
actual fun rememberStudyStore(): StudyStore = IosStudyStore

@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS'ta sistem geri tuşu yok; ekranlardaki geri düğmeleri kullanılır.
}

actual fun nowTimestamp(): String = NSDateFormatter().apply {
    dateFormat = "yyyy-MM-dd HH:mm:ss"
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
}.stringFromDate(NSDate())

@Composable
actual fun rememberCrashReport(): CrashReport? = null
