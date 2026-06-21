package com.voxi.captions.vision

import kotlin.math.abs

/**
 * Fusión audio + labios (spec §6, Modo B): elige la cara que está hablando.
 *
 * Regla: si hay energía de audio y una cara mueve la boca, esa cara es el
 * hablante activo. Para que la burbuja no parpadee, se mantiene al último
 * hablante durante unos frames y se re-ancla a la cara más cercana.
 */
class ActiveSpeaker {

    companion object {
        private const val MIN_MOUTH_OPEN = 0.03f // por debajo se considera boca quieta
        private const val HOLD_FRAMES = 8        // frames que se mantiene al hablante
    }

    private var current: DetectedFace? = null
    private var hold = 0

    fun update(faces: List<DetectedFace>, audioActive: Boolean): DetectedFace? {
        if (faces.isEmpty()) {
            current = null
            hold = 0
            return null
        }
        val candidate = faces.maxByOrNull { it.mouthOpen }
        if (audioActive && candidate != null && candidate.mouthOpen >= MIN_MOUTH_OPEN) {
            current = candidate
            hold = HOLD_FRAMES
            return current
        }
        if (hold > 0) {
            hold--
            current = nearest(faces, current)
            return current
        }
        current = null
        return null
    }

    fun reset() {
        current = null
        hold = 0
    }

    private fun nearest(faces: List<DetectedFace>, ref: DetectedFace?): DetectedFace? {
        ref ?: return faces.firstOrNull()
        return faces.minByOrNull { abs(it.cx - ref.cx) }
    }
}
