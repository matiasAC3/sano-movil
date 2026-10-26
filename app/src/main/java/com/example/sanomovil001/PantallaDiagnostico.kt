package com.example.sanomovil001

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Las tres secciones de "Estado del equipo". */
enum class SubDiagnostico { BATERIA, ALMACENAMIENTO, SINTOMAS }

/**
 * Estado del equipo.
 *  sub == null -> menú; si no, la sección elegida.
 */
@Composable
fun PantallaDiagnostico(
    sub: SubDiagnostico?,
    onElegir: (SubDiagnostico) -> Unit,
    onGuia: (String) -> Unit,
    onArchivos: () -> Unit,
    onVolver: () -> Unit
) {
    when (sub) {
        null -> MenuDiagnostico(onElegir, onVolver)
        SubDiagnostico.BATERIA -> PantallaBateria(onGuia, onVolver)
        SubDiagnostico.ALMACENAMIENTO -> PantallaAlmacenamiento(onGuia, onArchivos, onVolver)
        SubDiagnostico.SINTOMAS -> PantallaSintomas(onGuia, onVolver)
    }
}

@Composable
private fun MenuDiagnostico(onElegir: (SubDiagnostico) -> Unit, onVolver: () -> Unit) {
    PantallaBase("Estado del equipo", onVolver) {
        ZonaScroll {
            Txt("Elige qué quieres revisar.", 15, Tema.TextoSuave)
            TarjetaAccion("Batería y energía", "Cómo está y cuánto podría durar", Tema.Acentos[0]) { onElegir(SubDiagnostico.BATERIA) }
            TarjetaAccion("Espacio y almacenamiento", "Cuánto espacio te queda", Tema.Acentos[1]) { onElegir(SubDiagnostico.ALMACENAMIENTO) }
            TarjetaAccion("¿Algo no funciona bien?", "Responde unas preguntas y te orientamos", Tema.Acentos[2]) { onElegir(SubDiagnostico.SINTOMAS) }
        }
    }
}

// ---------------------------------------------------------------- Batería

