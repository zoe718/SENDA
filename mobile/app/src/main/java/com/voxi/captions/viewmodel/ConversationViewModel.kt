package com.voxi.captions.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voxi.captions.audio.AudioCapture
import com.voxi.captions.audio.ProsodyAnalyzer
import com.voxi.captions.audio.SpeakerEmbedder
import com.voxi.captions.audio.VoskEngine
import com.voxi.captions.data.ConversationStore
import com.voxi.captions.data.SessionMeta
import com.voxi.captions.data.SettingsStore
import com.voxi.captions.data.SpeakerStore
import com.voxi.captions.export.TranscriptExporter
import com.voxi.captions.model.AnchoredCaption
import com.voxi.captions.model.Origin
import com.voxi.captions.model.Speaker
import com.voxi.captions.model.Tone
import com.voxi.captions.model.Utterance
import com.voxi.captions.model.VoiceType
import com.voxi.captions.ai.OfflineReplies
import com.voxi.captions.ai.SmartReplyService
import com.voxi.captions.tts.AndroidTts
import com.voxi.captions.tts.ElevenLabsTts
import com.voxi.captions.vision.ActiveSpeaker
import com.voxi.captions.vision.DetectedFace
import com.voxi.captions.vision.FaceTracker
import com.voxi.captions.vision.FaceVoiceBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val partialSpeaker: Speaker = Speaker.First,
    val manualSpeaker: Speaker? = null,
    // Hablantes ya descubiertos por la diarizacion (para el selector manual).
    val knownSpeakers: List<Speaker> = listOf(Speaker.First),
    val utterances: List<Utterance> = emptyList(),
    val statusMessage: String? = null,
    // Capa 3 Modo B (cámara + caras)
    val showCamera: Boolean = false,
    val faces: List<DetectedFace> = emptyList(),
    val activeFace: DetectedFace? = null,
    val anchoredCaption: AnchoredCaption? = null,
    // Modo C (spec §6): direccion estimada del sonido (-1 izq .. +1 der) o null.
    val soundDirection: Float? = null,
    // Voz del TTS elegida al inicio (spec §7).
    val voiceType: VoiceType = VoiceType.Default,
    val needsVoiceSelection: Boolean = false,
    // Respuestas sugeridas con IA (estilo LinkedIn): hasta 3 frases que la
    // persona sorda puede tocar para que el telefono las diga (spec §7).
    val suggestedReplies: List<String> = emptyList(),
    // Historial (spec §10)
    val showHistory: Boolean = false,
    val historySessions: List<SessionMeta> = emptyList(),
    val viewingUtterances: List<Utterance>? = null,
)

/** Resultado puntual de exportar, para mostrar un aviso de una sola vez (spec §10). */
data class ExportResult(val success: Boolean, val message: String)

