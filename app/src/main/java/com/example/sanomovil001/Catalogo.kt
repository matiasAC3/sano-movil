package com.example.sanomovil001

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Un paso de una guía. [video] es el nombre (sin extensión) de una animación corta guardada en res/raw. */
data class PasoGuia(val titulo: String, val texto: String, val video: String? = null)

data class Guia(
    val id: String,
    val titulo: String,
    val resumen: String,
    val ajuste: String?,          // clave de Ajustes (lista fija)
    val pasos: List<PasoGuia>
)

data class Sintoma(val id: String, val pregunta: String, val detalle: String, val guiaId: String?)

data class AlertaModelo(val titulo: String, val detalle: String, val nivel: Nivel)

/** Una marca conocida: sus nombres (normalizados) y los únicos paquetes que son la app oficial. */
data class Marca(val nombres: Set<String>, val paquetes: Set<String>)

/**
 * Lista local de amenazas (assets/datos/amenazas.json). La app no tiene Internet, así que esta lista
 * solo se actualiza al actualizar la app. Todo se valida al leer.
 */
data class ListaAmenazas(
    val paquetes: Map<String, String>,       // nombre de paquete -> nota
    val hashes: Map<String, String>,         // SHA-256 de un archivo APK (hex minúsculas) -> nota
    val certificados: Map<String, String>,   // SHA-256 de un certificado de firma -> nota
    val marcas: List<Marca>
)

/** Ficha de un modelo: nombre comercial, datos (etiqueta + valor) y alertas conocidas. */
data class FichaModelo(val nombre: String?, val datos: List<Pair<String, String>>, val alertas: List<AlertaModelo>)

/**
 * Lee los datos (guías, síntomas, alertas por modelo) desde la carpeta assets/datos (archivos .json).
 * Los archivos se tratan como DATOS, nunca como instrucciones: cada campo se revisa
 * (tipo, largo, valores permitidos) y lo que no calza se descarta.
 */
internal object Catalogo {

    private val ID = Regex("^[a-z0-9-]{1,40}$")
    private val VIDEO = Regex("^[a-z][a-z0-9_]{0,39}$")

    private var guiasCache: List<Guia>? = null
    private var sintomasCache: List<Sintoma>? = null
    private var amenazasCache: ListaAmenazas? = null

    private val PAQUETE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    private fun leer(ctx: Context, nombre: String, maxCaracteres: Int = 200_000): Any? = runCatching {
        val texto = ctx.assets.open("datos/$nombre").bufferedReader().use { it.readText() }
        if (texto.length > maxCaracteres) null else JSONTokener(texto).nextValue()
    }.getOrNull()

    private fun txt(o: JSONObject, clave: String, max: Int): String? {
        val v = o.opt(clave) as? String ?: return null
        val t = v.trim()
        return if (t.isEmpty() || t.length > max) null else t
    }

    private fun id(o: JSONObject, clave: String): String? = txt(o, clave, 40)?.takeIf { ID.matches(it) }

    private fun objetos(o: JSONObject, clave: String, max: Int): List<JSONObject> {
        val a: JSONArray = o.optJSONArray(clave) ?: return emptyList()
        return (0 until minOf(a.length(), max)).mapNotNull { a.optJSONObject(it) }
    }

    // ---------- Guías ----------

    fun guias(ctx: Context): List<Guia> {
        guiasCache?.let { return it }
        val raiz = leer(ctx, "guias.json") as? JSONObject
        val res = mutableListOf<Guia>()
        if (raiz != null) {
            for (g in objetos(raiz, "guias", 30)) {
                val gid = id(g, "id") ?: continue
                val titulo = txt(g, "titulo", 80) ?: continue
                val pasos = objetos(g, "pasos", 15).mapNotNull { p ->
                    val t = txt(p, "titulo", 80)
                    val x = txt(p, "texto", 600)
                    val v = txt(p, "video", 40)?.takeIf { VIDEO.matches(it) }
                    if (t != null && x != null) PasoGuia(t, x, v) else null
                }
                if (pasos.isEmpty()) continue
                val ajuste = txt(g, "ajuste", 30)?.takeIf { it in Ajustes.clavesValidas }
                res.add(Guia(gid, titulo, txt(g, "resumen", 160).orEmpty(), ajuste, pasos))
            }
        }
        guiasCache = res
        return res
    }

    // ---------- Síntomas ----------

    fun sintomas(ctx: Context): List<Sintoma> {
        sintomasCache?.let { return it }
        val raiz = leer(ctx, "triage.json") as? JSONObject
        val res = mutableListOf<Sintoma>()
        if (raiz != null) {
            for (s in objetos(raiz, "sintomas", 20)) {
                val sid = id(s, "id") ?: continue
                val pregunta = txt(s, "pregunta", 140) ?: continue
                val detalle = txt(s, "detalle", 400) ?: continue
                res.add(Sintoma(sid, pregunta, detalle, id(s, "guiaId")))
            }
        }
        sintomasCache = res
        return res
    }

    // ---------- Alertas por modelo ----------