@Composable
private fun PantallaBateria(onGuia: (String) -> Unit, onVolver: () -> Unit) {
    val context = LocalContext.current
    var recarga by remember { mutableIntStateOf(0) }
    val bat = remember(recarga) { Dispositivo.bateria(context) }

    PantallaBase("Batería y energía", onVolver, textoVolver = "← Volver") {
        if (bat == null) {
            Txt("No pudimos leer la batería de tu equipo.", 17, Tema.TextoSuave)
        } else {
            val temp = bat.temperaturaC
            val (nivel, veredicto) = when {
                bat.tieneProblema -> Nivel.PELIGRO to
                    "Tu batería informa un problema (${bat.etiquetaSalud}). Si se repite, conviene llevar el equipo a revisión."
                temp != null && temp >= 40 -> Nivel.ATENCION to
                    "La batería está más caliente de lo normal. Déjala reposar, quita la funda y evita usarla mientras carga."
                else -> Nivel.OK to "Tu batería está funcionando con normalidad."
            }
            ZonaScroll {
                Row(verticalAlignment = Alignment.Top) {
                    PuntoNivel(nivel)
                    Spacer(Modifier.width(10.dp))
                    Txt(veredicto, 17, Tema.colorDe(nivel), negrita = true, modifier = Modifier.weight(1f))
                }
                Dato("Carga actual", if (bat.porcentaje < 0) "Sin datos" else "${bat.porcentaje} %" + if (bat.cargando) " (cargando)" else "")
                Dato("Temperatura", if (temp == null) "Sin datos" else "${temp.toInt()} °C")
                Dato("Estado de la batería", bat.etiquetaSalud)
                if (bat.ciclos > 0) Dato("Ciclos de carga", "${bat.ciclos}")
                bat.horasEstimadas?.let { Dato("Duración estimada", "unas ${it.toInt()} horas al ritmo de uso de ahora") }
                Txt(
                    "Con los años toda batería rinde menos, como un vaso que ya no se llena del todo. " +
                        "La duración estimada es aproximada y cambia según lo que estés haciendo.",
                    14, Tema.TextoSuave
                )
                BotonSano("Ver guía para cuidar la batería") { onGuia("cuidar-bateria") }
                BotonSano("Actualizar datos", color = Tema.BotonSecundario) { recarga++ }
            }
        }
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column(modifier = Modifier.tarjeta()) {
        Txt(etiqueta, 14, Tema.TextoSuave)
        Txt(valor, 18, negrita = true)
    }
}

// ---------------------------------------------------------- Almacenamiento

@Composable
private fun PantallaAlmacenamiento(onGuia: (String) -> Unit, onArchivos: () -> Unit, onVolver: () -> Unit) {
    val alm = remember { Dispositivo.almacenamiento() }

    PantallaBase("Espacio y almacenamiento", onVolver, textoVolver = "← Volver") {
        if (alm == null) {
            Txt("No pudimos leer el almacenamiento.", 17, Tema.TextoSuave)
        } else {
            val usadoPct = alm.usado.toFloat() / alm.total.toFloat()
            val nivel = when {
                usadoPct > 0.9f -> Nivel.PELIGRO
                usadoPct > 0.8f -> Nivel.ATENCION
                else -> Nivel.OK
            }
            // Equivalencias aproximadas: foto ≈ 3 MB, canción ≈ 4 MB, 1 minuto de vídeo ≈ 100 MB.
            val fotos = alm.libre / 3_000_000L
            val canciones = alm.libre / 4_000_000L
            val minutos = alm.libre / 100_000_000L

            ZonaScroll {
                Txt("Te quedan ${Archivos.tamano(alm.libre)} libres", 24, Tema.colorDe(nivel), negrita = true)
                Txt("de ${Archivos.tamano(alm.total)} en total", 15, Tema.TextoSuave)
                BarraProgreso(usadoPct, Tema.colorDe(nivel), 12.dp)
                Txt("${(usadoPct * 100).toInt()} % ocupado", 14, Tema.TextoSuave)
                Column(modifier = Modifier.tarjeta()) {
                    Txt(
                        "Con ese espacio libre aún caben unas $fotos fotos, o $canciones canciones, o $minutos minutos de vídeo. " +
                            "(Son valores aproximados.)",
                        16
                    )
                }
                BotonSano("Ordenar mis archivos", onClick = onArchivos)
                BotonSano("Ver guía para liberar espacio") { onGuia("liberar-espacio") }
            }
        }
    }
}

// ---------------------------------------------------------------- Síntomas

@Composable
private fun PantallaSintomas(onGuia: (String) -> Unit, onVolver: () -> Unit) {
    val context = LocalContext.current
    val lista = remember { Catalogo.sintomas(context) }
    var indice by remember { mutableIntStateOf(0) }
    var marcados by remember { mutableStateOf<List<Sintoma>>(emptyList()) }

    PantallaBase("¿Algo no funciona bien?", onVolver, textoVolver = "← Volver") {
        when {
            lista.isEmpty() -> Txt("No hay preguntas disponibles.", 17, Tema.TextoSuave)

            indice < lista.size -> {
                Txt("Pregunta ${indice + 1} de ${lista.size}", 14, Tema.TextoSuave)
                ZonaScroll {
                    Spacer(Modifier.height(24.dp))
                    Txt(lista[indice].pregunta, 24, negrita = true, centrado = true, modifier = Modifier.fillMaxWidth())
                }
                BotonSano("Sí") { marcados = marcados + lista[indice]; indice++ }
                BotonSano("No", color = Tema.BotonSecundario) { indice++ }
            }

            marcados.isEmpty() -> {
                ZonaScroll {
                    Spacer(Modifier.height(24.dp))
                    Txt(
                        "No marcaste ningún problema. Si algo cambia, puedes volver a hacer estas preguntas.",
                        18, Tema.Exito, negrita = true, centrado = true, modifier = Modifier.fillMaxWidth()
                    )
                }
                BotonSano("Empezar de nuevo") { indice = 0; marcados = emptyList() }
            }

            else -> {
                Txt("Esto es lo que te recomendamos:", 17, negrita = true)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(marcados) { s ->
                        Column(modifier = Modifier.tarjeta()) {
                            Txt(s.pregunta, 16, negrita = true)
                            Txt(s.detalle, 15, modifier = Modifier.padding(top = 4.dp))
                            if (s.guiaId != null) {
                                androidx.compose.material3.TextButton(onClick = { onGuia(s.guiaId) }) {
                                    Txt("Ver guía", 16, Tema.Boton, negrita = true)
                                }
                            }
                        }
                    }
                }
                BotonSano("Empezar de nuevo", color = Tema.BotonSecundario, compacto = true) { indice = 0; marcados = emptyList() }
            }
        }
    }
}
