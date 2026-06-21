package com.voxi.captions.vision

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Identidad ESTABLE de cada rostro (spec §6, reconocimiento facial + fusión cara↔voz).
 *
 * El [DetectedFace.trackId] nativo de ML Kit es inestable: se reinicia cuando una
 * cara se ocluye, sale del cuadro o gira, lo que rompia toda la cadena cara↔voz
 * (la diarización duplicaba hablantes, el aura del hablante parpadeaba y salia el
 * falso "fuera de cuadro"). Este identificador da a cada persona una identidad
 * que SOBREVIVE a esos reinicios combinando tres pistas, en orden de confianza:
 *
 *  1. **Persona enrolada** ([FaceMemory]): si la firma del rostro coincide con
 *     alguien guardado en disco, su id es PERSISTENTE (= su id de [FaceMemory]).
 *     Asi un conocido conserva la misma llave cara↔voz aunque entre y salga, e
 *     incluso entre sesiones. Es lo que permite "saber quien es" de inmediato.
 *  2. **Tracking nativo** estable de ML Kit, mientras dure.
 *  3. **Posición/tamaño** (las caras no se teletransportan) y, como respaldo,
 *     **re-identificación por firma** (distancia euclidea) para reenganchar a una
 *     cara que reaparece tras una oclusión breve.
 *
 * La salida son los mismos [DetectedFace] pero con [DetectedFace.trackId]
 * reemplazado por el id estable y con [DetectedFace.personId]/[DetectedFace.personName]
 * rellenos si la persona esta enrolada. Ese id estable es la unica llave que usa
 * el resto del pipeline ([ActiveSpeaker], [FaceVoiceBinder]).
 */
class FaceIdentifier(private val memory: FaceMemory) {

    companion object {
        // Cuadros que una identidad sobrevive sin verse antes de descartarse
        // (~3 s a 15 fps): aguanta oclusiones y vueltas de cabeza.
        private const val MAX_MISSED = 45
        // Suavizado EMA de posición/tamaño/firma de la identidad (0..1).
        private const val SMOOTH = 0.4f
        // Distancia euclidea maxima entre firmas para reenganchar una cara que
        // reaparece (mas estricta que el match enrolado para no confundir vecinos).
        private const val REACQUIRE_DIST = 0.42f
        // Base de ids para rostros DESCONOCIDOS (no enrolados). Los enrolados usan
        // su id de disco (0..N, pequeño); este offset evita que choquen.
        private const val UNKNOWN_BASE = 1000
    }

    private class Track(
        var id: Int,
        var cx: Float,
        var cy: Float,
        var w: Float,
        var h: Float,
        var signature: FloatArray?,
        var personId: Int,
        var personName: String?,
        var nativeId: Int,
    ) {
        var missed = 0
    }

    private val tracks = mutableListOf<Track>()
    private var nextUnknown = 0

    /**
     * Asigna identidades estables. Devuelve copias de [faces] con su
     * [DetectedFace.trackId] = id estable y la identidad enrolada si la hay.
     */
    fun identify(faces: List<DetectedFace>): List<DetectedFace> {
        // Envejece todas las identidades; las que se vean abajo se refrescan.
        tracks.forEach { it.missed++ }

        val available = tracks.toMutableList()
        val result = ArrayList<DetectedFace>(faces.size)

        // Las caras mas grandes primero: suelen traer firma/landmarks mas fiables.
        for (face in faces.sortedByDescending { it.widthRatio * it.heightRatio }) {
            // Reconocimiento enrolado primero: da identidad persistente.
            val person = memory.match(face.signature)
            val track = match(face, person, available)?.also {
                available.remove(it)
                refresh(it, face, person)
            } ?: create(face, person)
            track.nativeId = face.trackId
            result.add(
                face.copy(
                    trackId = track.id,
                    personId = track.personId,
                    personName = track.personName,
                ),
            )
        }

        tracks.removeAll { it.missed > MAX_MISSED }
        return result
    }

