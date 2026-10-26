package com.example.sanomovil001

import android.content.Context

/**
 * Lo único que la app guarda en el teléfono: el resultado resumido de la última revisión y el tamaño de letra.
 * Es privado de la app (MODE_PRIVATE). Al leerlo se revisa que los valores tengan sentido.
 */
internal object Almacen {
    private const val ARCHIVO = "sano_estado"
    private val GUIA = Regex("^[a-z0-9-]{1,40}$")
    private val ESCALAS = listOf(1f, 1.2f, 1.4f)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun guardar(ctx: Context, r: ResultadoRevision) {
        // Lo primero a resolver: el hallazgo más grave (a igual gravedad, el que más puntos resta).
        val top = r.hallazgos.filter { it.nivel != Nivel.OK }
            .maxWithOrNull(compareBy({ it.nivel.ordinal }, { it.penalizacion }))
        prefs(ctx).edit()
            .putInt("puntaje", r.puntaje)
            .putInt("nivel", r.nivel.ordinal)
            .putLong("fecha", System.currentTimeMillis())
            .putString("p_titulo", top?.titulo?.take(80))
            .putString("p_detalle", top?.detalle?.take(300))
            .putString("p_guia", top?.guiaId)
            .apply()
    }

    fun leer(ctx: Context): UltimaRevision? {
        val p = prefs(ctx)
        val fecha = p.getLong("fecha", 0L)
        val puntaje = p.getInt("puntaje", -1)
        val nivel = Nivel.values().getOrNull(p.getInt("nivel", -1))
        if (fecha <= 0L || fecha > System.currentTimeMillis() + 86_400_000L || puntaje !in 0..100 || nivel == null) return null

        val titulo = p.getString("p_titulo", null)?.takeIf { it.length in 1..80 }
        val detalle = p.getString("p_detalle", null)?.takeIf { it.length in 1..300 }
        val guia = p.getString("p_guia", null)?.takeIf { GUIA.matches(it) }
        val prioridad = if (titulo != null && detalle != null) Prioridad(titulo, detalle, guia) else null
        return UltimaRevision(puntaje, nivel, fecha, prioridad)
    }

    /** Guarda los ajustes de apariencia que la persona eligió (modo, contraste, movimiento, letra). */
    fun guardarAjustes(ctx: Context) {
        prefs(ctx).edit()
            .putBoolean("oscuro", Tema.oscuro)
            .putBoolean("alto_contraste", Tema.altoContraste)
            .putBoolean("reducir_movimiento", Tema.reducirMovimiento)
            .putFloat("escala", Tema.escala)
            .apply()
    }

    /** Aplica al tema lo que se guardó la vez anterior. */
    fun cargarAjustes(ctx: Context) {
        val p = prefs(ctx)
        Tema.oscuro = p.getBoolean("oscuro", false)
        Tema.altoContraste = p.getBoolean("alto_contraste", false)
        Tema.reducirMovimiento = p.getBoolean("reducir_movimiento", false)
        Tema.escala = leerEscala(ctx)
    }

    fun leerEscala(ctx: Context): Float {
        val v = prefs(ctx).getFloat("escala", 1f)
        return ESCALAS.firstOrNull { kotlin.math.abs(it - v) < 0.01f } ?: 1f
    }

    fun guardarEscala(ctx: Context, escala: Float) {
        prefs(ctx).edit().putFloat("escala", escala).apply()
    }
}
