package com.voxi.captions.vision

import android.graphics.Bitmap
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
 *  - [trackId]: id ESTABLE de identidad asignado por [FaceIdentifier]: sobrevive
 *    a los reinicios del tracking nativo de ML Kit y, para personas enroladas,
 *    es persistente (= su id en disco). Es la llave cara↔voz en todo el pipeline.
 *  - [personId]/[personName]: identidad enrolada reconocida (spec §6), o -1/null.
 *  - [speaker]: hablante (voz) asociado a esta cara, para pintar su contorno con
 *    el color correcto y para ayudar a la diarización (o null si aún no se sabe).
 *  - [contour]: puntos del óvalo del rostro (FACE_OVAL de ML Kit) normalizados
 *    como [x0,y0,x1,y1,...], para dibujar la silueta/aura del que habla.
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
    val contour: FloatArray? = null,
    // Aspecto (ancho/alto) de la imagen vertical de la que salio esta cara, para
    // mapear las coordenadas normalizadas al recorte FILL_CENTER del preview.
    val imageAspect: Float = 0f,
    // Miniatura recortada del rostro (solo se llena en modo escaneo, spec §6),
    // para enrolar a la persona con su foto. Transitoria: no entra en equals.
    val thumbnail: Bitmap? = null,
    // Identidad enrolada reconocida por [FaceIdentifier] (spec §6): id persistente
    // de la persona guardada en disco y su nombre, o -1/null si es un rostro
    // desconocido. Permite saber QUIEN es esta cara aunque cambie el trackId
    // nativo de ML Kit y antes incluso de que la persona hable.
    val personId: Int = -1,
    val personName: String? = null,
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
            personId == other.personId &&
            speaker == other.speaker
    }

    override fun hashCode(): Int {
        var result = cx.hashCode()
        result = 31 * result + cy.hashCode()
        result = 31 * result + widthRatio.hashCode()
        result = 31 * result + heightRatio.hashCode()
        result = 31 * result + mouthOpen.hashCode()
        result = 31 * result + trackId
        result = 31 * result + personId
        result = 31 * result + (speaker?.hashCode() ?: 0)
        return result
    }
}
