package com.example.grouplauncher

import android.content.Context
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Un grupo. Los subgrupos reutilizan la misma clase y heredan columnas/filas/tamaño del padre. */
data class Group(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val cols: Int = 4,
    val rows: Int = 2,
    val size: Int = 52,
    val apps: List<String> = emptyList(),
    val subs: List<Group> = emptyList(),
    val color: Long = 0x15171C,
    val font: Int = 0,
)

fun Group.toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("cols", cols)
    .put("rows", rows).put("size", size).put("apps", JSONArray(apps))
    .put("subs", JSONArray(subs.map { it.toJson() })).put("color", color).put("font", font)

fun JSONObject.toGroup(): Group = Group(
    getString("id"), getString("name"), getInt("cols"), getInt("rows"), getInt("size"),
    getJSONArray("apps").let { a -> List(a.length()) { a.getString(it) } },
    getJSONArray("subs").let { a -> List(a.length()) { a.getJSONObject(it).toGroup() } },
    optLong("color", 0x15171C),
    optInt("font", 0),
)

class LauncherState(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    var edit by mutableStateOf(false)
    var groups by mutableStateOf(
        runCatching {
            JSONArray(prefs.getString("groups", "[]")).let { a -> List(a.length()) { a.getJSONObject(it).toGroup() } }
        }.getOrDefault(emptyList())
    )
        private set

    var homeEnabled by mutableStateOf(prefs.getBoolean("homeOn", true))
        private set
    var drawerOpen by mutableStateOf(false)
    private fun load(key: String, def: List<Group>) = runCatching {
        JSONArray(prefs.getString(key, null)!!).let { a -> List(a.length()) { a.getJSONObject(it).toGroup() } }
    }.getOrDefault(def)
    /** Páginas de la pantalla Home (se conservan aunque el Home se desactive). */
    var home by mutableStateOf(load("home", listOf(Group(name = "Inicio", rows = 5, size = 56))))
        private set
    /** Configuración de la tarjeta "Sin grupo". */
    var ung by mutableStateOf(load("ung", listOf(Group(id = "ungrouped", name = "Sin grupo", rows = 4))).first())
        private set
    fun setHome(l: List<Group>) { home = l; prefs.edit().putString("home", JSONArray(l.map { it.toJson() }).toString()).apply() }
    fun setUng(g: Group) { ung = g; prefs.edit().putString("ung", JSONArray(listOf(g.toJson())).toString()).apply() }
    fun toggleHome() { homeEnabled = !homeEnabled; prefs.edit().putBoolean("homeOn", homeEnabled).apply() }
    fun find(id: String): Group? = (groups + groups.flatMap { it.subs } + home + ung).find { it.id == id }

    fun set(list: List<Group>) {
        groups = list
        prefs.edit().putString("groups", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    /** Actualiza un grupo o subgrupo por id. */
    fun upd(id: String, f: (Group) -> Group) {
        if (id == ung.id) return setUng(f(ung))
        if (home.any { it.id == id }) return setHome(home.map { if (it.id == id) f(it) else it })
        set(groups.map { g -> if (g.id == id) f(g) else g.copy(subs = g.subs.map { if (it.id == id) f(it) else it }) })
    }

    fun move(i: Int, d: Int) {
        val j = i + d
        if (j !in groups.indices) return
        set(groups.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t })
    }

    fun remove(id: String) = set(groups.filter { it.id != id }.map { g -> g.copy(subs = g.subs.filter { it.id != id }) })
}
