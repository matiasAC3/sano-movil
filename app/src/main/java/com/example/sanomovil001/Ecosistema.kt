package com.example.sanomovil001

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TipoDispositivo(val titulo: String) {
    TELEFONO("Teléfono"),
    TABLET("Tablet"),
    OTRO("Otro equipo")
}

/** Un equipo que la persona registró a mano (por ejemplo, la tablet o el teléfono de un familiar). */
data class DispositivoFamilia(
    val id: String,
    val tipo: TipoDispositivo,
    val marca: String,
    val modelo: String,
    val apodo: String
) {
    val nombre: String get() = apodo.ifEmpty { "$marca $modelo" }
}

/**
 * Lista local de equipos del "ecosistema". Solo se guardan datos que la persona escribe (marca, modelo, nombre).
 * Todo lo que entra o se lee se valida: solo letras, números y unos pocos signos, máximo 40 caracteres.
 */
internal object Ecosistema {
    const val ESTE_EQUIPO = "este"
    const val MAX = 8

    private const val ARCHIVO = "sano_ecosistema"
    private val TEXTO = Regex("^[\\p{L}\\p{N} ._()+/-]{1,40}$")
    private val ID = Regex("^[a-f0-9]{8}$")

    private fun textoValido(t: String): Boolean = TEXTO.matches(t)

    fun leer(ctx: Context): List<DispositivoFamilia> {
        val bruto = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).getString("lista", null)
            ?: return emptyList()
        val arr = runCatching { JSONArray(bruto) }.getOrNull() ?: return emptyList()
        val res = mutableListOf<DispositivoFamilia>()
        for (i in 0 until minOf(arr.length(), MAX)) {
            val o = arr.optJSONObject(i) ?: continue
            val tipo = TipoDispositivo.values().firstOrNull { it.name == o.optString("tipo") } ?: continue
            val id = o.optString("id")
            val marca = o.optString("marca").trim()
            val modelo = o.optString("modelo").trim()
            val apodo = o.optString("apodo").trim()
            if (!ID.matches(id) || !textoValido(marca) || !textoValido(modelo)) continue
            if (apodo.isNotEmpty() && !textoValido(apodo)) continue
            res.add(DispositivoFamilia(id, tipo, marca, modelo, apodo))
        }
        return res
    }

    private fun guardar(ctx: Context, lista: List<DispositivoFamilia>) {
        val arr = JSONArray()
        for (d in lista) {
            arr.put(
                JSONObject().put("id", d.id).put("tipo", d.tipo.name)
                    .put("marca", d.marca).put("modelo", d.modelo).put("apodo", d.apodo)
            )
        }
        ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit().putString("lista", arr.toString()).apply()
    }

    /** Devuelve null si se guardó bien, o el mensaje de error para mostrarle a la persona. */
    fun agregar(ctx: Context, tipo: TipoDispositivo, marca: String, modelo: String, apodo: String): String? {
        val m = marca.trim()
        val mo = modelo.trim()
        val a = apodo.trim()
        val lista = leer(ctx)
        return when {
            lista.size >= MAX -> "Llegaste al máximo de $MAX equipos. Elimina uno para agregar otro."
            !textoValido(m) -> "Revisa la marca: usa solo letras, números y espacios."
            !textoValido(mo) -> "Revisa el modelo: usa solo letras, números y espacios."
            a.isNotEmpty() && !textoValido(a) -> "Revisa el nombre: usa solo letras, números y espacios."
            else -> {
                val id = UUID.randomUUID().toString().replace("-", "").take(8)
                guardar(ctx, lista + DispositivoFamilia(id, tipo, m, mo, a))
                null
            }
        }
    }

    fun eliminar(ctx: Context, id: String) {
        guardar(ctx, leer(ctx).filter { it.id != id })
        quitar(ctx, id)
    }

    // ---------- Equipos colocados en la zona "Mis dispositivos" (se guarda el orden) ----------

    private fun idValido(id: String): Boolean = id == ESTE_EQUIPO || ID.matches(id)

    fun colocados(ctx: Context): List<String> {
        val bruto = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).getString("colocados", null)
            ?: return emptyList()
        val arr = runCatching { JSONArray(bruto) }.getOrNull() ?: return emptyList()
        val ids = mutableListOf<String>()
        for (i in 0 until minOf(arr.length(), MAX + 1)) {
            val id = arr.optString(i)
            if (idValido(id) && id !in ids) ids.add(id)
        }
        return ids
    }

    private fun guardarColocados(ctx: Context, ids: List<String>) {
        ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit()
            .putString("colocados", JSONArray(ids).toString()).apply()
    }

    fun colocar(ctx: Context, id: String) {
        val l = colocados(ctx)
        if (idValido(id) && id !in l) guardarColocados(ctx, l + id)
    }

    fun quitar(ctx: Context, id: String) = guardarColocados(ctx, colocados(ctx).filter { it != id })

    /** Sube (-1) o baja (+1) un equipo en la lista. */
    fun mover(ctx: Context, id: String, delta: Int) {
        val l = leer(ctx).toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i))
        guardar(ctx, l)
    }
}
