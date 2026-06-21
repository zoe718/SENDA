package com.voxi.captions.viewmodel

import com.voxi.captions.data.SpeakerStore
import com.voxi.captions.model.Speaker
import com.voxi.captions.model.VoiceProfile
import kotlin.math.sqrt

/**
 * Diarización por huella de voz para N hablantes, con memoria persistente
 * estilo Alexa (spec §6).
 *
 * Cada voz es una gaussiana diagonal adaptativa en el espacio
 * (pitch mediano, dispersión de pitch, brillo/timbre). Cada frase fija se
 * compara contra todas las voces conocidas y:
 *  - si encaja en alguna (distancia pequeña) → es esa persona;
 *  - si no se parece a ninguna (distancia > [NEW_SPEAKER_THRESHOLD]) y aún hay
 *    cupo → se crea un hablante nuevo con el siguiente índice/color;
 *  - una voz que regresa vuelve a su mismo hablante.
 *
 * Las voces se guardan en [SpeakerStore], así que una persona reconocida en una
 * sesión se vuelve a reconocer al reabrir la app. La asignación usa distancia de
 * Mahalanobis diagonal (z-score) con piso de varianza e histéresis por margen
 * para no parpadear en la frontera entre voces parecidas.
 */
class SpeakerTracker(private val store: SpeakerStore? = null) {

    companion object {
        // Adaptación de la gaussiana de cada hablante (0..1; más alto = más rápido).
        private const val ADAPT = 0.2f
        // Piso de desviación por dimensión (evita dividir por ~0 con pocos datos).
        private const val MIN_STD = 0.06f
        // Varianza inicial razonable de una voz (en unidades normalizadas 0..1).
        private const val INIT_VAR = 0.02f
        // Distancia de Mahalanobis por encima de la cual una voz se considera
        // desconocida y se crea un hablante nuevo.
        private const val NEW_SPEAKER_THRESHOLD = 2.8f
        // Margen (en distancia) para cambiar de hablante respecto al último.
        private const val SWITCH_MARGIN = 0.8f
        // Tope de hablantes distintos (después se reusa el más cercano).
        const val MAX_SPEAKERS = 8
        // Pesos por dimensión: el pitch y el timbre son los más discriminativos.
        private const val W_PITCH = 2.0f
        private const val W_SPREAD = 1.0f
        private const val W_BRIGHT = 1.5f
        // Tramas con voz minimas para considerar la huella "fuerte". Solo una
        // huella fuerte puede CREAR un hablante nuevo o MOVER el centro de una
        // voz; las debiles (frases muy cortas) se asignan pero no contaminan.
        // Esto evita los duplicados por frases sueltas.
        private const val STRONG_FRAMES = 12
        // Distancia (euclidea ponderada entre medias) por debajo de la cual dos
        // voces se consideran la misma y se fusionan (limpia duplicados ya
        // creados cuando una persona quedo partida en dos).
        private const val MERGE_DIST = 0.16f
        // "Madurez" inicial de las voces cargadas de disco: ya son establecidas,
        // no deben fusionarse ni desplazarse a la ligera.
        private const val MATURE_COUNT = 10

        // --- Diarizacion neuronal (sherpa-onnx, hibrida) ---
        // Cuando hay un embedding disponible, manda sobre la heuristica. Son
        // similitudes COSENO entre vectores L2-normalizados (1 = identico).
        // Coseno >= esto: misma persona (asigna y adapta el centro).
        private const val EMB_SAME = 0.55f
        // Coseno del mejor < esto: claramente desconocida -> hablante nuevo.
        private const val EMB_NEW = 0.40f
        // Dos centros con coseno > esto son la misma voz partida -> fusionar.
        private const val EMB_MERGE = 0.72f
        // Adaptacion del centro de embedding por EMA (0..1).
        private const val EMB_ADAPT = 0.30f
    }

