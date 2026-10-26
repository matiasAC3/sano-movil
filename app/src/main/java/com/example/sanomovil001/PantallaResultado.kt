package com.example.sanomovil001

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Resultado: primero cómo está todo, luego "Lo primero", luego lo que se puede mirar con calma,
 * y al final, en una sola tarjeta, lo que ya está en orden (para no llenar la pantalla).
 */
@Composable
fun PantallaResultado(revision: ResultadoRevision, onGuia: (String) -> Unit, onVolver: () -> Unit) {
    val context = LocalContext.current
    val color = Tema.colorDe(revision.nivel)
    val pendientes = revision.hallazgos
        .filter { it.nivel != Nivel.OK }
        .sortedWith(compareByDescending<Hallazgo> { it.nivel.ordinal }.thenByDescending { it.penalizacion })
    val enOrden = revision.hallazgos.filter { it.nivel == Nivel.OK }

    PantallaBase("Resultado", onVolver, textoVolver = "← Volver al inicio") {
        ZonaScroll {
            Row(modifier = Modifier.tarjeta(), verticalAlignment = Alignment.CenterVertically) {
                SelloEstado(revision.nivel, 80.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Txt(Escaner.etiquetaDe(revision.nivel), 24, color, negrita = true)
                    Txt("Puntaje: ${revision.puntaje} de 100", 16, Tema.TextoSuave)
                }
            }

            if (pendientes.isEmpty()) {
                Column(modifier = Modifier.tarjeta(fondo = Tema.fondoDe(Nivel.OK))) {
                    Txt("No hay nada urgente. Puedes estar tranquilo.", 20, Tema.Exito, negrita = true)
                }
            } else {
                Txt("Lo primero", 22, negrita = true)
                TarjetaHallazgo(pendientes.first(), destacado = true, onGuia = onGuia)

                if (pendientes.size > 1) {
                    Txt("Para mirar con calma", 22, negrita = true)
                    pendientes.drop(1).forEach { TarjetaHallazgo(it, destacado = false, onGuia = onGuia) }
                }
            }

            if (enOrden.isNotEmpty()) {
                Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("Todo lo demás está en orden", 18, Tema.Exito, negrita = true)
                    Txt(enOrden.joinToString(" · ") { it.titulo }, 16, Tema.TextoSuave)
                }
            }

            Txt(
                "La revisión mira Android, el parche de seguridad, la batería, el espacio y los permisos. " +
                    "No puede saber si hay una actualización esperándote: para eso usa el botón.",
                15, Tema.TextoSuave
            )
            BotonSano("Buscar actualizaciones", color = Tema.BotonSecundario) {
                Ajustes.abrir(context, "actualizaciones")
            }
        }
    }
}

@Composable
private fun TarjetaHallazgo(h: Hallazgo, destacado: Boolean, onGuia: (String) -> Unit) {
    Row(
        modifier = Modifier.tarjeta(fondo = Tema.fondoDe(h.nivel)),
        verticalAlignment = Alignment.Top
    ) {
        PuntoNivel(h.nivel, 30.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(h.titulo, 19, negrita = true)
            Txt(h.detalle, 17)
            if (h.guiaId != null) {
                BotonSano(
                    "Ver cómo resolverlo",
                    color = if (destacado) Tema.Boton else Tema.BotonSecundario,
                    compacto = !destacado
                ) { onGuia(h.guiaId) }
            }
        }
    }
}
