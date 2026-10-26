package com.example.sanomovil001

import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Guías y ayuda. guiaId == null -> lista de guías; guiaId != null -> esa guía paso a paso.
 * (Reemplaza tu Pantalla4GuiaAyuda anterior.)
 */
@Composable
fun Pantalla4GuiaAyuda(
    guiaId: String?,
    onElegir: (String) -> Unit,
    onVolver: () -> Unit,
    onPestana: (Pestana) -> Unit
) {
    val context = LocalContext.current
    val guias = remember { Catalogo.guias(context) }
    val guia = guias.firstOrNull { it.id == guiaId }

    if (guia == null) {
        ListaGuias(guias, onElegir, onPestana)
    } else {
        GuiaPasoAPaso(guia, onVolver)
    }
}

@Composable
private fun ListaGuias(guias: List<Guia>, onElegir: (String) -> Unit, onPestana: (Pestana) -> Unit) {
    PantallaBase("Guías de ayuda", onVolver = {}, pestana = Pestana.AYUDA, onPestana = onPestana) {
        ZonaScroll {
            Txt("Paso a paso, con palabras sencillas.", 17, Tema.TextoSuave)
            if (guias.isEmpty()) {
                Txt("No hay guías disponibles.", 17, Tema.TextoSuave)
            } else {
                guias.forEachIndexed { i, g ->
                    TarjetaAccion(g.titulo, g.resumen, Tema.Acentos[i % 4]) { onElegir(g.id) }
                }
            }
        }
    }
}

/** Una pantalla por paso, con barras de avance arriba (estilo "historias"). */
@Composable
private fun GuiaPasoAPaso(guia: Guia, onVolver: () -> Unit) {
    val context = LocalContext.current
    var paso by remember(guia.id) { mutableIntStateOf(0) }
    val n = guia.pasos.size
    val ultimo = paso == n - 1

    PantallaBase(guia.titulo, onVolver, textoVolver = "← Salir de la guía") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until n) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(if (i <= paso) Tema.Boton else Tema.TextoSuave.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                )
            }
        }

        ZonaScroll {
            Spacer(Modifier.height(12.dp))
            Txt("Paso ${paso + 1} de $n", 14, Tema.TextoSuave)
            Txt(guia.pasos[paso].titulo, 24, negrita = true)
            Txt(guia.pasos[paso].texto, 19, modifier = Modifier.padding(top = 4.dp))
            // Cada paso puede traer una animación corta; "key" hace que al cambiar de paso se cargue la nueva.
            guia.pasos[paso].video?.let { key(it) { VideoPaso(it) } }
        }

        if (ultimo && guia.ajuste != null) {
            BotonSano("Abrir ajustes", onClick = { Ajustes.abrir(context, guia.ajuste) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonSano(
                "Anterior", Modifier.weight(1f), color = Tema.BotonSecundario, compacto = true,
                activo = paso > 0, onClick = { paso-- }
            )
            BotonSano(
                if (ultimo) "Terminar" else "Siguiente", Modifier.weight(1f), compacto = true,
                onClick = { if (ultimo) onVolver() else paso++ }
            )
        }
    }
}

/**
 * Animación corta de un paso (video en bucle, sin sonido). Se toca para pausar o reanudar.
 * Los videos viven en res/raw y solo se pueden pedir por nombre válido: no se abren direcciones de Internet.
 * Si la persona activó "Reducir movimiento", empieza en pausa.
 */
@Composable
private fun VideoPaso(nombre: String) {
    val ctx = LocalContext.current
    val recurso = remember(nombre) { ctx.resources.getIdentifier(nombre, "raw", ctx.packageName) }
    if (recurso == 0) return

    var reproduciendo by remember(nombre) { mutableStateOf(!Tema.reducirMovimiento) }
    val forma = RoundedCornerShape(24.dp)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .height(320.dp)
                .aspectRatio(9f / 16f)
                .clip(forma)
                .border(1.dp, Tema.Borde, forma)
                .background(Tema.Tarjeta)
                .clickable(onClickLabel = if (reproduciendo) "Pausar la animación" else "Ver la animación") { reproduciendo = !reproduciendo }
                .semantics { contentDescription = "Animación que muestra este paso" }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    VideoView(c).apply {
                        setVideoURI(Uri.parse("android.resource://${c.packageName}/$recurso"))
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            seekTo(1)
                            if (reproduciendo) start()
                        }
                    }
                },
                update = { v -> if (reproduciendo) v.start() else if (v.isPlaying) v.pause() },
                onRelease = { it.stopPlayback() }
            )
            if (!reproduciendo) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Txt("▶", 30, Color.White, tamMin = 10) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Txt("Ilustración: tu teléfono puede verse un poco distinto. Toca para pausar.", 14, Tema.TextoSuave, centrado = true, tamMin = 14)
    }
}
