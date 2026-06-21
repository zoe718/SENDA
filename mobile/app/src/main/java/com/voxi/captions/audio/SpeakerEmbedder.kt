package com.voxi.captions.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlin.math.sqrt

/**
 * Extractor de "huella neuronal" de voz (spec §6, mejora de diarizacion).
 *
 * Envuelve el modelo de speaker-embedding de sherpa-onnx (3D-Speaker ERes2Net):
 * convierte un fragmento de voz en un vector que identifica a la persona, mucho
 * mas robusto que las heuristicas de pitch/timbre. Los embeddings son casi
 * independientes del idioma, asi que un modelo entrenado en ingles separa bien
 * voces en espanol.
 *
 * Es OPCIONAL: si el modelo no carga (por memoria o ABI no incluida), Voxi sigue
 * funcionando con la diarizacion heuristica de [com.voxi.captions.viewmodel.SpeakerTracker].
 */
class SpeakerEmbedder(private val context: Context) {

    companion object {
        private const val TAG = "SpeakerEmbedder"
        // Debe estar directamente en assets/ (no en subcarpeta), lo exige sherpa.
        private const val MODEL = "3dspeaker_speech_eres2net_sv_en_voxceleb_16k.onnx"
        // Minimo de audio para un embedding fiable (~0.6 s de voz).
        private const val MIN_SAMPLES = (0.6f * AudioCapture.SAMPLE_RATE).toInt()
    }

    @Volatile
    private var extractor: SpeakerEmbeddingExtractor? = null

    @Volatile
    var dim: Int = 0
        private set

    val isReady: Boolean get() = extractor != null

    /**
     * Carga el modelo desde assets. Es pesado (~25 MB): llamar SIEMPRE fuera del
     * hilo principal. Devuelve true si quedo listo.
     */
    fun load(): Boolean {
        if (extractor != null) return true
        return runCatching {
            val ex = SpeakerEmbeddingExtractor(
                assetManager = context.assets,
                config = SpeakerEmbeddingExtractorConfig(
                    model = MODEL,
                    numThreads = 2,
                    provider = "cpu",
                ),
            )
            dim = ex.dim()
            extractor = ex
            Log.i(TAG, "Modelo de embeddings listo (dim=$dim)")
            true
        }.getOrElse {
            Log.w(TAG, "No se pudo cargar el modelo de embeddings: ${it.message}")
            false
        }
    }

    /**
     * Calcula el embedding L2-normalizado de un fragmento PCM16 mono a 16 kHz.
     * Devuelve null si el modelo no esta listo, el audio es muy corto o falla.
     */
    fun embed(pcm: ShortArray, length: Int): FloatArray? = embedRange(pcm, 0, length)

    /**
     * Huella de voz para ENROLAR a una persona (spec 6), como GALERIA: en vez de
     * un solo vector, parte el audio largo en varias ventanas solapadas y saca
     * el embedding de cada una. Devuelve:
     *  - [VoicePrint.windows]: cada ventana (captura la variabilidad natural de
     *    la voz: entonacion, energia, ruido), y
     *  - [VoicePrint.centroid]: su media normalizada (referencia "promedio").
     * Comparar despues por COSENO MAXIMO contra la galeria reconoce mucho mejor
     * que un unico centroide, porque una frase real se parece a ALGUNA de las
     * ventanas aunque no al promedio. Devuelve null si no hay modelo o audio.
     */
    class VoicePrint(val centroid: FloatArray, val windows: List<FloatArray>)

    fun enrollVoiceprint(
        pcm: ShortArray,
        length: Int,
        windowMs: Int = 2500,
        hopMs: Int = 1000,
        maxWindows: Int = 8,
    ): VoicePrint? {
        if (extractor == null) return null
        val total = length.coerceAtMost(pcm.size)
        if (total < MIN_SAMPLES) return null
        val window = (windowMs / 1000f * AudioCapture.SAMPLE_RATE).toInt().coerceAtLeast(MIN_SAMPLES)
        val hop = (hopMs / 1000f * AudioCapture.SAMPLE_RATE).toInt().coerceAtLeast(1)
        val windows = ArrayList<FloatArray>()
        var start = 0
        while (start + MIN_SAMPLES <= total && windows.size < maxWindows) {
            val len = (total - start).coerceAtMost(window)
            embedRange(pcm, start, len)?.let { windows.add(it) }
            start += hop
        }
        if (windows.isEmpty()) {
            val whole = embedRange(pcm, 0, total) ?: return null
            return VoicePrint(whole, listOf(whole))
        }
        // Centroide = media normalizada (cada ventana ya es unitaria).
        val sum = FloatArray(windows[0].size)
        for (w in windows) for (i in sum.indices) if (i < w.size) sum[i] += w[i]
        return VoicePrint(normalize(sum), windows)
    }

    /** Centroide robusto (media de ventanas). Atajo sobre [enrollVoiceprint]. */
    fun embedRobust(pcm: ShortArray, length: Int): FloatArray? =
        enrollVoiceprint(pcm, length)?.centroid

    /** Embedding L2-normalizado de un sub-rango [offset, offset+length) del PCM. */
    private fun embedRange(pcm: ShortArray, offset: Int, length: Int): FloatArray? {
        val ex = extractor ?: return null
        if (offset < 0 || length < MIN_SAMPLES) return null
        val n = length.coerceAtMost(pcm.size - offset)
        if (n < MIN_SAMPLES) return null
        val samples = FloatArray(n)
        for (i in 0 until n) samples[i] = pcm[offset + i] / 32768f
        return runCatching {
            val stream = ex.createStream()
            stream.acceptWaveform(samples, AudioCapture.SAMPLE_RATE)
            stream.inputFinished()
            if (!ex.isReady(stream)) {
                stream.release()
                return null
            }
            val emb = ex.compute(stream)
            stream.release()
            normalize(emb)
        }.getOrNull()
    }

    private fun normalize(v: FloatArray): FloatArray {
        var s = 0f
        for (x in v) s += x * x
        val norm = sqrt(s)
        if (norm < 1e-6f) return v
        val out = FloatArray(v.size)
        for (i in v.indices) out[i] = v[i] / norm
        return out
    }

    fun shutdown() {
        runCatching { extractor?.release() }
        extractor = null
    }
}
