package com.voxi.captions.vision

import android.graphics.Bitmap
import kotlin.math.sqrt

/**
 * Memoria de rostros en runtime (spec §6). Mantiene a las personas enroladas en
 * el escaneo inicial (id, nombre, firma geométrica y foto) y re-identifica a una
 * cara detectada comparando su firma con las guardadas (coseno).
 *
 * Sirve para dos cosas durante la conversación:
 *  - dar a cada [DetectedFace] el nombre/foto de la persona ya conocida;
 *  - reforzar la fusión cara↔voz para no duplicar ni confundir hablantes.
 */
class FaceMemory {

    data class Entry(
        val id: Int,
        val name: String?,
        val signature: FloatArray,
        val photo: Bitmap?,
    )

    private val entries = ArrayList<Entry>()

    fun setAll(list: List<Entry>) {
        entries.clear()
        entries.addAll(list)
    }

    fun isEmpty(): Boolean = entries.isEmpty()

    fun all(): List<Entry> = entries.toList()

    fun byId(id: Int): Entry? = entries.firstOrNull { it.id == id }

    /** Persona cuya firma más se parece a [signature], si supera el umbral. */
    fun match(signature: FloatArray?): Entry? {
        if (signature == null || entries.isEmpty()) return null
        var best: Entry? = null
        var bestSim = MATCH_SIG
        for (e in entries) {
            val s = cosine(signature, e.signature)
            if (s >= bestSim) {
                bestSim = s
                best = e
            }
        }
        return best
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val den = sqrt(na) * sqrt(nb)
        return if (den < 1e-6f) 0f else dot / den
    }

    companion object {
        // Coseno mínimo entre firmas para considerar que es la misma persona.
        private const val MATCH_SIG = 0.985f
    }
}
