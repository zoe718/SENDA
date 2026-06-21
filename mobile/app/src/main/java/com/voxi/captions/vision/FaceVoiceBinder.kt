package com.voxi.captions.vision

import com.voxi.captions.model.Speaker

/**
 * Puente cara↔voz (spec §6, fusión). Aprende qué rostro ([DetectedFace.trackId])
 * corresponde a qué [Speaker] (voz) a partir de las intervenciones: cada vez que
 * alguien habla y hay una cara activa clara, se vota por esa asociación.
 *
 * Sirve para dos cosas:
 *  - pintar el contorno de cada cara con el color de su hablante;
 *  - dar una pista fuerte a la diarización: si la cara que está hablando ya se
 *    asoció antes con cierta voz, esa intervención debe ir a esa misma persona
 *    aunque el audio sea ambiguo (así no se confunde ni duplica hablantes).
 *
 * Es por sesión: una conversación nueva limpia las asociaciones (las caras no se
 * recuerdan entre sesiones todavía).
 */
class FaceVoiceBinder {

    companion object {
        // Votos mínimos para confiar en la asociación cara→voz.
        private const val MIN_VOTES = 2
        // Tope de votos por par para que una pista pueda migrar si cambia la cara.
        private const val MAX_VOTES = 12
    }

    // trackId -> (índice de hablante -> votos)
    private val votes = HashMap<Int, HashMap<Int, Int>>()

    /** Refuerza la asociación entre una cara y un hablante. */
    fun reinforce(trackId: Int, speaker: Speaker) {
        if (trackId < 0) return
        val bySpeaker = votes.getOrPut(trackId) { HashMap() }
        val v = (bySpeaker[speaker.index] ?: 0) + 1
        bySpeaker[speaker.index] = v.coerceAtMost(MAX_VOTES)
    }

    /** Hablante dominante asociado a una cara, si la confianza es suficiente. */
    fun speakerFor(trackId: Int): Speaker? {
        if (trackId < 0) return null
        val bySpeaker = votes[trackId] ?: return null
        val top = bySpeaker.maxByOrNull { it.value } ?: return null
        return if (top.value >= MIN_VOTES) Speaker(top.key) else null
    }

    fun reset() {
        votes.clear()
    }
}