    /** Gaussiana diagonal adaptativa de un hablante en (pitch, spread, brillo). */
    private class Cluster(
        val index: Int,
        var name: String?,
        pitch: Float,
        spread: Float,
        bright: Float,
    ) {
        var meanPitch = pitch
        var meanSpread = spread
        var meanBright = bright
        var varPitch = INIT_VAR
        var varSpread = INIT_VAR
        var varBright = INIT_VAR
        // Cuantas intervenciones se han asignado a esta voz (su "peso").
        var count = 1
        // Centro del embedding neuronal (sherpa-onnx), L2-normalizado, o null si
        // esta voz aun no tiene huella neuronal asociada.
        var emb: FloatArray? = null
        // Galeria de huellas de una voz ENROLADA (varias ventanas del
        // enrolamiento). Se compara por coseno MAXIMO contra ella, lo que
        // reconoce mucho mejor que solo el centroide. Fija (no se adapta).
        var embGallery: List<FloatArray>? = null
        // Si esta voz vino de una persona ENROLADA en el escaneo, su id en
        // PersonStore (>=0); -1 si se descubrio sola durante la conversacion.
        var personId: Int = -1

        /** Distancia de Mahalanobis diagonal (z-score) ponderada al vector dado. */
        fun distance(pitch: Float, spread: Float, bright: Float): Float {
            val sp = varPitch.coerceAtLeast(MIN_STD * MIN_STD)
            val ss = varSpread.coerceAtLeast(MIN_STD * MIN_STD)
            val sb = varBright.coerceAtLeast(MIN_STD * MIN_STD)
            val dp = pitch - meanPitch
            val ds = spread - meanSpread
            val db = bright - meanBright
            return sqrt(
                W_PITCH * dp * dp / sp +
                    W_SPREAD * ds * ds / ss +
                    W_BRIGHT * db * db / sb,
            )
        }

        /** Actualiza media y varianza por EMA hacia el nuevo vector. */
        fun update(pitch: Float, spread: Float, bright: Float) {
            val dp = pitch - meanPitch
            meanPitch += ADAPT * dp
            varPitch = (1f - ADAPT) * (varPitch + ADAPT * dp * dp)
            val ds = spread - meanSpread
            meanSpread += ADAPT * ds
            varSpread = (1f - ADAPT) * (varSpread + ADAPT * ds * ds)
            val db = bright - meanBright
            meanBright += ADAPT * db
            varBright = (1f - ADAPT) * (varBright + ADAPT * db * db)
        }

        fun toProfile() = SpeakerStore.Profile(
            index, name, meanPitch, meanSpread, meanBright, varPitch, varSpread, varBright,
        )
    }

    private val clusters = mutableListOf<Cluster>()
    private var nextIndex = 0
    private var lastSpeaker = Speaker.First
    private var manual: Speaker? = null

    init {
        // Carga las voces aprendidas en sesiones anteriores (memoria tipo Alexa).
        store?.load()?.forEach { p ->
            val c = Cluster(p.index, p.name, p.meanPitch, p.meanSpread, p.meanBright)
            c.varPitch = p.varPitch
            c.varSpread = p.varSpread
            c.varBright = p.varBright
            c.count = MATURE_COUNT
            clusters.add(c)
        }
        nextIndex = (clusters.maxOfOrNull { it.index } ?: -1) + 1
        if (clusters.isNotEmpty()) lastSpeaker = Speaker(clusters.first().index)
    }

    val manualSpeaker: Speaker? get() = manual
    val currentSpeaker: Speaker get() = manual ?: lastSpeaker

    /** Hablantes ya descubiertos, en orden de aparición (para la UI). */
    val knownSpeakers: List<Speaker>
        get() = if (clusters.isEmpty()) listOf(Speaker.First)
        else clusters.map { Speaker(it.index) }

    fun nameOf(speaker: Speaker): String? =
        clusters.firstOrNull { it.index == speaker.index }?.name

    fun rename(speaker: Speaker, name: String) {
        clusters.firstOrNull { it.index == speaker.index }?.name = name.ifBlank { null }
        persist()
    }