class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val vosk = VoskEngine()
    private val audio = AudioCapture()
    private val prosody = ProsodyAnalyzer()
    // Huella neuronal de voz (sherpa-onnx) para una diarizacion mas robusta; es
    // opcional: si el modelo no carga, SpeakerTracker usa solo la heuristica.
    private val embedder = SpeakerEmbedder(app)
    private val speakers = SpeakerTracker(SpeakerStore(app))
    private val activeSpeaker = ActiveSpeaker()
    // Reconocimiento facial (spec 6): da ids estables a las caras y aprende que
    // rostro corresponde a que voz, para colorear contornos y guiar la diarizacion.
    private val faceTracker = FaceTracker()
    private val faceVoiceBinder = FaceVoiceBinder()
    private val tts by lazy { AndroidTts(getApplication()) }
    // Voz humana de ElevenLabs (spec §7): si hay clave e internet sintetiza una
    // voz expresiva; si no, cae al TTS nativo de Android.
    private val elevenLabs by lazy { ElevenLabsTts(getApplication()) }
    private val store = ConversationStore(app)
    private val settings = SettingsStore(app)

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ExportResult>(extraBufferCapacity = 1)
    val exportEvents: SharedFlow<ExportResult> = _exportEvents.asSharedFlow()

    private var nextId = 0L
    private var captureJob: Job? = null
    private var saveJob: Job? = null
    private var replyJob: Job? = null

    // Id de la sesión actual (timestamp de inicio) para el guardado automático.
    private var sessionId = System.currentTimeMillis()

    // Watchdog (spec §4): reinicia Vosk si hay audio pero no hay parciales.
    private var lastProgressMs = System.currentTimeMillis()

    // Modo C: paneo L/R suavizado y si el dispositivo entrega direccion (estereo).
    private var emaPan = 0f
    private var hasPan = false

    // Buffer PCM de la intervencion en curso (mono 16 kHz). Al cerrarla se le
    // calcula el embedding neuronal del hablante (diarizacion hibrida).
    private var uttBuf = ShortArray(AudioCapture.SAMPLE_RATE * 4)
    private var uttLen = 0

    init {
        // Voz del TTS: aplica la elegida (o marca que falta elegir, 1a vez).
        _uiState.update {
            it.copy(voiceType = settings.voiceType, needsVoiceSelection = !settings.voiceChosen)
        }
        tts.setVoiceType(settings.voiceType)
        elevenLabs.setVoiceType(settings.voiceType)
        // Carga el modelo de embeddings en segundo plano (pesado ~25 MB); si
        // falla, la app sigue con la diarizacion heuristica.
        viewModelScope.launch(Dispatchers.Default) { embedder.load() }
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
                var ttsMuted = false
                audio.frames().collect { frame ->
                    if (!vosk.isReady) return@collect
                    // Fix del eco del TTS (spec §7): mientras el telefono habla en
                    // voz alta, su propia voz entra por el microfono. Si la
                    // alimentamos a Vosk, se transcribe y la diarizacion la cuenta
                    // como un "hablante nuevo". Por eso, mientras el TTS suena (mas
                    // una cola corta), descartamos el audio propio.
                    if (tts.isSpeaking || elevenLabs.isSpeaking) {
                        if (!ttsMuted) {
                            ttsMuted = true
                            if (_uiState.value.partialText.isNotEmpty()) {
                                _uiState.update { it.copy(partialText = "") }
                            }
                        }
                        return@collect
                    }
                    if (ttsMuted) {
                        // Al terminar el TTS, descarta lo que Vosk haya bufferizado
                        // de la voz del telefono para no fijarlo como una frase.
                        ttsMuted = false
                        vosk.reset()
                        prosody.reset()
                        resetPcm()
                        return@collect
                    }
                    // Un mismo buffer alimenta a Vosk y al análisis de tono (spec §2).
                    prosody.feed(frame.samples, frame.length)
                    // Y se acumula para el embedding del hablante al cerrar la frase.
                    appendPcm(frame.samples, frame.length)
                    // Modo C (spec §6): el paneo L/R de cada buffer estima la
                    // direccion del sonido; se suaviza para ubicar al hablante
                    // fuera de cuadro. Solo se actualiza con voz clara (no con
                    // ruido de fondo) para que la direccion siga al que habla.
                    frame.pan?.let { p ->
                        if (prosody.current().volume > 0.3f) {
                            emaPan = if (hasPan) emaPan * 0.85f + p * 0.15f else p
                            hasPan = true
                        }
                    }
                    when (val r = vosk.accept(frame.samples, frame.length)) {
                        is VoskEngine.Recognition.Partial -> {
                            lastProgressMs = System.currentTimeMillis()
                            _uiState.update {
                                it.copy(
                                    partialText = r.text,
                                    partialTone = prosody.current(),
                                    partialSpeaker = speakers.currentSpeaker,
                                    soundDirection = if (hasPan) emaPan else null,
                                )
                            }
                        }
                        is VoskEngine.Recognition.Final -> {
                            lastProgressMs = System.currentTimeMillis()
                            val emb = if (embedder.isReady) embedder.embed(uttBuf, uttLen) else null
                            resetPcm()
                            addUtterance(r.text, prosody.finishUtterance(), emb)
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
        if (pending is VoskEngine.Recognition.Final) {
            val emb = if (embedder.isReady) embedder.embed(uttBuf, uttLen) else null
            resetPcm()
            addUtterance(pending.text, prosody.finishUtterance(), emb)
        }
        _uiState.update { it.copy(isListening = false, partialText = "") }
        saveNow()
    }

    /** Vía de regreso (spec §7): la persona sorda escribe y el teléfono lo dice. */
    fun speak(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        // Al hablar, el gate anti-eco de startListening descarta el audio propio
        // mientras el TTS suena, para que no se transcriba como otro hablante.
        // Primero intenta la voz humana de ElevenLabs; si no se puede, TTS nativo.
        elevenLabs.speak(clean) { fallback -> tts.speak(fallback) }
        _uiState.update { state ->
            state.copy(
                utterances = state.utterances +
                    Utterance(id = nextId++, text = clean, origin = Origin.SELF),
                // Ya respondio: limpia los chips hasta que vuelva a escuchar algo.
                suggestedReplies = emptyList(),
            )
        }
        scheduleSave()
    }

    /** Elige el tipo de voz del TTS (masculina/femenina/neutral) y lo recuerda. */
    fun setVoiceType(type: VoiceType) {
        settings.voiceType = type
        settings.voiceChosen = true
        tts.setVoiceType(type)
        elevenLabs.setVoiceType(type)
        _uiState.update { it.copy(voiceType = type, needsVoiceSelection = false) }
    }

    /** Exporta la conversación a un .txt en Descargas (spec §7 extra). */
    fun exportConversation(context: Context) {
        val utterances = _uiState.value.utterances
        viewModelScope.launch(Dispatchers.IO) {
            val result = TranscriptExporter.export(context.applicationContext, utterances)
            val event = result.fold(
                onSuccess = { path -> ExportResult(true, "Conversacion guardada en $path") },
                onFailure = { e -> ExportResult(false, e.message ?: "No se pudo exportar.") },
            )
            _exportEvents.emit(event)
        }
    }

    /** Empieza una conversación nueva (la anterior queda guardada en el historial). */
    fun startNewConversation() {
        saveNow()
        nextId = 0
        sessionId = System.currentTimeMillis()
        // Reset suave: conserva las voces aprendidas (memoria tipo Alexa).
        speakers.softReset()
        prosody.reset()
        resetPcm()
        // Las asociaciones cara-voz son por sesion: una conversacion nueva las olvida.
        faceTracker.reset()
        faceVoiceBinder.reset()
        replyJob?.cancel()
        _uiState.update {
            it.copy(
                utterances = emptyList(),
                partialText = "",
                anchoredCaption = null,
                knownSpeakers = speakers.knownSpeakers,
                suggestedReplies = emptyList(),
            )
        }
    }

    // --- Historial (spec §10) ---

    fun openHistory() {
        viewModelScope.launch {
            val sessions = withContext(Dispatchers.IO) { store.list() }
            _uiState.update {
                it.copy(showHistory = true, historySessions = sessions, viewingUtterances = null)
            }
        }
    }

    fun closeHistory() {
        _uiState.update { it.copy(showHistory = false, viewingUtterances = null) }
    }

    fun openSession(id: Long) {
        viewModelScope.launch {
            val utts = withContext(Dispatchers.IO) { store.load(id) }
            _uiState.update { it.copy(viewingUtterances = utts) }
        }
    }

    fun backToHistoryList() {
        _uiState.update { it.copy(viewingUtterances = null) }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(id) }
            val sessions = withContext(Dispatchers.IO) { store.list() }
            _uiState.update { it.copy(historySessions = sessions) }
        }
    }

    /** Capa 3 Modo B: alterna entre el chat espacial y la vista de cámara. */
    fun setShowCamera(show: Boolean) {
        if (!show) activeSpeaker.reset()
        _uiState.update {
            it.copy(
                showCamera = show,
                faces = emptyList(),
                activeFace = null,
                anchoredCaption = if (show) it.anchoredCaption else null,
            )
        }
    }

    fun toggleCamera() = setShowCamera(!_uiState.value.showCamera)

    /**
     * Llega desde ML Kit (executor de CameraX). Decide el hablante activo
     * fusionando movimiento de labios con energía de audio (spec §6).
     */
    fun onFacesDetected(faces: List<DetectedFace>) {
        val s = _uiState.value
        // Asigna ids estables y pinta cada cara con el color de su hablante (si ya
        // se asocio una voz con ese rostro).
        val tracked = faceTracker.track(faces).map { f ->
            f.copy(speaker = faceVoiceBinder.speakerFor(f.trackId))
        }
        val audioActive = s.isListening &&
            (s.partialText.isNotBlank() || s.partialTone.volume > 0.35f)
        val active = activeSpeaker.update(tracked, audioActive)
        _uiState.update { it.copy(faces = tracked, activeFace = active) }
    }

    /** Modo A (spec §6): el usuario fija un carril, o null para diarización automática. */
    fun setSpeaker(speaker: Speaker?) {
        speakers.setManual(speaker)
        _uiState.update {
            it.copy(
                manualSpeaker = speaker,
                partialSpeaker = speakers.currentSpeaker,
                knownSpeakers = speakers.knownSpeakers,
            )
        }
    }

    private fun addUtterance(text: String, tone: Tone, embedding: FloatArray? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        // Fusion cara-voz (spec 6): si la cara que esta hablando ya tiene una voz
        // asociada, se usa como pista fuerte para no confundir ni duplicar
        // hablantes; al terminar, se refuerza la asociacion de esa cara con la voz
        // que resulto clasificada.
        val activeFace = _uiState.value.activeFace
        val faceHint = activeFace?.let { faceVoiceBinder.speakerFor(it.trackId) }
        // La diarización usa la huella de voz de esta intervención: si hay
        // embedding neuronal (sherpa-onnx) manda; si no, cae a la heuristica.
        val speaker = speakers.classify(prosody.lastVoiceProfile(), embedding, faceHint)
        if (activeFace != null) faceVoiceBinder.reinforce(activeFace.trackId, speaker)
        val utterance =
            Utterance(id = nextId++, text = clean, tone = tone, speaker = speaker, origin = Origin.HEARD)
        _uiState.update { state ->
            // En cámara, "pega" lo dicho a la cara activa para que quede anclado.
            val anchored = if (state.showCamera) {
                state.activeFace?.let { f -> AnchoredCaption(clean, tone, speaker, f.cx, f.cy, f.widthRatio) }
                    ?: state.anchoredCaption
            } else {
                state.anchoredCaption
            }
            state.copy(
                utterances = state.utterances + utterance,
                partialText = "",
                partialSpeaker = speakers.currentSpeaker,
                knownSpeakers = speakers.knownSpeakers,
                anchoredCaption = anchored,
            )
        }
        scheduleSave()
        refreshSuggestions()
    }

    /**
     * Pide a la IA hasta 3 respuestas cortas con base en lo ultimo que se
     * escucho (spec §7). Si Gemini no esta disponible, usa plantillas offline
     * para que los chips nunca queden vacios.
     */
    private fun refreshSuggestions() {
        val heard = _uiState.value.utterances
            .filter { it.origin == Origin.HEARD }
            .takeLast(6)
            .map { it.text }
        if (heard.isEmpty()) {
            _uiState.update { it.copy(suggestedReplies = emptyList()) }
            return
        }
        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            val replies = SmartReplyService.suggest(heard)
            val final = replies.ifEmpty { OfflineReplies.defaults }
            _uiState.update { it.copy(suggestedReplies = final) }
        }
    }

    /** Acumula el PCM de la intervencion en curso (recortado a ~12 s). */
    private fun appendPcm(samples: ShortArray, length: Int) {
        if (length <= 0) return
        val max = AudioCapture.SAMPLE_RATE * 12
        if (uttLen >= max) return
        val need = uttLen + length
        if (need > uttBuf.size) {
            uttBuf = uttBuf.copyOf(maxOf(need, uttBuf.size * 2).coerceAtMost(max))
        }
        val n = length.coerceAtMost(uttBuf.size - uttLen)
        if (n <= 0) return
        System.arraycopy(samples, 0, uttBuf, uttLen, n)
        uttLen += n
    }

    private fun resetPcm() {
        uttLen = 0
    }

    /** Guardado automático con un pequeño debounce para no escribir en cada frase. */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            saveNow()
        }
    }

    private fun saveNow() {
        val current = _uiState.value.utterances
        if (current.isEmpty()) return
        val id = sessionId
        viewModelScope.launch(Dispatchers.IO) { store.save(id, current) }
    }

    private suspend fun runWatchdog() {
        while (true) {
            delay(2_000)
            val idleMs = System.currentTimeMillis() - lastProgressMs
            if (_uiState.value.isListening && idleMs > 8_000) {
                withContext(Dispatchers.IO) {
                    vosk.reset()
                    prosody.reset()
                    resetPcm()
                    speakers.softReset()
                }
                lastProgressMs = System.currentTimeMillis()
                _uiState.update { it.copy(statusMessage = "Reanudando…") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        saveNow()
        captureJob?.cancel()
        vosk.close()
        tts.shutdown()
        elevenLabs.shutdown()
        embedder.shutdown()
    }
}
