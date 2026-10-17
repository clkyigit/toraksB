package com.clkyigit.toraksb.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Uygulama sürümü (çalışma verisine ve hata kaydına yazılır). */
object BuildInfo {
    const val VERSION = "1.0.0"
}

/** Kamera / galeriden BT ekranı fotoğrafı alma. Sonuç uygulamanın kendi klasörüne kopyalanmış dosya yoludur. */
interface PhotoSource {
    fun takePhoto()
    fun pickPhoto()
}

@Composable
expect fun rememberPhotoSource(onImage: (path: String) -> Unit, onError: () -> Unit): PhotoSource

/** Fotoğrafı yönünü düzelterek ve en uzun kenarı 2048 px'i geçmeyecek şekilde açar. */
expect suspend fun decodeImage(path: String): ImageBitmap?

/** Çalışma verisi deposu (CSV). Biçim: StudyFormat. */
interface StudyStore {
    fun append(values: Map<String, String>)
    /** Kayıtlar, eskiden yeniye. */
    fun records(): List<Map<String, String>>
    fun clear()
    /** Paylaşım penceresini açar; hata olursa açıklama döner. */
    fun share(): String?
}

@Composable
expect fun rememberStudyStore(): StudyStore

/** Bu ekran açıkken telefonun kilitlenmesini engeller. */
@Composable
expect fun KeepScreenOn()

/** Sistem geri tuşu (Android). iOS'ta ekrandaki düğmeler kullanılır. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

/** "yyyy-MM-dd HH:mm:ss" yerel saat. */
expect fun nowTimestamp(): String

/** Önceki açılışta kaydedilmiş çökme raporu (yalnız Android'de tutulur). */
interface CrashReport {
    val text: String
    fun clear()
}

@Composable
expect fun rememberCrashReport(): CrashReport?
