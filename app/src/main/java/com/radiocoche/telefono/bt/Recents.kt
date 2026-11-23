package com.radiocoche.telefono.bt

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Llamadas recientes. La app de fábrica guarda las suyas en un archivo privado que no se puede leer,
 * así que esta app apunta las que ve pasar (mientras su servicio está en marcha). Se guardan las 60 últimas.
 */
object Recents {
    enum class Kind { IN, OUT, MISSED }

    data class Entry(val kind: Kind, val name: String, val number: String, val time: Long, val seconds: Int)

    var list by mutableStateOf<List<Entry>>(emptyList())
        private set

    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "recientes.json")

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        list = runCatching {
            val arr = JSONArray(file(ctx).readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(
                    Kind.valueOf(o.optString("k", "OUT")), o.optString("n"), o.optString("u"),
                    o.optLong("t"), o.optInt("s"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(ctx: Context, kind: Kind, name: String, number: String, seconds: Int) {
        if (name.isBlank() && number.isBlank()) return
        load(ctx)
        list = (listOf(Entry(kind, name, number, System.currentTimeMillis(), seconds)) + list).take(60)
        runCatching {
            val arr = JSONArray()
            for (e in list) arr.put(
                JSONObject().put("k", e.kind.name).put("n", e.name).put("u", e.number).put("t", e.time).put("s", e.seconds),
            )
            file(ctx).writeText(arr.toString())
        }
    }
}
