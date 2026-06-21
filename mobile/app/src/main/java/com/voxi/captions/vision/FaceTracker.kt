package com.voxi.captions.vision

import kotlin.math.hypot

/**
 * Seguimiento de rostros entre cuadros (spec §6, reconocimiento facial). ML Kit
 * Face Mesh no entrega ids de tracking, así que aquí asignamos a cada cara un
 * [DetectedFace.trackId] estable durante la sesión.
 *
 * Estrategia (robusta y ligera, sin modelos extra):
 *  - emparejamiento voraz por cercanía de centro/tamaño dentro de una "puerta"
 *    proporcional al tamaño del rostro (las caras no se teletransportan);
 *  - como respaldo, si una cara nueva no casa por posición pero su firma
 *    geométrica coincide con una pista perdida hace poco, se re-identifica con el
 *    mismo id (sobrevive a oclusiones breves);
 *  - las pistas no vistas durante varios cuadros se descartan.
 *
 * Así cada persona conserva su id (y por tanto su color de contorno) aunque se
 * mueva, y el id sirve de puente para asociar su cara con su voz.
 */
class FaceTracker {

    companion object {
        // Cuadros que una pista sobrevive sin ser vista antes de descartarse.
        private const val MAX_MISSED = 30
        // Suavizado EMA de posición/tamaño de la pista (0..1).
        private const val SMOOTH = 0.4f
        // Coseno mínimo entre firmas para re-identificar una cara reaparecida.
        private const val REACQUIRE_SIG = 0.99f
    }

    private class Track(
        var id: Int,
        var cx: Float,
        var cy: Float,
        var w: Float,
        var h: Float,
        var signature: FloatArray?,
    ) {
        var missed = 0
    }

    private val tracks = mutableListOf<Track>()
    private var nextId = 0

    /** Asigna ids estables. Devuelve copias de [faces] con su [DetectedFace.trackId]. */
    fun track(faces: List<DetectedFace>): List<DetectedFace> {
        // Envejece todas las pistas; las emparejadas se refrescan abajo.
        tracks.forEach { it.missed++ }

        val available = tracks.toMutableList()
        val result = ArrayList<DetectedFace>(faces.size)

        // Ordena las caras más grandes primero (suelen ser las más fiables).
        for (face in faces.sortedByDescending { it.widthRatio * it.heightRatio }) {
            val match = bestMatch(face, available)
            val track = if (match != null) {
                available.remove(match)
                match.missed = 0
                match.cx += SMOOTH * (face.cx - match.cx)
                match.cy += SMOOTH * (face.cy - match.cy)
                match.w += SMOOTH * (face.widthRatio - match.w)
                match.h += SMOOTH * (face.heightRatio - match.h)
                match.signature = blend(match.signature, face.signature)
                match
            } else {
                val t = Track(nextId++, face.cx, face.cy, face.widthRatio, face.heightRatio, face.signature)
                tracks.add(t)
                t
            }
            result.add(face.copy(trackId = track.id))
        }

        // Descarta pistas perdidas hace demasiado.
        tracks.removeAll { it.missed > MAX_MISSED }
        return result
    }

    fun reset() {
        tracks.clear()
        nextId = 0
    }

    /** Mejor pista para una cara: primero por posición, luego por firma. */
    private fun bestMatch(face: DetectedFace, candidates: List<Track>): Track? {
        if (candidates.isEmpty()) return null
        // Puerta proporcional al tamaño: el centro no puede saltar más de ~80%
        // del tamaño del rostro entre cuadros consecutivos.
        val gate = (maxOf(face.widthRatio, face.heightRatio) * 0.8f).coerceAtLeast(0.06f)
        var bestPos: Track? = null
        var bestDist = Float.MAX_VALUE
        for (t in candidates) {
            val d = hypot((face.cx - t.cx).toDouble(), (face.cy - t.cy).toDouble()).toFloat()
            if (d < gate && d < bestDist) {
                bestDist = d
                bestPos = t
            }
        }
        if (bestPos != null) return bestPos

        // Re-identificación por firma (reaparición tras oclusión/movimiento).
        val sig = face.signature ?: return null
        var bestSig: Track? = null
        var bestSim = REACQUIRE_SIG
        for (t in candidates) {
            val ts = t.signature ?: continue
            val s = cosine(sig, ts)
            if (s >= bestSim) {
                bestSim = s
                bestSig = t
            }
        }
        return bestSig
    }

    private fun blend(base: FloatArray?, next: FloatArray?): FloatArray? {
        if (next == null) return base
        if (base == null) return next.copyOf()
        val n = minOf(base.size, next.size)
        val out = base.copyOf()
        for (i in 0 until n) out[i] += SMOOTH * (next[i] - out[i])
        return out
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val den = kotlin.math.sqrt(na) * kotlin.math.sqrt(nb)
        return if (den < 1e-6f) 0f else dot / den
    }
}
