package com.voxi.captions.viewmodel

import com.voxi.captions.model.Speaker
import kotlin.math.abs

/**
 * Diarización para el Modo A (spec §6): decide Hablante 1 / 2 a partir del
 * pitch promedio de cada frase usando un clustering online de 2 centroides
 * (una especie de k-means incremental), con histéresis para no parpadear.
 *
 * - Hablante 1 = voz más grave (centroide bajo).
 * - Hablante 2 = voz más aguda (centroide alto).
 *
 * Soporta bloqueo manual: si el usuario toca un carril, todo va a ese hablante
 * hasta volver a "Auto". Cero dependencia de cámara.
 */
class SpeakerTracker {

    companion object {
        // Velocidad con la que cada centroide sigue a su hablante.
        private const val CENTROID_EMA = 0.25f
        // Separación mínima de pitch para aceptar que hay un segundo hablante.
        private const val MIN_SEPARATION = 0.10f
        // Margen de distancia para mantener al último hablante en zona ambigua.
        private const val HYSTERESIS = 0.04f
    }

    private var low = Float.NaN   // centroide grave  → Hablante 1
    private var high = Float.NaN  // centroide agudo  → Hablante 2
    private var lastSpeaker = Speaker.ONE
    private var manual: Speaker? = null

    val manualSpeaker: Speaker? get() = manual
    val currentSpeaker: Speaker get() = manual ?: lastSpeaker

    fun setManual(speaker: Speaker?) {
        manual = speaker
        if (speaker != null) lastSpeaker = speaker
    }

    /** Clasifica una frase ya fijada a partir de su pitch promedio (0..1). */
    fun classify(pitch: Float): Speaker {
        manual?.let { return it }

        // Arranque en frío: el primer hablante define el centroide grave.
        if (low.isNaN()) {
            low = pitch
            lastSpeaker = Speaker.ONE
            return Speaker.ONE
        }

        // Aún no hay segundo hablante: créalo solo si el pitch difiere claramente.
        if (high.isNaN()) {
            if (abs(pitch - low) >= MIN_SEPARATION) {
                if (pitch > low) {
                    high = pitch
                } else {
                    high = low
                    low = pitch
                }
                lastSpeaker = if (abs(pitch - low) <= abs(pitch - high)) Speaker.ONE else Speaker.TWO
            } else {
                low += CENTROID_EMA * (pitch - low)
                lastSpeaker = Speaker.ONE
            }
            return lastSpeaker
        }

        // Dos centroides: asigna al más cercano con histéresis.
        val dLow = abs(pitch - low)
        val dHigh = abs(pitch - high)
        val speaker = when {
            dLow + HYSTERESIS < dHigh -> Speaker.ONE
            dHigh + HYSTERESIS < dLow -> Speaker.TWO
            else -> lastSpeaker // zona ambigua: no cambies de hablante
        }
        if (speaker == Speaker.ONE) low += CENTROID_EMA * (pitch - low)
        else high += CENTROID_EMA * (pitch - high)
        lastSpeaker = speaker
        return speaker
    }

    fun reset() {
        low = Float.NaN
        high = Float.NaN
        lastSpeaker = manual ?: Speaker.ONE
    }
}
