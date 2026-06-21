package com.voxi.captions.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.voxi.captions.audio.AudioCapture
import com.voxi.captions.audio.ProsodyAnalyzer
import com.voxi.captions.audio.SpeakerEmbedder
import com.voxi.captions.audio.VoskEngine
import com.voxi.captions.data.ConversationStore
import com.voxi.captions.data.PersonStore
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
import com.voxi.captions.vision.FaceIdentifier
import com.voxi.captions.vision.FaceMemory
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
    // Aspecto (ancho/alto) de la imagen de la camara en vertical, para mapear
    // las coordenadas de las caras al recorte FILL_CENTER del preview (spec §6).
    val cameraAspect: Float = 0.75f,
    // Modo C (spec §6): direccion estimada del sonido (-1 izq .. +1 der) o null.
    val soundDirection: Float? = null,
    // Modo C (spec §6): true SOLO cuando, de forma sostenida, hay voz en curso y
    // ninguna cara visible esta hablando -> el hablante esta fuera de cuadro. Se
    // calcula con debounce en el ViewModel para que el aviso no parpadee.
    val speakerOffscreen: Boolean = false,
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
    // Capa 2 (spec §6): escaneo inicial de rostros + memoria cara↔voz.
    // needsScan = aun no se ha escaneado en esta sesion; isScanning = pantalla de
    // escaneo abierta; scanPeople = rostros capturados (para nombrarlos).
    val needsScan: Boolean = true,
    val isScanning: Boolean = false,
    val scanPeople: List<ScanPerson> = emptyList(),
    // Caras detectadas en vivo durante el escaneo (para dibujar sus marcos sobre
    // el preview y dejar tocar a cada persona para enrolarla). Transitorio.
    val scanLiveFaces: List<DetectedFace> = emptyList(),
    // Identidad (nombre + foto) por indice de hablante, para pintar el avatar y
    // el nombre real en el chat en vez de "Hablante N" y un circulo de color.
    val speakerIdentities: Map<Int, SpeakerIdentity> = emptyMap(),
    // Capa 4: el usuario silencio el microfono a proposito (pausa la escucha).
    val isMuted: Boolean = false,
)

/** Resultado puntual de exportar, para mostrar un aviso de una sola vez (spec §10). */
data class ExportResult(val success: Boolean, val message: String)

/** Rostro capturado en el escaneo inicial (spec §6), listo para enrolar. */
data class ScanPerson(
    val localId: Int,
    val name: String?,
    val photo: ImageBitmap,
    // Id de tracking nativo con el que se capturo (>=0 si la persona sigue en
    // cuadro; -1 para personas sembradas de sesiones anteriores). Sirve para
    // marcar su rostro como "ya agregado" sobre el preview en vivo.
    val trackId: Int = -1,
)

/** Identidad enrolada de un hablante: nombre y foto para mostrar en el chat. */
data class SpeakerIdentity(
    val name: String?,
    val photo: ImageBitmap?,
)

class ConversationViewModel(app: Application) : AndroidViewModel(app) {

    private val vosk = VoskEngine()
    private val audio = AudioCapture()
    private val prosody = ProsodyAnalyzer()
    // Huella neuronal de voz (sherpa-onnx) para una diarizacion mas robusta; es
    // opcional: si el modelo no carga, SpeakerTracker usa solo la heuristica.
    private val embedder = SpeakerEmbedder(app)
    private val speakers = SpeakerTracker(SpeakerStore(app))
    private val activeSpeaker = ActiveSpeaker()
    private val faceVoiceBinder = FaceVoiceBinder()
    // Capa 2 (spec §6): memoria de rostros persistente + matcher en runtime.
    private val personStore = PersonStore(app)
    private val faceMemory = FaceMemory()
    // Reconocimiento facial (spec §6): da a cada rostro una identidad ESTABLE que
    // sobrevive a los reinicios del trackId nativo de ML Kit y reconoce a las
    // personas enroladas. Su id estable es la llave cara-voz de todo el pipeline.
    private val faceIdentifier = FaceIdentifier(faceMemory)
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
    // Ultimo contexto por el que ya pedimos sugerencias (dedup de cuota Gemini).
    private var lastSuggestKey: String? = null
    // Frames seguidos con voz pero sin cara hablante (debounce de "fuera de cuadro").
    private var offscreenFrames = 0
    // Frames sostenidos necesarios para declarar al hablante fuera de cuadro (~0.5 s).
    private val OFFSCREEN_FRAMES = 5
    // Tope de personas enroladas en un escaneo.
    private val MAX_SCAN_PEOPLE = 8

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
        // Carga la memoria de rostros aprendida en escaneos anteriores (spec §6).
        loadFaceMemory()
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

