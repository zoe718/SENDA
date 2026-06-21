package com.voxi.captions.model

/**
 * Una intervención (frase) ya fijada por Vosk como resultado final.
 *
 * En la Capa 1 solo contiene el texto. Las Capas 2 (tono) y 3 (espacio)
 * añadirán prosodia y hablante más adelante.
 */
data class Utterance(
    val id: Long,
    val text: String,
)
