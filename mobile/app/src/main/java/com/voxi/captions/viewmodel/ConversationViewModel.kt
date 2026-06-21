package com.voxi.captions.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voxi.captions.audio.AudioCapture
import com.voxi.captions.audio.ProsodyAnalyzer
import com.voxi.captions.audio.VoskEngine
import com.voxi.captions.model.Speaker
import com.voxi.captions.model.Tone
import com.voxi.captions.model.Utterance
import com.voxi.captions.tts.AndroidTts
import com.voxi.captions.vision.ActiveSpeaker
import com.voxi.captions.vision.DetectedFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado que la UI observa para pintar la conversación (spec §2). */
data class ConversationUiState(
    val isModelLoading: Boolean = true,
    val isListening: Boolean = false,
    val partialText: String = "",
    val partialTone: Tone = Tone.Neutral,
    val partialSpeaker: Speaker = Speaker.ONE,
    val manualSpeaker: Speaker? = null,
    val utterances: List<Utterance> = emptyList(),
    val statusMessage: String? = null,
    // Capa 3 Modo B (cámara + caras)
    val showCamera: Boolean = false,
    val faces: List<DetectedFace> = emptyList(),
    val activeFace: DetectedFace? = null,
)

class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val vosk = VoskEngine()
    private val audio = AudioCapture()
    private val prosody = ProsodyAnalyzer()
    private val speakers = SpeakerTracker()
    private val activeSpeaker = ActiveSpeaker()
    private val tts by lazy { AndroidTts(getApplication()) }

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private var nextId = 0L
    private var captureJob: Job? = null

    // Watchdog (spec §4): reinicia Vosk si hay audio pero no hay parciales.
    private var lastProgressMs = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            runCatching { vosk.load(getApplication()) }
                .onSuccess { _uiState.update { it.copy(isModelLoading = false) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isModelLoading = false,
                            statusMessage = "No se pudo cargar el modelo de voz: ${e.message}",
                        )
                    }
                }
        }
    }

    /** Llamar cuando el permiso de micrófono ya está concedido. */
    fun startListening() {
        if (captureJob != null) return
        lastProgressMs = System.currentTimeMillis()
        _uiState.update { it.copy(isListening = true, statusMessage = null) }

        captureJob = viewModelScope.launch(Dispatchers.IO) {
            launch { runWatchdog() }
            runCatching {
                audio.frames().collect { buffer ->
                    if (!vosk.isReady) return@collect
                    // Un mismo buffer alimenta a Vosk y al análisis de tono (spec §2).
                    prosody.feed(buffer, buffer.size)
                    when (val r = vosk.accept(buffer, buffer.size)) {
                        is VoskEngine.Recognition.Partial -> {
                            lastProgressMs = System.currentTimeMillis()
                            _uiState.update {
                                it.copy(
                                    partialText = r.text,
                                    partialTone = prosody.current(),
                                    partialSpeaker = speakers.currentSpeaker,
                                )
                            }
                        }
                        is VoskEngine.Recognition.Final -> {
                            lastProgressMs = System.currentTimeMillis()
                            addUtterance(r.text, prosody.finishUtterance())
                        }
                        VoskEngine.Recognition.None -> Unit
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isListening = false, statusMessage = "Micrófono detenido: ${e.message}")
                }
            }
        }
    }

    fun stopListening() {
        captureJob?.cancel()
        captureJob = null
        val pending = vosk.flush()
        if (pending is VoskEngine.Recognition.Final) addUtterance(pending.text, prosody.finishUtterance())
        _uiState.update { it.copy(isListening = false, partialText = "") }
    }

    /** Vía de regreso (spec §7): la persona sorda escribe y el teléfono lo dice. */
    fun speak(text: String) {
        tts.speak(text)
    }

    /** Capa 3 Modo B: alterna entre el chat espacial y la vista de cámara. */
    fun setShowCamera(show: Boolean) {
        if (!show) activeSpeaker.reset()
        _uiState.update { it.copy(showCamera = show, faces = emptyList(), activeFace = null) }
    }

    fun toggleCamera() = setShowCamera(!_uiState.value.showCamera)

    /**
     * Llega desde ML Kit (executor de CameraX). Decide el hablante activo
     * fusionando movimiento de labios con energía de audio (spec §6).
     */
    fun onFacesDetected(faces: List<DetectedFace>) {
        val s = _uiState.value
        val audioActive = s.isListening &&
            (s.partialText.isNotBlank() || s.partialTone.volume > 0.35f)
        val active = activeSpeaker.update(faces, audioActive)
        _uiState.update { it.copy(faces = faces, activeFace = active) }
    }

    /** Modo A (spec §6): el usuario fija un carril, o null para diarización automática. */
    fun setSpeaker(speaker: Speaker?) {
        speakers.setManual(speaker)
        _uiState.update {
            it.copy(manualSpeaker = speaker, partialSpeaker = speakers.currentSpeaker)
        }
    }

    private fun addUtterance(text: String, tone: Tone) {
        val speaker = speakers.classify(tone.pitch)
        _uiState.update { state ->
            state.copy(
                utterances = state.utterances +
                    Utterance(id = nextId++, text = text, tone = tone, speaker = speaker),
                partialText = "",
                partialSpeaker = speakers.currentSpeaker,
            )
        }
    }

    private suspend fun runWatchdog() {
        while (true) {
            delay(2_000)
            val idleMs = System.currentTimeMillis() - lastProgressMs
            if (_uiState.value.isListening && idleMs > 8_000) {
                withContext(Dispatchers.IO) {
                    vosk.reset()
                    prosody.reset()
                    speakers.reset()
                }
                lastProgressMs = System.currentTimeMillis()
                _uiState.update { it.copy(statusMessage = "Reanudando…") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
        vosk.close()
        tts.shutdown()
    }
}
