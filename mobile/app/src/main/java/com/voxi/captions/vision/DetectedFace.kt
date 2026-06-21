package com.voxi.captions.vision

import com.voxi.captions.model.Speaker

/**
 * Una cara detectada por ML Kit, ya normalizada (0..1) respecto al tamaño de la
 * imagen en posición vertical. [mouthOpen] mide la apertura de la boca
 * (distancia entre labios interiores / alto de la cara) y sirve para saber
 * quién está hablando (spec §6, Modo B).
 *
 * A partir del reconocimiento facial (spec §6, fusión cara↔voz), cada cara
 * arrastra además:
 *  - [signature]: firma geométrica del rostro (proporciones faciales invariantes
 *    a escala/posición) para re-identificar a la misma persona entre cuadros.
 *  - [trackId]: id estable asignado por [FaceTracker] durante la sesión
 *    (-1 mientras no se ha asignado).
 *  - [speaker]: hablante (voz) asociado a esta cara, para pintar su contorno con
 *    el color correcto y para ayudar a la diarización (o null si aún no se sabe).
 */
data class DetectedFace(
    val cx: Float,         // centro X normalizado (0 izquierda, 1 derecha)
    val cy: Float,         // centro Y normalizado (0 arriba, 1 abajo)
    val widthRatio: Float, // ancho de la cara respecto al ancho de imagen
    val heightRatio: Float, // alto de la cara respecto al alto de imagen
    val mouthOpen: Float,  // 0 (cerrada) .. 1 (muy abierta)
    val signature: FloatArray? = null,
    val trackId: Int = -1,
    val speaker: Speaker? = null,
) {
    // equals/hashCode ignoran la firma (FloatArray) para no comparar arreglos por
    // referencia; basta la geometría y los ids para detectar cambios de cuadro.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedFace) return false
        return cx == other.cx &&
            cy == other.cy &&
            widthRatio == other.widthRatio &&
            heightRatio == other.heightRatio &&
            mouthOpen == other.mouthOpen &&
            trackId == other.trackId &&
            speaker == other.speaker
    }

    override fun hashCode(): Int {
        var result = cx.hashCode()
        result = 31 * result + cy.hashCode()
        result = 31 * result + widthRatio.hashCode()
        result = 31 * result + heightRatio.hashCode()
        result = 31 * result + mouthOpen.hashCode()
        result = 31 * result + trackId
        result = 31 * result + (speaker?.hashCode() ?: 0)
        return result
    }
}
