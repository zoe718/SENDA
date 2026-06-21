package com.voxi.captions.viewmodel

import com.voxi.captions.model.Speaker

/**
 * Diarización simple para el Modo A (spec §6): alterna Hablante 1/2 según los
 * cambios de pitch, con una zona muerta (histéresis) para no parpadear.
 *
 * Soporta bloqueo manual: si el usuario toca un carril, todas las frases se
 * asignan a ese hablante hasta que vuelva a "Auto". Cero dependencia de cámara.
 */
class SpeakerTracker {

    companion object {
        private const val MEAN_EMA = 0.2f
        private const val MARGIN = 0.08f // zona muerta alrededor de la media de pitch
    }

    private var meanPitch = 0.5f
    private var seen = false
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
        if (!seen) {
            meanPitch = pitch
            seen = true
            return lastSpeaker
        }
        val speaker = when {
            pitch > meanPitch + MARGIN -> Speaker.TWO  // voz más aguda
            pitch < meanPitch - MARGIN -> Speaker.ONE  // voz más grave
            else -> lastSpeaker
        }
        meanPitch += MEAN_EMA * (pitch - meanPitch)
        lastSpeaker = speaker
        return speaker
    }

    fun reset() {
        meanPitch = 0.5f
        seen = false
        lastSpeaker = manual ?: Speaker.ONE
    }
}