    fun setManual(speaker: Speaker?) {
        manual = speaker
        if (speaker != null) lastSpeaker = speaker
    }

    /**
     * Clasifica una frase ya fijada a partir de su huella de voz.
     *
     * [faceHint] es una pista FUERTE de la fusion cara-voz (spec 6): si la cara
     * que esta hablando ya se asocio antes con una voz conocida, esa intervencion
     * va a esa misma persona aunque el audio sea ambiguo. Asi no se confunden ni
     * se duplican hablantes cuando vemos la cara del que habla. La huella de voz
     * (heuristica/embedding) se sigue aprendiendo dentro de esa misma voz.
     */
    fun classify(
        profile: VoiceProfile,
        embedding: FloatArray? = null,
        faceHint: Speaker? = null,
    ): Speaker {
        manual?.let { return it }

        // Pista cara-voz: si la cara activa ya tiene una voz conocida asociada,
        // mandamos la intervencion a esa voz y aprendemos su huella ahi mismo.
        if (faceHint != null) {
            val c = clusters.firstOrNull { it.index == faceHint.index }
            if (c != null) {
                // Las voces enroladas mantienen su huella ancla (no derivan).
                if (embedding != null && c.personId < 0) updateEmb(c, embedding)
                if (profile.voiced && profile.frames >= STRONG_FRAMES) {
                    c.update(profile.medianPitch, profile.pitchSpread, profile.brightness)
                }
                c.count++
                lastSpeaker = Speaker(c.index)
                persist()
                return lastSpeaker
            }
        }

        // Si hay huella neuronal (sherpa-onnx), manda sobre la heuristica.
        if (embedding != null) return classifyByEmbedding(profile, embedding)

        // Sin voz fiable: no toques el modelo, conserva al último hablante.
        if (!profile.voiced) return lastSpeaker

        val pitch = profile.medianPitch
        val spread = profile.pitchSpread
        val bright = profile.brightness
        // Una huella es "fuerte" si se construyo con suficientes tramas de voz.
        val strong = profile.frames >= STRONG_FRAMES

        // Arranque en frío: la primera voz define el Hablante 1.
        if (clusters.isEmpty()) {
            clusters.add(Cluster(nextIndex, null, pitch, spread, bright))
            lastSpeaker = Speaker(nextIndex)
            nextIndex++
            persist()
            return lastSpeaker
        }

        // Voz más cercana entre las conocidas.
        var nearest = clusters[0]
        var nearestDist = nearest.distance(pitch, spread, bright)
        for (c in clusters) {
            val d = c.distance(pitch, spread, bright)
            if (d < nearestDist) {
                nearest = c
                nearestDist = d
            }
        }

        // No se parece a nadie conocido y hay cupo → hablante nuevo, PERO solo si
        // la huella es fuerte. Una frase corta y rara NO crea un duplicado: se
        // asigna a la voz mas cercana sin tocar su modelo.
        if (nearestDist > NEW_SPEAKER_THRESHOLD && clusters.size < MAX_SPEAKERS && strong) {
            val nuevo = Cluster(nextIndex, null, pitch, spread, bright)
            clusters.add(nuevo)
            lastSpeaker = Speaker(nuevo.index)
            nextIndex++
            persist()
            return lastSpeaker
        }

        // Histéresis: para cambiar de hablante, el candidato debe ganarle por
        // margen al último; si no, conserva al último (evita parpadeo).
        val lastCluster = clusters.firstOrNull { it.index == lastSpeaker.index }
        val chosen = if (lastCluster != null && lastCluster !== nearest) {
            val dLast = lastCluster.distance(pitch, spread, bright)
            if (nearestDist + SWITCH_MARGIN < dLast) nearest else lastCluster
        } else {
            nearest
        }

        // Solo las huellas fuertes desplazan el centro de la voz; asi una frase
        // corta no "arrastra" el modelo hacia el ruido.
        if (strong) {
            chosen.update(pitch, spread, bright)
            chosen.count++
            mergeNearDuplicates()
        }
        lastSpeaker = Speaker(
            clusters.firstOrNull { it.index == chosen.index }?.index ?: chosen.index,
        )
        persist()
        return lastSpeaker
    }

