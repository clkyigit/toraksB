package com.clkyigit.toraksb

import android.content.Context
import java.io.File

/** Çökme olursa hata kaydını saklar; bir sonraki açılışta kullanıcıya gösterilir. */
object CrashLog {
    private fun file(c: Context) = File(c.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(
                    "toraksB ${BuildInfo.VERSION} · Android ${android.os.Build.VERSION.RELEASE} · " +
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n\n" +
                        error.stackTraceToString().take(6000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(c: Context): String? = runCatching { file(c).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(c: Context) {
        runCatching { file(c).delete() }
    }
}

object BuildInfo {
    const val VERSION = "0.9.0"
}
