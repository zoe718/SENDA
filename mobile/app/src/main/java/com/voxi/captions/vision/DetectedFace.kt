package com.voxi.captions.vision

/**
 * Una cara detectada por ML Kit, ya normalizada (0..1) respecto al tamaño de la
 * imagen en posición vertical. [mouthOpen] mide la apertura de la boca
 * (distancia entre labios interiores / alto de la cara) y sirve para saber
 * quién está hablando (spec §6, Modo B).
 */
data class DetectedFace(
    val cx: Float,        // centro X normalizado (0 izquierda, 1 derecha)
    val cy: Float,        // centro Y normalizado (0 arriba, 1 abajo)
    val widthRatio: Float, // ancho de la cara respecto al ancho de imagen
    val mouthOpen: Float,  // 0 (cerrada) .. 1 (muy abierta)
)
