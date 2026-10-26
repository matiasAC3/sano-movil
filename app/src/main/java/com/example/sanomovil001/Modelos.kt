package com.example.sanomovil001

/** Qué tan grave es algo que encontramos (semáforo). */
enum class Nivel { OK, ATENCION, PELIGRO }

/** Un resultado de la revisión: título, explicación, semáforo y puntos que resta al puntaje. */
data class Hallazgo(
    val titulo: String,
    val detalle: String,
    val nivel: Nivel,
    val penalizacion: Int = 0,
    val guiaId: String? = null   // guía que ayuda a resolverlo
)

/** Resultado completo de "Cuidar mi equipo hoy". */
data class ResultadoRevision(val hallazgos: List<Hallazgo>, val puntaje: Int, val nivel: Nivel)

/** Lo único que se recuerda entre sesiones: el puntaje y cuándo se hizo. */
/** Lo primero que conviene resolver de la última revisión. */
data class Prioridad(val titulo: String, val detalle: String, val guiaId: String?)

data class UltimaRevision(val puntaje: Int, val nivel: Nivel, val fecha: Long, val prioridad: Prioridad? = null)

/** Los permisos que mostramos. Para agregar uno nuevo, se agrega una línea aquí y su lógica en Permisos.kt. */
enum class TipoPermiso(
    val titulo: String,
    val icono: Icono,
    val acceso: String,       // frase: "Aplicaciones con acceso a ..."
    val uso: String,          // frase: "N apps pueden ..."
    val nota: String,         // consejo corto
    val instruccion: String   // qué tocar en los ajustes de Android
) {
    MICROFONO(
        "Micrófono", Icono.MICROFONO, "el micrófono", "usar tu micrófono",
        "Conviene dejarlo solo a las apps que de verdad lo necesitan.",
        "Toca «Permisos» y luego «Micrófono»."
    ),
    CAMARA(
        "Cámara", Icono.CAMARA, "la cámara", "usar tu cámara",
        "Conviene dejarla solo a las apps que de verdad la necesitan.",
        "Toca «Permisos» y luego «Cámara»."
    ),
    UBICACION(
        "Ubicación", Icono.UBICACION, "la ubicación", "ver tu ubicación",
        "«Siempre» quiere decir que la app puede saber dónde estás aunque no la estés usando.",
        "Toca «Permisos» y luego «Ubicación»."
    ),
    NOTIFICACIONES(
        "Notificaciones", Icono.CAMPANA, "las notificaciones", "enviarte notificaciones",
        "Puedes bloquear los avisos de las apps que te molestan.",
        "Toca «Notificaciones»."
    ),
    TELEFONO(
        "Teléfono", Icono.TELEFONO, "el teléfono", "ver o hacer llamadas",
        "Una app que hace llamadas por ti debería ser una en la que confíes.",
        "Toca «Permisos» y luego «Teléfono»."
    ),
    MENSAJES(
        "Mensajes", Icono.MENSAJES, "los mensajes", "leer o enviar mensajes de texto",
        "Leer tus mensajes es delicado: déjalo solo a apps de confianza.",
        "Toca «Permisos» y luego «SMS» o «Mensajes»."
    )
}

/** Cómo está un permiso en una app: texto y semáforo. */
data class EstadoPermiso(val texto: String, val nivel: Nivel)

data class InfoDispositivo(
    val marca: String,
    val modelo: String,
    val release: String,
    val sdk: Int,
    val parche: String
) {
    val nombre: String get() = "${marca.replaceFirstChar { it.uppercase() }} $modelo".trim()
}

data class BateriaInfo(
    val porcentaje: Int,          // -1 si no se sabe
    val temperaturaC: Double?,
    val salud: String,
    val cargando: Boolean,
    val ciclos: Int,              // -1 si no se sabe
    val horasEstimadas: Double?
) {
    val tieneProblema: Boolean get() = salud in setOf("sobrecalentada", "agotada", "sobrevoltaje", "falla")

    val etiquetaSalud: String
        get() = when (salud) {
            "buena" -> "buena"
            "sobrecalentada" -> "sobrecalentada"
            "agotada" -> "agotada"
            "sobrevoltaje" -> "voltaje excesivo"
            "falla" -> "falla no especificada"
            "fria" -> "demasiado fría"
            else -> "sin información"
        }
}

data class AlmacenamientoInfo(val total: Long, val libre: Long) {
    val usado: Long get() = total - libre
}

data class AppSimple(val nombre: String, val paquete: String)

data class RiesgosEquipo(val fueraDeTienda: List<AppSimple>, val accesibilidad: List<AppSimple>)
