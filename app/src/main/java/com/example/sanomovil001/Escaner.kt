package com.example.sanomovil001

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Revisión REAL del equipo. Las reglas son simples y están arriba para que sea fácil ajustarlas.
 * Cada hallazgo puede restar puntos; el puntaje final va de 0 a 100.
 */
internal object Escaner {

    // Versión de Android (API): 35 = Android 15, 33 = Android 13. Revisar una vez al año.
    private const val API_RECIENTE = 35
    private const val API_ACEPTABLE = 33

    // Antigüedad del parche de seguridad (en días)
    private const val DIAS_ATENCION = 90L
    private const val DIAS_PELIGRO = 180L

    val pasos = listOf(
        "Revisando la versión de Android…",
        "Revisando las actualizaciones de seguridad…",
        "Revisando la batería…",
        "Revisando el espacio disponible…",
        "Revisando los permisos de tus apps…",
        "Buscando señales de apps peligrosas…",
        "Revisando alertas para tu modelo…"
    )

    fun etiquetaDe(nivel: Nivel): String = when (nivel) {
        Nivel.OK -> "Todo en orden"
        Nivel.ATENCION -> "Conviene revisar"
        Nivel.PELIGRO -> "Hay algo importante"
    }

    /** Corre todas las revisiones. [alPaso] avisa en qué paso va (se llama en el hilo de la pantalla). */
    suspend fun revisar(ctx: Context, alPaso: (Int) -> Unit): ResultadoRevision {
        val info = Dispositivo.info()
        val h = mutableListOf<Hallazgo>()

        // Pausa corta solo para que la persona alcance a leer qué se está revisando.
        suspend fun paso(i: Int) {
            alPaso(i)
            delay(500)
        }

        paso(0); h.add(version(info))
        paso(1); h.add(parche(info))
        paso(2); Dispositivo.bateria(ctx)?.let { h.add(bateria(it)) }
        paso(3); Dispositivo.almacenamiento()?.let { h.add(almacenamiento(it)) }

        paso(4)
        val apps = withContext(Dispatchers.IO) { Permisos.cargar(ctx) }
        val riesgos = withContext(Dispatchers.IO) { Riesgos.analizar(ctx) }
        h.addAll(privacidad(apps, riesgos))

        paso(5)
        val informe = withContext(Dispatchers.IO) { Amenazas.analizar(ctx) }
        h.addAll(amenazas(informe))

        paso(6)
        val alertas = withContext(Dispatchers.IO) { Catalogo.alertasPara(ctx, info) }
        for (a in alertas) {
            val pen = when (a.nivel) { Nivel.PELIGRO -> 10; Nivel.ATENCION -> 5; Nivel.OK -> 0 }
            h.add(Hallazgo(a.titulo, a.detalle, a.nivel, pen))
        }
        return resumir(h)
    }

    private fun resumir(h: List<Hallazgo>): ResultadoRevision {
        var puntaje = 100 - h.sumOf { it.penalizacion }
        val peor = h.maxOfOrNull { it.nivel.ordinal } ?: 0
        // El puntaje nunca contradice al semáforo: un problema grave no puede dar "buen estado".
        if (peor == Nivel.PELIGRO.ordinal) puntaje = minOf(puntaje, 59)
        else if (peor == Nivel.ATENCION.ordinal) puntaje = minOf(puntaje, 89)
        puntaje = puntaje.coerceIn(0, 100)
        val nivel = when {
            puntaje >= 90 -> Nivel.OK
            puntaje >= 60 -> Nivel.ATENCION
            else -> Nivel.PELIGRO
        }
        return ResultadoRevision(h.map { it.copy(guiaId = it.guiaId ?: guiaPara(it.titulo)) }, puntaje, nivel)
    }