    /**
     * Capa 4: silenciar/reactivar el microfono a voluntad. Al silenciar se pausa
     * la captura (la persona deja de ser transcrita); al reactivar, se reanuda.
     */
    fun toggleMute() {
        if (_uiState.value.isMuted) {
            _uiState.update { it.copy(isMuted = false) }
            startListening()
        } else {
            stopListening()
            _uiState.update { it.copy(isMuted = true, partialText = "") }
        }
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

    /**
     * Reabre el selector de voz a mitad de sesion (spec 7): el usuario puede
     * re-personalizar la voz del TTS cuando quiera. Reusa la misma pantalla que
     * la primera vez; al elegir, [setVoiceType] la cierra.
     */
    fun requestVoiceChange() {
        _uiState.update { it.copy(needsVoiceSelection = true) }
    }

    /** Cierra el selector de voz sin cambiar nada (mantiene la voz actual). */
    fun cancelVoiceChange() {
        // Marca que ya hubo una eleccion para no volver a mostrarlo solo.
        settings.voiceChosen = true
        _uiState.update { it.copy(needsVoiceSelection = false) }
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
        // Conversacion nueva = hablantes nuevos: olvida las voces de la sesion en
        // memoria, pero conserva la memoria de largo plazo en disco (spec 6).
        speakers.sessionReset()
        prosody.reset()
        resetPcm()
        // Las asociaciones cara-voz son por sesion: una conversacion nueva las olvida.
        faceIdentifier.reset()
        faceVoiceBinder.reset()
        replyJob?.cancel()
        // Las fotos cara↔voz tambien son por sesion: una conversacion nueva las olvida.
        speakerPhotos.clear()
        _uiState.update {
            it.copy(
                utterances = emptyList(),
                partialText = "",
                anchoredCaption = null,
                knownSpeakers = speakers.knownSpeakers,
                suggestedReplies = emptyList(),
                speakerIdentities = emptyMap(),
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
        // Damos a cada rostro una identidad ESTABLE y persistente (sobrevive a los
        // reinicios del trackId nativo de ML Kit y reconoce a los enrolados); ese
        // id estable es la llave cara-voz. Luego pintamos cada cara con el color de
        // su hablante si ya se asocio una voz con esa identidad.
        val tracked = faceIdentifier.identify(faces).map { f ->
            f.copy(speaker = faceVoiceBinder.speakerFor(f.trackId))
        }
        val audioActive = s.isListening &&
            (s.partialText.isNotBlank() || s.partialTone.volume > 0.35f)
        val active = activeSpeaker.update(tracked, audioActive)
        val aspect = tracked.firstOrNull()?.imageAspect?.takeIf { it > 0f } ?: s.cameraAspect
        // Debounce del "fuera de cuadro": solo lo damos por cierto si hay voz y
        // ninguna cara visible es el hablante durante varios frames seguidos; un
        // unico frame nulo (pausa natural entre silabas) NO debe disparar el aviso.
        offscreenFrames = if (audioActive && active == null) (offscreenFrames + 1) else 0
        val offscreen = offscreenFrames >= OFFSCREEN_FRAMES
        _uiState.update {
            it.copy(faces = tracked, activeFace = active, cameraAspect = aspect, speakerOffscreen = offscreen)
        }
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

    // --- Capa 2 (spec §6): escaneo inicial de rostros + memoria cara↔voz ---

    private class ScanEntry(
        val localId: Int,
        var personId: Int,
        var name: String?,
        val signature: FloatArray,
        val bitmap: Bitmap,
        val image: ImageBitmap,
        // Id de tracking nativo con el que se enrolo (>=0 mientras la persona
        // sigue en cuadro; -1 si vino sembrada de disco). Evita duplicar el mismo
        // rostro y permite marcarlo como "ya agregado" sobre el preview en vivo.
        var trackId: Int,
    )

    private val scanLock = Any()
    private val scanEntries = mutableListOf<ScanEntry>()
    private var scanNextLocalId = 0
    // Ultimas caras detectadas en vivo durante el escaneo (con miniatura y firma),
    // para enrolar justo a la que el usuario toque sobre el preview.
    @Volatile
    private var latestScanFaces: List<DetectedFace> = emptyList()
    // Foto enrolada por indice de hablante (se llena al fusionar cara↔voz).
    private val speakerPhotos = HashMap<Int, ImageBitmap>()

    /** Carga en runtime la memoria de rostros guardada en disco (spec §6). */
    private fun loadFaceMemory() {
        val people = personStore.load()
        faceMemory.setAll(
            people.map { p ->
                FaceMemory.Entry(p.id, p.name, p.signature, personStore.loadPhoto(p.photoPath))
            },
        )
    }

    /** Abre el escaneo inicial, sembrando a las personas ya conocidas. */
    fun startScan() {
        synchronized(scanLock) {
            scanEntries.clear()
            scanNextLocalId = 0
            personStore.load().forEach { p ->
                val photo = personStore.loadPhoto(p.photoPath) ?: return@forEach
                scanEntries.add(
                    ScanEntry(scanNextLocalId++, p.id, p.name, p.signature, photo, photo.asImageBitmap(), -1),
                )
            }
        }
        latestScanFaces = emptyList()
        _uiState.update { it.copy(isScanning = true, scanLiveFaces = emptyList()) }
        publishScan()
    }

    /**
     * Llega desde ML Kit en modo escaneo. NO enrola a nadie por su cuenta: solo
     * refresca las caras visibles en vivo para dibujar sus marcos sobre el
     * preview. El usuario decide a quien agregar tocando su rostro (spec §6).
     */
    fun onScanFaces(faces: List<DetectedFace>) {
        if (!_uiState.value.isScanning) return
        latestScanFaces = faces
        _uiState.update { it.copy(scanLiveFaces = faces) }
    }

    /** Enrola a la persona cuyo rostro (trackId) toco el usuario sobre el preview. */
    fun captureScanFace(trackId: Int) {
        if (trackId < 0) return
        var changed = false
        synchronized(scanLock) {
            val alreadyIn = scanEntries.any { it.trackId == trackId }
            if (!alreadyIn && scanEntries.size < MAX_SCAN_PEOPLE) {
                val face = latestScanFaces.firstOrNull { it.trackId == trackId }
                val sig = face?.signature
                val bmp = face?.thumbnail
                if (sig != null && bmp != null) {
                    scanEntries.add(
                        ScanEntry(scanNextLocalId++, -1, null, sig, bmp, bmp.asImageBitmap(), trackId),
                    )
                    changed = true
                }
            }
        }
        if (changed) publishScan()
    }

    /** Quita a una persona del escaneo (capturada por error o que sobra). */
    fun removeScanPerson(localId: Int) {
        var changed = false
        synchronized(scanLock) {
            changed = scanEntries.removeAll { it.localId == localId }
        }
        if (changed) publishScan()
    }

    /** Pone (o quita) el nombre a una persona del escaneo. */
    fun nameScanPerson(localId: Int, name: String) {
        synchronized(scanLock) {
            scanEntries.firstOrNull { it.localId == localId }?.name = name.trim().ifBlank { null }
        }
        publishScan()
    }

    /** Cierra el escaneo: persiste rostro+nombre+foto y arranca la conversacion. */
    fun finishScan() {
        synchronized(scanLock) {
            var nextId = (scanEntries.maxOfOrNull { it.personId } ?: -1) + 1
            val people = scanEntries.map { e ->
                val id = if (e.personId >= 0) e.personId else nextId++
                val path = personStore.savePhoto(id, e.bitmap)
                PersonStore.Person(id, e.name, e.signature, path)
            }
            personStore.save(people)
            scanEntries.clear()
        }
        latestScanFaces = emptyList()
        loadFaceMemory()
        _uiState.update {
            it.copy(isScanning = false, needsScan = false, scanPeople = emptyList(), scanLiveFaces = emptyList())
        }
    }

    /** Omite el escaneo (conserva la memoria previa de disco). */
    fun skipScan() {
        synchronized(scanLock) { scanEntries.clear() }
        latestScanFaces = emptyList()
        _uiState.update {
            it.copy(isScanning = false, needsScan = false, scanPeople = emptyList(), scanLiveFaces = emptyList())
        }
    }

    private fun publishScan() {
        val list = synchronized(scanLock) {
            scanEntries.map { ScanPerson(it.localId, it.name, it.image, it.trackId) }
        }
        _uiState.update { it.copy(scanPeople = list) }
    }

    /** Identidad (nombre + foto) por indice de hablante, para el avatar del chat. */
    private fun buildIdentities(): Map<Int, SpeakerIdentity> {
        val map = HashMap<Int, SpeakerIdentity>()
        for (sp in speakers.knownSpeakers) {
            val name = speakers.nameOf(sp)
            val photo = speakerPhotos[sp.index]
            if (!name.isNullOrBlank() || photo != null) {
                map[sp.index] = SpeakerIdentity(name, photo)
            }
        }
        return map
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
        // Memoria de rostros (spec §6): si la cara activa coincide con una persona
        // enrolada en el escaneo, su nombre y foto pasan a este hablante para
        // mostrar un avatar real en el chat en vez de un circulo de color.
        val personId = activeFace?.personId ?: -1
        if (personId >= 0) faceMemory.byId(personId)?.let { person ->
            if (!person.name.isNullOrBlank()) speakers.rename(speaker, person.name)
            person.photo?.let { speakerPhotos[speaker.index] = it.asImageBitmap() }
        }
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
                speakerIdentities = buildIdentities(),
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
            lastSuggestKey = null
            _uiState.update { it.copy(suggestedReplies = emptyList()) }
            return
        }
        // Mismo contexto que la ultima vez: no regastamos cuota de Gemini.
        val key = heard.joinToString("|")
        if (key == lastSuggestKey) return
        lastSuggestKey = key

        replyJob?.cancel()
        replyJob = viewModelScope.launch {
            // Respaldo offline al instante (saludo/pregunta/agradecimiento...).
            _uiState.update { it.copy(suggestedReplies = OfflineReplies.forContext(heard)) }
            // Debounce: deja que se acumulen frases muy seguidas antes de llamar.
            delay(700)
            val replies = SmartReplyService.suggest(heard)
            if (replies.isNotEmpty()) {
                _uiState.update { it.copy(suggestedReplies = replies) }
            }
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
