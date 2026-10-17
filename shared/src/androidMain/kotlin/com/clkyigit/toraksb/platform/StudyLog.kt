package com.clkyigit.toraksb.platform

import com.clkyigit.toraksb.data.StudyFormat

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Çalışma verisinin Android'de saklanması ve paylaşılması; biçim StudyFormat'ta (ortak kod). */
object StudyLog {
    val COLUMNS get() = StudyFormat.COLUMNS
    fun label(key: String) = StudyFormat.label(key)
    fun num(v: Double?, digits: Int = 2): String = StudyFormat.num(v, digits)
    fun parse(line: String) = StudyFormat.parse(line)

    fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun dir(c: Context) = File(c.filesDir, "study").apply { mkdirs() }
    fun file(c: Context) = File(dir(c), "toraksB_calisma_verisi.csv")

    fun append(c: Context, values: Map<String, String>) {
        val f = file(c)
        val sb = StringBuilder()
        if (!f.exists() || f.length() == 0L) {
            sb.append(StudyFormat.BOM) // Excel'de Türkçe karakterler doğru görünsün
            sb.append(StudyFormat.headerLine()).append('\n')
        }
        sb.append(StudyFormat.rowLine(values)).append('\n')
        f.appendText(sb.toString())
    }

    /** Kayıtlar (eski → yeni). */
    fun records(c: Context): List<Map<String, String>> = runCatching {
        val f = file(c)
        if (!f.exists()) emptyList() else StudyFormat.records(f.readText())
    }.getOrDefault(emptyList())

    fun clear(c: Context) {
        runCatching { file(c).delete() }
    }

    fun shareIntent(c: Context): Intent? {
        val f = file(c)
        if (!f.exists()) return null
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "toraksB çalışma verisi")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Çalışma verisini paylaş")
    }
}
