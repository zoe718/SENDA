package com.voxi.captions.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.voxi.captions.model.Tone
import com.voxi.captions.model.Utterance
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exporta la conversación a un archivo .txt en la carpeta Descargas.
 *
 * En Android 10+ usa MediaStore (sin permisos). En versiones anteriores cae a la
 * carpeta de Descargas propia de la app (tampoco requiere permisos). Nunca lanza:
 * devuelve un [Result] para que la UI muestre éxito o error (spec §10, robustez).
 */
object TranscriptExporter {

    fun export(context: Context, utterances: List<Utterance>): Result<String> = runCatching {
        require(utterances.isNotEmpty()) { "No hay conversacion para exportar todavia." }

        val now = Date()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(now)
        val fileName = "voxi_conversacion_$stamp.txt"
        val content = buildTranscript(utterances, now)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, fileName, content)
        } else {
            saveLegacy(context, fileName, content)
        }
    }

    private fun buildTranscript(utterances: List<Utterance>, date: Date): String {
        val header = SimpleDateFormat("dd 'de' MMMM yyyy, HH:mm", Locale.forLanguageTag("es"))
            .format(date)
        return buildString {
            appendLine("SENDA - Conversacion")
            appendLine(header)
            appendLine("=".repeat(32))
            appendLine()
            utterances.forEach { u ->
                val tag = toneTag(u.tone)
                appendLine("[${u.speaker.displayName}]$tag ${u.text}")
            }
            appendLine()
            appendLine("Generado con SENDA - subtitulos espaciales con tono de voz.")
        }
    }

    /** Marca breve de cómo se dijo, solo cuando es notable (spec §5). */
    private fun toneTag(tone: Tone): String = when {
        tone.emphasis -> " (enfasis)"
        tone.volume > 0.8f -> " (grito)"
        tone.volume < 0.25f -> " (susurro)"
        else -> ""
    }

    private fun saveWithMediaStore(context: Context, fileName: String, content: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("No se pudo crear el archivo en Descargas.")
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            ?: error("No se pudo escribir el archivo.")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return "Descargas/$fileName"
    }

    private fun saveLegacy(context: Context, fileName: String, content: String): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)
        return file.absolutePath
    }
}
