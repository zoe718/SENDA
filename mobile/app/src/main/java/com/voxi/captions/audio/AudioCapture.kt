package com.voxi.captions.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlin.concurrent.thread

/**
 * Captura de audio cruda con [AudioRecord]: 16 kHz, mono, PCM 16-bit.
 *
 * Un solo micrófono. En la Capa 1 el único consumidor es Vosk; en la Capa 2
 * el mismo stream alimentará también al ProsodyAnalyzer (spec §2).
 */
class AudioCapture {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        // Ventana de lectura ~ varios buffers de 20-30 ms.
        private const val READ_SAMPLES = 1_600 // 100 ms a 16 kHz
    }

    /**
     * Emite buffers de audio (ShortArray PCM16) de forma continua en [Dispatchers.IO].
     * Requiere que el permiso RECORD_AUDIO ya esté concedido.
     */
    @SuppressLint("MissingPermission")
    fun frames(): Flow<ShortArray> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = maxOf(minBuffer, READ_SAMPLES * 2)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            close(IllegalStateException("No se pudo inicializar AudioRecord"))
            return@callbackFlow
        }

        recorder.startRecording()

        val worker = thread(name = "voxi-audio") {
            val buffer = ShortArray(READ_SAMPLES)
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    trySend(buffer.copyOf(read))
                }
            }
        }

        awaitClose {
            worker.interrupt()
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
