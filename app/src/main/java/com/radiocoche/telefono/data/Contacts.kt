package com.radiocoche.telefono.data

import android.content.Context
import android.provider.ContactsContract
import java.text.Normalizer

/** Un contacto con un número (si tiene varios, sale una vez por número). */
data class Contact(val name: String, val number: String, val label: String) {
    val key: String get() = Contacts.digits(number).takeLast(9)
    val initials: String get() = Contacts.initials(name)
}

/**
 * Agenda. La app de fábrica copia los contactos del móvil a la agenda de Android de la unidad;
 * de ahí se leen (sin escribir nada en ella).
 */
object Contacts {

    fun load(ctx: Context): List<Contact> {
        val out = mutableListOf<Contact>()
        val seen = HashSet<String>()
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)?.trim().orEmpty()
                    val num = c.getString(1)?.trim().orEmpty()
                    if (num.isEmpty()) continue
                    val k = name + "|" + digits(num).takeLast(9)
                    if (!seen.add(k)) continue
                    val label = runCatching {
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(ctx.resources, c.getInt(2), c.getString(3)).toString()
                    }.getOrDefault("")
                    out += Contact(name.ifEmpty { num }, num, label)
                }
            }
        }
        return out.sortedWith(compareBy { fold(it.name) })
    }

    fun digits(s: String): String = s.filter { it.isDigit() }

    /** Sin acentos y en minúsculas, para ordenar y buscar. */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    fun initials(name: String): String {
        val words = name.split(' ', '-', '.').filter { w -> w.any { it.isLetter() } }
        val first = words.take(2).joinToString("") { w -> w.first { it.isLetter() }.uppercase() }
        return first.ifEmpty { "#" }
    }

    /** Número con espacios para leerlo mejor (9 cifras españolas: 3-2-2-2 o 3-3-3). */
    fun pretty(n: String): String {
        val d = n.filter { it.isDigit() || it == '+' }
        if (d.length == 9 && (d[0] == '6' || d[0] == '7')) return "${d.substring(0, 3)} ${d.substring(3, 5)} ${d.substring(5, 7)} ${d.substring(7)}"
        if (d.length == 9) return "${d.substring(0, 2)} ${d.substring(2, 5)} ${d.substring(5, 7)} ${d.substring(7)}"
        return n
    }
}

/** Favoritos: se marcan con la estrella en esta app y se guardan por número (sobreviven a la resincronización). */
object Favorites {
    private const val KEY = "favoritos"

    fun get(ctx: Context): Set<String> =
        ctx.getSharedPreferences("telefono", Context.MODE_PRIVATE).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun toggle(ctx: Context, key: String): Set<String> {
        val cur = get(ctx).toMutableSet()
        if (!cur.add(key)) cur.remove(key)
        ctx.getSharedPreferences("telefono", Context.MODE_PRIVATE).edit().putStringSet(KEY, cur).apply()
        return cur
    }
}
