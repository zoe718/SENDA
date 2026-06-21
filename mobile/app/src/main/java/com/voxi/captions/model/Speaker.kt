package com.voxi.captions.model

/**
 * Hablante en el chat espacial (spec §6, Modo A).
 *
 * Para una demo confiable se trabaja con dos carriles (izquierda / derecha).
 * La asignación es automática (diarización por pitch) o manual (tocar un carril).
 */
enum class Speaker(val displayName: String) {
    ONE("Hablante 1"),
    TWO("Hablante 2"),
}