    /** Qué guía ayuda a resolver cada hallazgo. */
    private fun guiaPara(titulo: String): String? = when (titulo) {
        "Versión de Android", "Actualizaciones de seguridad" -> "actualizar-sistema"
        "Batería" -> "cuidar-bateria"
        "Espacio de almacenamiento" -> "liberar-espacio"
        "Ubicación de tus apps" -> "revisar-camara-microfono"
        "Servicios de accesibilidad" -> "servicios-accesibilidad"
        Amenazas.T_APPS -> "app-sospechosa"
        else -> null
    }

    // ---------- Reglas ----------

    private fun version(info: InfoDispositivo): Hallazgo {
        val titulo = "Versión de Android"
        val v = "Android ${info.release}"
        return when {
            info.sdk <= 0 -> Hallazgo(titulo, "No pudimos saber qué versión de Android tiene tu equipo.", Nivel.ATENCION, 5)
            info.sdk >= API_RECIENTE -> Hallazgo(titulo, "Tienes $v. Es una versión reciente.", Nivel.OK)
            info.sdk >= API_ACEPTABLE -> Hallazgo(
                titulo, "Tienes $v. Ya no es la más nueva; conviene revisar si hay una actualización disponible.",
                Nivel.ATENCION, 12
            )
            else -> Hallazgo(
                titulo, "Tienes $v. Es una versión antigua y es probable que ya no reciba todas las actualizaciones de seguridad.",
                Nivel.PELIGRO, 30
            )
        }
    }

    private fun parche(info: InfoDispositivo): Hallazgo {
        val titulo = "Actualizaciones de seguridad"
        val fecha: LocalDate? = runCatching { LocalDate.parse(info.parche) }.getOrNull()
        if (fecha == null) {
            return Hallazgo(titulo, "No pudimos saber cuándo fue la última actualización de seguridad de tu equipo.", Nivel.ATENCION, 5)
        }
        val dias = ChronoUnit.DAYS.between(fecha, LocalDate.now())
        val bonita = fecha.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")))
        val hace = if (dias < 45) "hace ${maxOf(dias, 0L)} días" else "hace ${dias / 30} meses"
        val base = "Tu última actualización de seguridad es del $bonita ($hace)."
        return when {
            dias <= DIAS_ATENCION -> Hallazgo(titulo, "$base Está al día.", Nivel.OK)
            dias <= DIAS_PELIGRO -> Hallazgo(titulo, "$base Ya pasó un tiempo: conviene buscar una actualización.", Nivel.ATENCION, 12)
            else -> Hallazgo(titulo, "$base Lleva mucho tiempo sin actualizaciones de seguridad y podría estar expuesto.", Nivel.PELIGRO, 30)
        }
    }

    private fun bateria(b: BateriaInfo): Hallazgo {
        val titulo = "Batería"
        val temp = b.temperaturaC
        return when {
            b.tieneProblema -> Hallazgo(
                titulo, "La batería informa un problema (${b.etiquetaSalud}). Si se repite, conviene llevar el equipo a revisión.",
                Nivel.PELIGRO, 15
            )
            temp != null && temp >= 40 -> Hallazgo(
                titulo, "La batería está más caliente de lo normal (${temp.toInt()} °C). Déjala reposar y evita usarla mientras carga.",
                Nivel.ATENCION, 8
            )
            else -> Hallazgo(titulo, "Tu batería está funcionando con normalidad.", Nivel.OK)
        }
    }

    private fun almacenamiento(a: AlmacenamientoInfo): Hallazgo {
        val titulo = "Espacio de almacenamiento"
        val pctLibre = a.libre * 100.0 / a.total
        val base = "Te quedan ${Archivos.tamano(a.libre)} libres de ${Archivos.tamano(a.total)}."
        return when {
            pctLibre < 10 -> Hallazgo(titulo, "$base Casi no queda espacio: conviene liberar.", Nivel.PELIGRO, 15)
            pctLibre < 20 -> Hallazgo(titulo, "$base Empieza a quedar poco espacio.", Nivel.ATENCION, 7)
            else -> Hallazgo(titulo, "$base Tienes espacio de sobra.", Nivel.OK)
        }
    }

