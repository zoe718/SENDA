package com.voxi.captions.audio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Envuelve el reconocedor offline de Vosk (spec §4).
 *
 * - Copia el modelo desde `assets/[ASSET_MODEL_DIR]` a almacenamiento interno la 1ª vez.
 * - Recibe buffers PCM16 y devuelve resultados parciales (en vivo) o finales (fijados).
 */
class VoskEngine {

    companion object {
        // Carpeta del modelo dentro de assets/ (vosk-model-small-es-0.42 desempaquetado).
        const val ASSET_MODEL_DIR = "model-es"
    }

    sealed interface Recognition {
        /** Texto provisional mientras la persona habla (atenuado en la UI). */
        data class Partial(val text: String) : Recognition

        /** Texto fijado por Vosk al detectar fin de frase. */
        data class Final(val text: String) : Recognition

        /** Sin cambios relevantes. */
        data object None : Recognition
    }

    private var model: Model? = null
    private var recognizer: Recognizer? = null

    val isReady: Boolean get() = recognizer != null

    /** Carga (pesada) del modelo. Llamar en un dispatcher de IO. */
    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        val modelDir = copyModelIfNeeded(context)
        val loadedModel = Model(modelDir.absolutePath)
        model = loadedModel
        recognizer = Recognizer(loadedModel, AudioCapture.SAMPLE_RATE.toFloat())
    }

    /** Procesa un buffer de audio y devuelve el resultado parcial o final. */
    fun accept(buffer: ShortArray, length: Int): Recognition {
        val rec = recognizer ?: return Recognition.None
        return if (rec.acceptWaveForm(buffer, length)) {
            val text = JSONObject(rec.result).optString("text").trim()
            if (text.isEmpty()) Recognition.None else Recognition.Final(text)
        } else {
            val partial = JSONObject(rec.partialResult).optString("partial").trim()
            Recognition.Partial(partial)
        }
    }

    /** Vacía cualquier resto pendiente y devuelve el último texto final, si lo hay. */
    fun flush(): Recognition {
        val rec = recognizer ?: return Recognition.None
        val text = JSONObject(rec.finalResult).optString("text").trim()
        return if (text.isEmpty()) Recognition.None else Recognition.Final(text)
    }

    /** Reinicia el reconocedor (watchdog del spec §4, "reanudando…"). */
    fun reset() {
        val currentModel = model ?: return
        recognizer?.close()
        recognizer = Recognizer(currentModel, AudioCapture.SAMPLE_RATE.toFloat())
    }

    fun close() {
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
    }

    /**
     * Copia recursivamente el modelo de assets a almacenamiento interno la primera vez.
     * Usa un archivo marcador para no volver a copiar en arranques posteriores.
     */
    private fun copyModelIfNeeded(context: Context): File {
        val targetDir = File(context.filesDir, ASSET_MODEL_DIR)
        val marker = File(targetDir, ".copied")
        if (marker.exists()) return targetDir

        if (targetDir.exists()) targetDir.deleteRecursively()
        copyAssetDir(context, ASSET_MODEL_DIR, targetDir)
        marker.createNewFile()
        return targetDir
    }

    private fun copyAssetDir(context: Context, assetPath: String, target: File) {
        val assets = context.assets
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // Es un archivo: copiarlo.
            target.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            for (child in children) {
                copyAssetDir(context, "$assetPath/$child", File(target, child))
            }
        }
    }
}
