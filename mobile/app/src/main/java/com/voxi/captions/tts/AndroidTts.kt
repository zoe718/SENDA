package com.voxi.captions.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Voz de regreso (spec §7): la persona sorda escribe y el teléfono lo dice en
 * voz alta con [TextToSpeech] nativo en español. Es offline y confiable.
 *
 * Robustez: si el español no está disponible cae al idioma por defecto, y si el
 * motor falla nunca lanza excepción hacia la UI.
 */
class AndroidTts(context: Context) {

    @Volatile
    private var ready = false

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) configureLanguage()
    }

    private fun configureLanguage() {
        val result = runCatching { tts.setLanguage(Locale.forLanguageTag("es-ES")) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            runCatching { tts.language = Locale.getDefault() }
        }
        ready = true
    }

    /** Dice el texto en voz alta. Encola para no cortar lo que ya esté sonando. */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || !ready) return
        runCatching {
            tts.speak(clean, TextToSpeech.QUEUE_ADD, null, clean.hashCode().toString())
        }
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}
