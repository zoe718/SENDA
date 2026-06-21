package com.voxi.captions.vision

import android.graphics.Bitmap

/**
 * Memoria de rostros en runtime (spec §6). Mantiene a las personas enroladas en
 * el escaneo inicial (id, nombre, firma geométrica y foto) y re-identifica a una
 * cara detectada comparando su firma con las guardadas.
 *
 * La firma es el vector de 9 proporciones faciales CRUDAS que produce
 * [FaceAnalyzer.signatureOf] (distancias por pares entre 5 landmarks estables,
 * normalizadas por la distancia interocular). Como son medidas absolutas y
 * positivas, la comparacion correcta es por DISTANCIA EUCLIDEA (no coseno): dos
 * caras son la misma persona si sus proporciones estan suficientemente cerca.
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

    /** Persona cuya firma más se parece a [signature], si entra dentro del umbral. */
    fun match(signature: FloatArray?): Entry? {
        if (signature == null || entries.isEmpty()) return null
        var best: Entry? = null
        var bestDist = MATCH_DIST
        for (e in entries) {
            val d = distance(signature, e.signature)
            if (d < bestDist) {
                bestDist = d
                best = e
            }
        }
        return best
    }

    /** Distancia euclidea entre dos firmas (sobre la longitud comun). */
    private fun distance(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until n) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return kotlin.math.sqrt(sum)
    }

    companion object {
        // Distancia euclidea maxima entre firmas para considerar que es la misma
        // persona. Las 9 proporciones de una misma cara varian poco entre cuadros
        // (~0.15) y entre personas distintas mas (~0.4+); este umbral corta en
        // medio. Ajustable si reconoce de mas o de menos.
        const val MATCH_DIST = 0.50f
    }
}