    private fun alerta(e: JSONObject): AlertaModelo? {
        val t = txt(e, "titulo", 80) ?: return null
        val d = txt(e, "detalle", 400) ?: return null
        val nivel = when (e.opt("nivel")) {
            "peligro" -> Nivel.PELIGRO
            "atencion" -> Nivel.ATENCION
            "ok" -> Nivel.OK
            else -> return null
        }
        return AlertaModelo(t, d, nivel)
    }

    private fun norm(s: String): String = s.uppercase().filter { it.isLetterOrDigit() }

    /** Entradas de modelos.json cuyo prefijo coincide con el modelo (se ignoran espacios y guiones). */
    private fun coincidencias(raiz: JSONObject, modelo: String): List<JSONObject> {
        val m = norm(modelo)
        return objetos(raiz, "modelos", 200).filter { e ->
            val p = e.optJSONArray("prefijos") ?: return@filter false
            (0 until minOf(p.length(), 20)).any { i ->
                val pref = norm((p.opt(i) as? String).orEmpty()).take(40)
                pref.isNotEmpty() && m.startsWith(pref)
            }
        }
    }

    fun alertasPara(ctx: Context, info: InfoDispositivo): List<AlertaModelo> {
        val raiz = leer(ctx, "modelos.json") as? JSONObject ?: return emptyList()
        val res = mutableListOf<AlertaModelo>()

        for (g in objetos(raiz, "generales", 20)) {
            val sdkMax = g.opt("sdkMax")
            if (sdkMax is Int && info.sdk > sdkMax) continue
            alerta(g)?.let { res.add(it) }
        }
        for (m in coincidencias(raiz, info.modelo)) {
            for (e in objetos(m, "alertas", 10)) alerta(e)?.let { res.add(it) }
        }
        return res
    }

    /** Ficha del modelo escrito (o null si no hay datos verificados para él). */
    fun fichaPara(ctx: Context, modelo: String): FichaModelo? {
        val raiz = leer(ctx, "modelos.json") as? JSONObject ?: return null
        var nombre: String? = null
        val datos = mutableListOf<Pair<String, String>>()
        val alertas = mutableListOf<AlertaModelo>()
        for (m in coincidencias(raiz, modelo)) {
            nombre = nombre ?: txt(m, "nombre", 60)
            for (d in objetos(m, "ficha", 12)) {
                val etiqueta = txt(d, "etiqueta", 40)
                val valor = txt(d, "valor", 80)
                if (etiqueta != null && valor != null) datos.add(etiqueta to valor)
            }
            for (e in objetos(m, "alertas", 10)) alerta(e)?.let { alertas.add(it) }
        }
        return if (nombre == null && datos.isEmpty() && alertas.isEmpty()) null else FichaModelo(nombre, datos, alertas)
    }

    // ---------- Lista de amenazas ----------

    /** Nombre de marca sin tildes, mayúsculas ni símbolos, para comparar «WhatsApp» con «whatsapp ». */
    fun normalizarNombre(s: String): String =
        java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }
            .lowercase()

    private fun textos(o: JSONObject, clave: String, max: Int, largo: Int): List<String> {
        val a = o.optJSONArray(clave) ?: return emptyList()
        return (0 until minOf(a.length(), max)).mapNotNull { i ->
            (a.opt(i) as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= largo }
        }
    }

    fun amenazas(ctx: Context): ListaAmenazas {
        amenazasCache?.let { return it }
        val raiz = leer(ctx, "amenazas.json", 2_000_000) as? JSONObject
        val paquetes = HashMap<String, String>()
        val hashes = HashMap<String, String>()
        val certificados = HashMap<String, String>()
        val marcas = mutableListOf<Marca>()
        if (raiz != null) {
            for (e in objetos(raiz, "paquetes", 5000)) {
                val p = txt(e, "paquete", 255)?.takeIf { PAQUETE.matches(it) } ?: continue
                paquetes[p] = txt(e, "nota", 120) ?: "amenaza registrada"
            }
            for (e in objetos(raiz, "hashes", 20000)) {
                val h = txt(e, "sha256", 64)?.lowercase()?.takeIf { SHA256.matches(it) } ?: continue
                hashes[h] = txt(e, "nota", 120) ?: "amenaza registrada"
            }
            for (e in objetos(raiz, "certificados", 5000)) {
                val h = txt(e, "sha256", 64)?.lowercase()?.takeIf { SHA256.matches(it) } ?: continue
                certificados[h] = txt(e, "nota", 120) ?: "amenaza registrada"
            }
            for (e in objetos(raiz, "marcas", 100)) {
                val nombres = textos(e, "nombres", 6, 40).map { normalizarNombre(it) }.filter { it.isNotEmpty() }.toSet()
                val pqs = textos(e, "paquetes", 10, 255).filter { PAQUETE.matches(it) }.toSet()
                if (nombres.isNotEmpty() && pqs.isNotEmpty()) marcas.add(Marca(nombres, pqs))
            }
        }
        val lista = ListaAmenazas(paquetes, hashes, certificados, marcas)
        amenazasCache = lista
        return lista
    }
}
