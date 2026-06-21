package com.voxi.captions.vision

import kotlin.math.abs

/**
 * Fusion audio + labios (spec 6, Modo B): elige la cara que esta hablando.
 *
 * La senal real de "estar hablando" no es que la boca este abierta, sino que la
 * boca se MUEVE (se abre y cierra). Por eso, en vez de mirar [DetectedFace.mouthOpen]
 * instantaneo, mantenemos un historial corto por [DetectedFace.trackId] y medimos
 * cuanto vibra la apertura (movimiento labial). Asi:
 *  - una cara con la boca naturalmente entreabierta pero quieta NO gana,
 *  - re-anclamos por identidad (trackId), no por "la cara mas cercana", para que
 *    la burbuja/aura no salte entre personas,
 *  - aplicamos histeresis: solo cambiamos de hablante si el nuevo candidato supera
 *    al actual por un margen claro.
 */
class ActiveSpeaker {

    companion object {
        // Movimiento labial minimo (media de |delta| de apertura) para considerar
        // que la cara realmente esta hablando. Bajo a proposito: basta una vibracion
        // pequena de labios para enganchar al hablante; el HOLD largo lo sostiene.
        private const val MIN_MOTION = 0.008f
        // Apertura minima como respaldo cuando una cara aun no tiene trackId/historial.
        private const val MIN_MOUTH_OPEN = 0.04f
        // Peso de la apertura absoluta dentro del puntaje (secundario al movimiento).
        private const val OPEN_WEIGHT = 0.08f
        // Margen extra que un candidato distinto debe superar para destronar al actual.
        private const val SWITCH_MARGIN = 0.010f
        // Frames que se mantiene al hablante tras dejar de detectar movimiento.
        // Alto a proposito (~1.5 s a 15 fps) para que el aura NO parpadee durante
        // las pausas naturales del habla y se sienta estable sobre quien habla.
        private const val HOLD_FRAMES = 22
        // Tamano de la ventana de historial por cara.
        private const val WINDOW = 6
        // Frames sin ver un trackId tras los que se descarta su historial.
        private const val PRUNE_AFTER = 30
    }

    /** Historial corto de apertura de boca de una cara concreta. */
    private class Lip {
        val samples = ArrayDeque<Float>()
        var lastSeen = 0

        fun push(open: Float) {
            samples.addLast(open)
            while (samples.size > WINDOW) samples.removeFirst()
        }

        /** Movimiento = media de |delta| entre muestras consecutivas. */
        fun motion(): Float {
            if (samples.size < 2) return 0f
            var sum = 0f
            var prev = samples.first()
            var first = true
            for (s in samples) {
                if (first) { first = false; continue }
                sum += abs(s - prev)
                prev = s
            }
            return sum / (samples.size - 1)
        }
    }

    private val lips = HashMap<Int, Lip>()
    private var frame = 0
    private var current: DetectedFace? = null
    private var currentTrackId = -1
    private var hold = 0

    fun update(faces: List<DetectedFace>, audioActive: Boolean): DetectedFace? {
        frame++
        if (faces.isEmpty()) {
            current = null
            currentTrackId = -1
            hold = 0
            return null
        }

        // 1) Actualiza historial por trackId y poda los que ya no aparecen.
        for (f in faces) {
            if (f.trackId >= 0) {
                val lip = lips.getOrPut(f.trackId) { Lip() }
                lip.push(f.mouthOpen)
                lip.lastSeen = frame
            }
        }
        val it = lips.entries.iterator()
        while (it.hasNext()) {
            if (frame - it.next().value.lastSeen > PRUNE_AFTER) it.remove()
        }

        // 2) Sin audio: nadie habla, pero sostenemos brevemente para evitar parpadeo.
        if (!audioActive) {
            if (hold > 0) { hold--; current = reanchor(faces); return current }
            current = null
            currentTrackId = -1
            return null
        }

        // 3) Solo compiten las caras que REALMENTE mueven la boca (gate de movimiento);
        //    entre ellas se ordena por puntaje (movimiento + apertura como desempate).
        val speaking = faces.filter { gate(it) }
        val best = speaking.maxByOrNull { score(it) }
        if (best != null) {
            val bestScore = score(best)
            val curFace = speaking.firstOrNull { it.trackId >= 0 && it.trackId == currentTrackId }
            val curScore = curFace?.let { score(it) } ?: 0f
            val keepCurrent = curFace != null &&
                best.trackId != currentTrackId &&
                bestScore <= curScore + SWITCH_MARGIN
            val chosen = if (keepCurrent) curFace else best
            commit(chosen)
            return current
        }

        // 4) Nadie se mueve lo suficiente este frame: sostiene o suelta.
        if (hold > 0) { hold--; current = reanchor(faces); return current }
        current = null
        currentTrackId = -1
        return null
    }

    fun reset() {
        lips.clear()
        current = null
        currentTrackId = -1
        hold = 0
        frame = 0
    }

    private fun commit(face: DetectedFace) {
        current = face
        currentTrackId = face.trackId
        hold = HOLD_FRAMES
    }

    /** Movimiento labial de la cara: media de |delta| del historial (0 si no hay trackId). */
    private fun motionOf(face: DetectedFace): Float =
        if (face.trackId >= 0) lips[face.trackId]?.motion() ?: 0f else 0f

    /** True si la cara mueve la boca lo suficiente para considerarla hablante. */
    private fun gate(face: DetectedFace): Boolean =
        if (face.trackId >= 0) motionOf(face) >= MIN_MOTION
        else face.mouthOpen >= MIN_MOUTH_OPEN // respaldo mientras no hay historial

    /** Puntaje para ordenar entre caras que ya pasaron el gate (movimiento + apertura). */
    private fun score(face: DetectedFace): Float =
        motionOf(face) + OPEN_WEIGHT * face.mouthOpen

    /** Re-ancla al mismo hablante por identidad; si desaparecio, a la cara mas cercana. */
    private fun reanchor(faces: List<DetectedFace>): DetectedFace? {
        faces.firstOrNull { it.trackId >= 0 && it.trackId == currentTrackId }?.let { return it }
        val ref = current ?: return faces.firstOrNull()
        return faces.minByOrNull { abs(it.cx - ref.cx) }
    }
}
