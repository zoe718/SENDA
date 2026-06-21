package com.voxi.captions.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Memoria de rostros (spec §6, fusión cara↔voz). Guarda en disco a las personas
 * "enroladas" durante el escaneo inicial: un id estable, su nombre opcional, una
 * firma geométrica del rostro (para re-identificarla en sesiones futuras) y una
 * miniatura (PNG) para mostrar su foto en el chat en vez de un círculo de color.
 *
 * La firma se serializa con org.json (sin libs extra) y la foto se guarda como
 * archivo en `filesDir/faces/`. Si algo está corrupto, se arranca de cero sin
 * romper la app (spec §10).
 */
class PersonStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences("voxi_people", Context.MODE_PRIVATE)
    private val facesDir = File(appContext.filesDir, "faces").apply { mkdirs() }

    /** Persona enrolada: rostro + nombre + foto. */
    data class Person(
        val id: Int,
        val name: String?,
        val signature: FloatArray,
        val photoPath: String?,
    )

    fun load(): List<Person> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val sigArr = o.getJSONArray("sig")
                val sig = FloatArray(sigArr.length()) { j -> sigArr.getDouble(j).toFloat() }
                if (sig.isEmpty()) return@mapNotNull null
                Person(
                    id = o.getInt("id"),
                    name = if (o.isNull("name")) null else o.getString("name"),
                    signature = sig,
                    photoPath = if (o.isNull("photo")) null else o.getString("photo"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(people: List<Person>) {
        val arr = JSONArray()
        people.forEach { p ->
            val sig = JSONArray()
            p.signature.forEach { sig.put(it.toDouble()) }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name ?: JSONObject.NULL)
                    .put("sig", sig)
                    .put("photo", p.photoPath ?: JSONObject.NULL),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** Guarda una miniatura PNG para [id] y devuelve su ruta, o null si falla. */
    fun savePhoto(id: Int, bitmap: Bitmap): String? = runCatching {
        val file = File(facesDir, "face_$id.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.absolutePath
    }.getOrNull()

    /** Carga una foto guardada como [Bitmap], o null si no existe/falla. */
    fun loadPhoto(path: String?): Bitmap? {
        if (path.isNullOrEmpty()) return null
        return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        runCatching { facesDir.listFiles()?.forEach { it.delete() } }
    }

    companion object {
        private const val KEY = "people"
    }
}