    /** Distancia euclidea ponderada entre los centros de dos voces. */
    private fun centroidDistance(a: Cluster, b: Cluster): Float {
        val dp = a.meanPitch - b.meanPitch
        val ds = a.meanSpread - b.meanSpread
        val db = a.meanBright - b.meanBright
        return sqrt(W_PITCH * dp * dp + W_SPREAD * ds * ds + W_BRIGHT * db * db)
    }

    /**
     * Fusiona voces que quedaron demasiado cerca (la misma persona partida en
     * dos). Conserva la de mayor [Cluster.count] (mas evidencia) y le suma la
     * otra. Reapunta [lastSpeaker] al superviviente si hace falta.
     */
    private fun mergeNearDuplicates() {
        var merged = true
        while (merged && clusters.size > 1) {
            merged = false
            outer@ for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val a = clusters[i]
                    val b = clusters[j]
                    // Nunca fusionamos voces enroladas: cada persona del escaneo
                    // conserva su propio cluster (nombre/foto/personId).
                    if (a.personId >= 0 || b.personId >= 0) continue
                    if (centroidDistance(a, b) < MERGE_DIST) {
                        val keep = if (a.count >= b.count) a else b
                        val drop = if (keep === a) b else a
                        // Mezcla ponderada de los centros hacia el superviviente.
                        val total = (keep.count + drop.count).coerceAtLeast(1)
                        val w = drop.count.toFloat() / total
                        keep.meanPitch += w * (drop.meanPitch - keep.meanPitch)
                        keep.meanSpread += w * (drop.meanSpread - keep.meanSpread)
                        keep.meanBright += w * (drop.meanBright - keep.meanBright)
                        keep.count += drop.count
                        if (keep.name == null) keep.name = drop.name
                        if (lastSpeaker.index == drop.index) lastSpeaker = Speaker(keep.index)
                        clusters.remove(drop)
                        merged = true
                        break@outer
                    }
                }
            }
        }
    }

    // --- Diarizacion por embedding (sherpa-onnx) ---

    /**
     * Clasifica usando la huella neuronal (coseno). Conserva los mismos indices
     * y colores que la via heuristica para no romper la UI, y sigue alimentando
     * las medias de pitch/timbre como respaldo si el modelo dejara de estar.
     */
    private fun classifyByEmbedding(profile: VoiceProfile, x: FloatArray): Speaker {
        val pitch = profile.medianPitch
        val spread = profile.pitchSpread
        val bright = profile.brightness

        // Arranque en frio total: primera voz = Hablante 1.
        if (clusters.isEmpty()) {
            val c = Cluster(nextIndex, null, pitch, spread, bright)
            c.emb = x.copyOf()
            clusters.add(c)
            lastSpeaker = Speaker(nextIndex)
            nextIndex++
            persist()
            return lastSpeaker
        }

        // Voces que ya tienen embedding asociado.
        val withEmb = clusters.filter { it.emb != null }
        if (withEmb.isEmpty()) {
            // Puente: aun nadie tiene embedding (voces cargadas de disco o
            // descubiertas por la heuristica antes de que cargara el modelo). En
            // vez de crear un duplicado, pega este embedding a la voz mas
            // parecida segun la heuristica.
            val seed = heuristicNearest(profile)
            seed.emb = x.copyOf()
            if (profile.voiced) {
                seed.update(pitch, spread, bright)
                seed.count++
            }
            lastSpeaker = Speaker(seed.index)
            persist()
            return lastSpeaker
        }

        // Mejor coincidencia por coseno MAXIMO contra centroide + galeria.
        var best = withEmb[0]
        var bestSim = simTo(best, x)
        for (c in withEmb) {
            val s = simTo(c, x)
            if (s > bestSim) {
                bestSim = s
                best = c
            }
        }

        // Claramente desconocida y hay cupo -> hablante nuevo.
        if (bestSim < EMB_NEW && clusters.size < MAX_SPEAKERS) {
            val c = Cluster(nextIndex, null, pitch, spread, bright)
            c.emb = x.copyOf()
            clusters.add(c)
            lastSpeaker = Speaker(nextIndex)
            nextIndex++
            persist()
            return lastSpeaker
        }

        // Coincide con claridad: asigna y adapta el centro. En la zona gris
        // (entre NEW y SAME) se asigna al mejor pero NO se mueve el centro, para
        // no contaminar la voz con una frase ambigua.
        if (bestSim >= EMB_SAME) {
            // No movemos la huella de una voz enrolada: es el ancla de referencia.
            if (best.personId < 0) updateEmb(best, x)
            if (profile.voiced) best.update(pitch, spread, bright)
            best.count++
            mergeNearDuplicatesEmb()
        }
        lastSpeaker = Speaker(best.index)
        persist()
        return lastSpeaker
    }

    /** Voz mas cercana por la heuristica (para el puente de embeddings). */
    private fun heuristicNearest(profile: VoiceProfile): Cluster {
        if (!profile.voiced) {
            return clusters.firstOrNull { it.index == lastSpeaker.index } ?: clusters[0]
        }
        var nearest = clusters[0]
        var nearestDist = nearest.distance(profile.medianPitch, profile.pitchSpread, profile.brightness)
        for (c in clusters) {
            val d = c.distance(profile.medianPitch, profile.pitchSpread, profile.brightness)
            if (d < nearestDist) {
                nearest = c
                nearestDist = d
            }
        }
        return nearest
    }

    /**
     * Similitud de [x] a una voz: coseno MAXIMO contra su centroide y, si es una
     * voz enrolada, contra cada huella de su galeria. Asi una frase real que se
     * parezca a CUALQUIER ventana del enrolamiento se reconoce, aunque no coincida
     * con el promedio.
     */
    private fun simTo(c: Cluster, x: FloatArray): Float {
        var best = c.emb?.let { cosine(it, x) } ?: -1f
        c.embGallery?.let { g ->
            for (e in g) {
                val s = cosine(e, x)
                if (s > best) best = s
            }
        }
        return best
    }

    /** Coseno entre dos vectores (los embeddings ya vienen ~L2-normalizados). */
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val den = sqrt(na) * sqrt(nb)
        return if (den < 1e-6f) 0f else dot / den
    }

    /** Adapta el centro de embedding hacia el nuevo vector y renormaliza. */
    private fun updateEmb(cluster: Cluster, x: FloatArray) {
        val cur = cluster.emb
        if (cur == null) {
            cluster.emb = x.copyOf()
            return
        }
        val n = minOf(cur.size, x.size)
        var norm = 0f
        for (i in 0 until n) {
            cur[i] += EMB_ADAPT * (x[i] - cur[i])
            norm += cur[i] * cur[i]
        }
        val s = sqrt(norm)
        if (s > 1e-6f) for (i in 0 until n) cur[i] /= s
    }

    /** Fusiona voces cuyos embeddings quedaron casi identicos (misma persona). */
    private fun mergeNearDuplicatesEmb() {
        var merged = true
        while (merged && clusters.size > 1) {
            merged = false
            outer@ for (i in clusters.indices) {
                for (j in i + 1 until clusters.size) {
                    val a = clusters[i]
                    val b = clusters[j]
                    // Las voces enroladas no se fusionan (ver mergeNearDuplicates).
                    if (a.personId >= 0 || b.personId >= 0) continue
                    val ea = a.emb
                    val eb = b.emb
                    if (ea != null && eb != null && cosine(ea, eb) > EMB_MERGE) {
                        val keep = if (a.count >= b.count) a else b
                        val drop = if (keep === a) b else a
                        keep.count += drop.count
                        if (keep.name == null) keep.name = drop.name
                        if (lastSpeaker.index == drop.index) lastSpeaker = Speaker(keep.index)
                        clusters.remove(drop)
                        merged = true
                        break@outer
                    }
                }
            }
        }
    }

    /**
     * Reset suave: arranca una conversación nueva PERO conserva las voces
     * aprendidas (memoria tipo Alexa). Solo olvida cuál fue el último hablante.
     */
    fun softReset() {
        lastSpeaker = manual ?: (clusters.firstOrNull()?.let { Speaker(it.index) } ?: Speaker.First)
    }

    /**
     * Reset de sesión: una conversación nueva empieza con hablantes NUEVOS (no
     * arrastra las voces de antes), pero NO borra el disco. La memoria de largo
     * plazo sigue guardada, así que al reiniciar la app se vuelven a reconocer;
     * las voces que se aprendan en esta sesión nueva se guardan con normalidad y
     * reemplazan a las anteriores. "Empezar de cero" sin perder la persistencia.
     */
    fun sessionReset() {
        clusters.clear()
        nextIndex = 0
        lastSpeaker = manual ?: Speaker.First
        // A propósito NO llamamos store?.clear(): el disco conserva la memoria.
    }

    /** Olvida por completo a todas las voces (también en disco). */
    fun forgetAll() {
        clusters.clear()
        nextIndex = 0
        lastSpeaker = manual ?: Speaker.First
        store?.clear()
    }

    // --- Siembra de voces enroladas en el escaneo (spec 6) ---

    /** Voz capturada durante el escaneo: persona + nombre + huella neuronal. */
    data class Enrolled(
        val personId: Int,
        val name: String?,
        val embedding: FloatArray,
        val gallery: List<FloatArray> = emptyList(),
    )

    /**
     * Siembra las voces grabadas durante el escaneo. Crea un cluster ya "maduro"
     * por cada persona enrolada, con su huella neuronal y su nombre, para
     * reconocerla desde la primera palabra AUNQUE no se le vea la cara y sin
     * arranque en frio. Reemplaza cualquier siembra anterior (idempotente) y NO
     * se persiste en [SpeakerStore]: su memoria vive en PersonStore. Devuelve el
     * indice de hablante asignado a cada personId, para enlazar su foto/avatar.
     */
    fun seedEnrolled(entries: List<Enrolled>): Map<Int, Int> {
        // Quita siembras previas para no duplicar al re-sembrar.
        clusters.removeAll { it.personId >= 0 }
        val map = HashMap<Int, Int>()
        for (e in entries) {
            if (e.embedding.isEmpty()) continue
            val c = Cluster(nextIndex, e.name, 0.5f, 0.1f, 0.5f)
            c.emb = normalizedCopy(e.embedding)
            c.embGallery = e.gallery
                .map { normalizedCopy(it) }
                .takeIf { it.isNotEmpty() }
            c.personId = e.personId
            c.count = MATURE_COUNT
            clusters.add(c)
            map[e.personId] = nextIndex
            nextIndex++
        }
        // Reapunta al ultimo hablante si quedo sobre un cluster retirado.
        if (clusters.none { it.index == lastSpeaker.index }) {
            lastSpeaker = manual ?: (clusters.firstOrNull()?.let { Speaker(it.index) } ?: Speaker.First)
        }
        persist()
        return map
    }

    /** Indice de hablante ligado a una persona enrolada, o null. */
    fun speakerForPerson(personId: Int): Speaker? =
        clusters.firstOrNull { it.personId == personId }?.let { Speaker(it.index) }

    private fun normalizedCopy(x: FloatArray): FloatArray {
        var n = 0f
        for (v in x) n += v * v
        val s = sqrt(n)
        if (s < 1e-6f) return x.copyOf()
        return FloatArray(x.size) { x[it] / s }
    }

    private fun persist() {
        // Solo se guardan las voces descubiertas en conversacion; las enroladas
        // (personId>=0) viven en PersonStore y se re-siembran al arrancar.
        store?.save(clusters.filter { it.personId < 0 }.map { it.toProfile() })
    }
}