    /** Convierte el informe de amenazas en hallazgos del puntaje. */
    private fun amenazas(inf: InformeAmenazas): List<Hallazgo> {
        val h = mutableListOf<Hallazgo>()
        val graves = inf.graves
        val dudosas = inf.dudosas
        fun nombres(nivel: Nivel): String =
            inf.apps.filter { it.nivel == nivel }.take(3).joinToString(", ") { "«${it.nombre}»" }

        when {
            graves > 0 -> h.add(
                Hallazgo(
                    Amenazas.T_APPS,
                    (if (graves == 1) "1 app tiene señales claras de riesgo: " else "$graves apps tienen señales claras de riesgo: ") +
                        nombres(Nivel.PELIGRO) + ". Conviene revisarla ahora; no es una prueba de virus, pero sí merece atención.",
                    Nivel.PELIGRO, minOf(25 + (graves - 1) * 8, 40)
                )
            )
            dudosas > 0 -> h.add(
                Hallazgo(
                    Amenazas.T_APPS,
                    (if (dudosas == 1) "1 app tiene señales que conviene mirar: " else "$dudosas apps tienen señales que conviene mirar: ") +
                        nombres(Nivel.ATENCION) + ". Mira por qué en Seguridad (tarjeta del Inicio).",
                    Nivel.ATENCION, minOf(dudosas * 4, 12)
                )
            )
            else -> h.add(
                Hallazgo(
                    Amenazas.T_APPS,
                    "No encontramos señales de apps peligrosas entre las ${inf.revisadas} que revisamos. Esto no es una garantía total.",
                    Nivel.OK
                )
            )
        }
        for (e in inf.equipo) h.add(Hallazgo(e.titulo, e.detalle, e.nivel, e.penalizacion, e.guiaId))
        return h
    }

    private fun privacidad(apps: List<AppConPermisos>, riesgos: RiesgosEquipo): List<Hallazgo> {
        val h = mutableListOf<Hallazgo>()

        val siempre = apps.count { it.estados[TipoPermiso.UBICACION]?.nivel == Nivel.ATENCION }
        if (siempre > 0) {
            h.add(
                Hallazgo(
                    "Ubicación de tus apps",
                    if (siempre == 1) "1 app puede ver tu ubicación aunque no la estés usando."
                    else "$siempre apps pueden ver tu ubicación aunque no las estés usando.",
                    Nivel.ATENCION, minOf(siempre * 3, 12)
                )
            )
        } else {
            h.add(Hallazgo("Ubicación de tus apps", "Ninguna app puede ver tu ubicación cuando no la usas.", Nivel.OK))
        }

        val cam = apps.count { TipoPermiso.CAMARA in it.estados }
        val mic = apps.count { TipoPermiso.MICROFONO in it.estados }
        h.add(
            Hallazgo(
                "Cámara y micrófono",
                "$cam apps pueden usar tu cámara y $mic tu micrófono. Revisa que sean las que de verdad lo necesitan.",
                Nivel.OK
            )
        )

        val f = riesgos.fueraDeTienda.size
        if (f > 0) {
            h.add(
                Hallazgo(
                    "Origen de tus apps",
                    (if (f == 1) "1 app se instaló fuera de una tienda conocida" else "$f apps se instalaron fuera de una tienda conocida") +
                        ". No es necesariamente un problema, pero vale la pena revisarlas.",
                    Nivel.ATENCION, minOf(f * 2, 8)
                )
            )
        }

        val a = riesgos.accesibilidad.size
        if (a > 0) {
            h.add(
                Hallazgo(
                    "Servicios de accesibilidad",
                    (if (a == 1) "1 app tiene un servicio de accesibilidad activo" else "$a apps tienen servicios de accesibilidad activos") +
                        ". Estos servicios pueden leer y controlar la pantalla; confirma que los reconoces.",
                    Nivel.ATENCION, minOf(a * 4, 8)
                )
            )
        }
        return h
    }
}