    fun reset() {
        tracks.clear()
        nextUnknown = 0
    }

    /** Mejor identidad existente para una cara, por confianza decreciente. */
    private fun match(
        face: DetectedFace,
        person: FaceMemory.Entry?,
        candidates: List<Track>,
    ): Track? {
        if (candidates.isEmpty()) return null

        // 1) Misma persona enrolada: reusa su identidad pase lo que pase.
        if (person != null) {
            candidates.firstOrNull { it.personId == person.id }?.let { return it }
        }

        // 2) Tracking nativo estable (solo identidades aun desconocidas, para no
        //    pisar una identidad enrolada cuyo nativo se reciclo en otra cara).
        if (face.trackId >= 0) {
            candidates.firstOrNull { it.nativeId == face.trackId && it.personId < 0 }
                ?.let { return it }
        }

        // 3) Cercania de centro dentro de una puerta proporcional al tamaño. No
        //    enganches una identidad enrolada de OTRA persona por mera posición.
        val gate = (maxOf(face.widthRatio, face.heightRatio) * 0.8f).coerceAtLeast(0.06f)
        var bestPos: Track? = null
        var bestDist = Float.MAX_VALUE
        for (t in candidates) {
            if (t.personId >= 0 && person != null && t.personId != person.id) continue
            val d = hypot((face.cx - t.cx).toDouble(), (face.cy - t.cy).toDouble()).toFloat()
            if (d < gate && d < bestDist) {
                bestDist = d
                bestPos = t
            }
        }
        if (bestPos != null) return bestPos

        // 4) Re-identificación por firma (reaparición tras oclusión/movimiento).
        val sig = face.signature ?: return null
        var bestSig: Track? = null
        var bestSd = REACQUIRE_DIST
        for (t in candidates) {
            if (t.personId >= 0 && person != null && t.personId != person.id) continue
            val ts = t.signature ?: continue
            val d = sigDist(sig, ts)
            if (d < bestSd) {
                bestSd = d
                bestSig = t
            }
        }
        return bestSig
    }

    /** Refresca una identidad existente con la nueva observación de la cara. */
    private fun refresh(track: Track, face: DetectedFace, person: FaceMemory.Entry?) {
        track.missed = 0
        track.cx += SMOOTH * (face.cx - track.cx)
        track.cy += SMOOTH * (face.cy - track.cy)
        track.w += SMOOTH * (face.widthRatio - track.w)
        track.h += SMOOTH * (face.heightRatio - track.h)
        track.signature = blend(track.signature, face.signature)
        // Promoción a enrolado (nunca degradamos: el reconocimiento es ruidoso).
        if (person != null) {
            track.personName = person.name
            if (track.personId != person.id) {
                track.personId = person.id
                track.id = person.id
            }
        }
    }

    /** Crea una identidad nueva: persistente si esta enrolada, efimera si no. */
    private fun create(face: DetectedFace, person: FaceMemory.Entry?): Track {
        val track = if (person != null) {
            Track(
                id = person.id,
                cx = face.cx, cy = face.cy, w = face.widthRatio, h = face.heightRatio,
                signature = face.signature?.copyOf(),
                personId = person.id, personName = person.name,
                nativeId = face.trackId,
            )
        } else {
            Track(
                id = UNKNOWN_BASE + nextUnknown++,
                cx = face.cx, cy = face.cy, w = face.widthRatio, h = face.heightRatio,
                signature = face.signature?.copyOf(),
                personId = -1, personName = null,
                nativeId = face.trackId,
            )
        }
        tracks.add(track)
        return track
    }

    private fun blend(base: FloatArray?, next: FloatArray?): FloatArray? {
        if (next == null) return base
        if (base == null) return next.copyOf()
        val n = minOf(base.size, next.size)
        val out = base.copyOf()
        for (i in 0 until n) out[i] += SMOOTH * (next[i] - out[i])
        return out
    }

    private fun sigDist(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until n) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
    }
}
