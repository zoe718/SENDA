package com.voxi.captions.ai

import com.voxi.captions.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Respuestas sugeridas con IA (estilo "smart replies" de LinkedIn): a partir de
 * lo último que se escuchó en la conversación, propone 3 respuestas cortas que
 * la persona sorda puede tocar para que el teléfono las diga en voz alta.
 *
 * Usa la API de Gemini (REST, sin SDK, vía [HttpURLConnection] para no agregar
 * dependencias). Es online; si no hay clave, no hay internet o falla, devuelve
 * lista vacía y la UI cae a plantillas offline (ver [OfflineReplies]).
 */
object SmartReplyService {

    // gemini-2.5-flash-lite: rapido y con free tier amplio (gemini-2.0-flash da
    // limite 0 y gemini-3.5-flash solo 20/dia en el free tier nuevo).
    private const val MODEL = "gemini-2.5-flash-lite"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    /** ¿Está configurada la clave de Gemini? */
    val isConfigured: Boolean get() = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * Genera hasta 3 respuestas a partir del contexto (frases escuchadas, de la
     * más antigua a la más reciente). Nunca lanza: devuelve vacío ante error.
     */
    suspend fun suggest(context: List<String>): List<String> = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank() || context.isEmpty()) return@withContext emptyList()
        runCatching {
            val payload = buildRequest(context)
            val url = URL(ENDPOINT)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                // Nuevo esquema de Gemini: la clave va en el header, no en ?key=
                setRequestProperty("X-goog-api-key", key)
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (code !in 200..299) emptyList() else parseReplies(raw)
        }.getOrDefault(emptyList())
    }

    private fun buildRequest(context: List<String>): String {
        val conversation = context.takeLast(6).joinToString("\n") { "- $it" }
        val prompt = buildString {
            append("Eres el asistente de una persona sorda en una conversacion presencial. ")
            append("Esto es lo ultimo que dijeron las personas a su alrededor (transcrito):\n")
            append(conversation)
            append("\n\nSugiere EXACTAMENTE 3 respuestas breves (maximo 8 palabras cada una), ")
            append("naturales, en espanol, que la persona sorda podria responder ahora. ")
            append("Que sean variadas (por ejemplo: una afirmativa, una pregunta y una neutral). ")
            append("Responde SOLO con un arreglo JSON de 3 cadenas de texto, sin nada mas.")
        }
        return JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 200)
                    put("responseMimeType", "application/json")
                },
            )
        }.toString()
    }

    /** Extrae el texto del primer candidato y lo interpreta como arreglo JSON. */
    private fun parseReplies(raw: String): List<String> = runCatching {
        val root = JSONObject(raw)
        val text = root.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
        val arr = when {
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> JSONObject(text).optJSONArray("replies") ?: JSONArray()
            else -> JSONArray()
        }
        (0 until arr.length())
            .map { arr.getString(it).trim() }
            .filter { it.isNotEmpty() }
            .take(3)
    }.getOrDefault(emptyList())
}

/**
 * Plan B sin internet: respuestas genéricas pero útiles. Se usan cuando Gemini
 * no está disponible para que el botón nunca quede vacío.
 */
object OfflineReplies {
    val defaults = listOf("Sí, claro", "¿Puedes repetir?", "No estoy seguro")
}
