package com.example.sanomovil001

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Texto con el estilo de la app (así no se repiten 6 parámetros en cada Text). Respeta el tamaño de letra elegido. */
@Composable
fun Txt(
    texto: String,
    tam: Int,
    color: Color = Tema.Texto,
    negrita: Boolean = false,
    modifier: Modifier = Modifier,
    centrado: Boolean = false,
    maxLineas: Int = Int.MAX_VALUE,
    espaciado: Float = 0f,
    tamMin: Int = 15
) {
    Text(
        text = texto,
        modifier = modifier,
        color = color,
        fontSize = ((if (tam < tamMin) tamMin else tam) * Tema.escala).sp,
        fontWeight = if (negrita) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = if (centrado) TextAlign.Center else null,
        letterSpacing = if (espaciado > 0f) espaciado.sp else TextUnit.Unspecified,
        maxLines = maxLineas,
        overflow = TextOverflow.Ellipsis
    )
}

/** Botón grande de la app (con subtítulo opcional). "compacto" es para filas de varios botones. */
@Composable
fun BotonSano(
    texto: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    color: Color = Tema.Boton,
    compacto: Boolean = false,
    activo: Boolean = true,
    onClick: () -> Unit
) {
    // El botón "secundario" es suave, con letra de color; los demás son de color con letra clara.
    val colorTexto = if (color == Tema.BotonSecundario) Tema.Boton else Tema.SobrePrimario
    Button(
        onClick = onClick,
        enabled = activo,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compacto) 52.dp else 64.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = colorTexto),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compacto) 8.dp else 14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(texto, if (compacto) 16 else 20, colorTexto, negrita = true, centrado = true)
            if (subtitulo != null) Txt(subtitulo, 16, colorTexto.copy(alpha = 0.9f), centrado = true)
        }
    }
}

/** Botón cuadrado con un ícono (modo oscuro, ajustes...). Siempre lleva una descripción para lectores de pantalla. */
@Composable
fun BotonIcono(icono: Icono, descripcion: String, fondo: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fondo)
            .clickable(onClickLabel = descripcion, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) {
        IconoSano(icono, 24.dp, Tema.Texto)
    }
}

/** Cuadro redondeado con un ícono dentro (para las tarjetas). */
@Composable
fun CuadroIcono(icono: Icono, fondo: Color, color: Color = Tema.Boton, tam: Dp = 48.dp) {
    Box(
        modifier = Modifier.size(tam).background(fondo, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        IconoSano(icono, tam * 0.5f, color)
    }
}

/** Ventana emergente con los colores de la app (se usa igual que AlertDialog). */
@Composable
fun DialogoSano(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(28.dp),
        containerColor = Tema.Tarjeta,
        titleContentColor = Tema.Texto,
        textContentColor = Tema.Texto
    )
}

/** Ventanita con un mensaje y un botón "Aceptar". */
@Composable
fun DialogoMensaje(texto: String, titulo: String = "Sano Móvil", onCerrar: () -> Unit) {
    DialogoSano(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Aceptar") } }
    )
}

/**
 * Tarjeta de una lista: fondo blanco, esquinas muy redondeadas, borde fino y sombra suave.
 * Si se pasa [onClick], toda la tarjeta se puede tocar. [fondo] sirve para resaltar un aviso.
 */
fun Modifier.tarjeta(onClick: (() -> Unit)? = null, fondo: Color? = null): Modifier {
    val forma = RoundedCornerShape(24.dp)
    return this
        .fillMaxWidth()
        .shadow(1.dp, forma)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .background(fondo ?: Tema.Tarjeta)
        .border(1.dp, Tema.Borde, forma)
        .padding(16.dp)
}

/** Tarjeta de menú: barra de color a la izquierda, título, explicación y flecha. */
@Composable
fun TarjetaAccion(titulo: String, subtitulo: String, acento: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.tarjeta(onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(6.dp)
                .height(44.dp)
                .background(acento, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Txt(titulo, 20, negrita = true)
            Txt(subtitulo, 16, Tema.TextoSuave)
        }
        Txt("›", 30, Tema.TextoSuave)
    }
}

/** Semáforo: círculo de color con un símbolo (así no depende solo del color). */
@Composable
fun PuntoNivel(nivel: Nivel, tam: Dp = 24.dp) {
    val (simbolo, descripcion) = when (nivel) {
        Nivel.OK -> "✓" to "Correcto"
        Nivel.ATENCION -> "!" to "Atención"
        Nivel.PELIGRO -> "✕" to "Importante"
    }
    Box(
        modifier = Modifier
            .size(tam)
            .background(Tema.colorDe(nivel), CircleShape)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) {
        Txt(simbolo, (tam.value * 0.6f).toInt(), Tema.SobrePrimario, negrita = true, tamMin = 10)
    }
}

/**
 * Sello de estado: una forma grande con símbolo y color. La forma también cambia (círculo / cuadrado redondeado)
 * para que se entienda aunque no se distingan los colores.
 */
@Composable
fun SelloEstado(nivel: Nivel?, tam: Dp = 104.dp) {
    val color = if (nivel == null) Tema.TextoSuave else Tema.colorDe(nivel)
    val fondo = if (nivel == null) Tema.Tonal else Tema.fondoDe(nivel)
    val forma = if (nivel == Nivel.PELIGRO) RoundedCornerShape(28.dp) else CircleShape
    val simbolo = when (nivel) {
        Nivel.OK -> "✓"
        Nivel.ATENCION, Nivel.PELIGRO -> "!"
        null -> "…"
    }
    val descripcion = when (nivel) {
        Nivel.OK -> "Estado: todo en orden"
        Nivel.ATENCION -> "Estado: conviene revisar"
        Nivel.PELIGRO -> "Estado: hay algo importante"
        null -> "Estado: aún sin revisar"
    }
    Box(
        modifier = Modifier
            .size(tam)
            .background(fondo, forma)
            .border(4.dp, color, forma)
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center
    ) {
        Txt(simbolo, (tam.value * 0.5f).toInt(), color, negrita = true, tamMin = 10)
    }
}

/** Barra de progreso simple (fracción de 0 a 1). */
@Composable
fun BarraProgreso(fraccion: Float, color: Color, alto: Dp = 8.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(alto)
            .background(Tema.Borde, RoundedCornerShape(alto))
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraccion.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color, RoundedCornerShape(alto))
        )
    }
}

@Composable
fun Cargando(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Tema.Boton)
    }
}

/** Zona que se desplaza si el contenido no cabe (importante con letra grande). */
@Composable
fun ColumnScope.ZonaScroll(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = contenido
    )
}

/** Las cuatro secciones principales de la app (barra de abajo). */
enum class Pestana(val titulo: String, val icono: Icono) {
    INICIO("Inicio", Icono.CASA),
    EQUIPOS("Mis equipos", Icono.EQUIPOS),
    GESTIONAR("Gestionar", Icono.CARPETA),
    AYUDA("Guías de ayuda", Icono.AYUDA)
}

/** Barra de navegación inferior flotante: ícono + palabra, siempre a la vista. La sección actual se destaca. */
@Composable
fun BarraInferior(actual: Pestana, onElegir: (Pestana) -> Unit) {
    val forma = RoundedCornerShape(32.dp)
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, forma)
                .background(Tema.Tarjeta)
                .border(1.dp, Tema.Borde, forma)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Pestana.values().forEach { p ->
                val seleccionada = p == actual
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (seleccionada) Tema.Tonal else Color.Transparent)
                        .clickable(role = Role.Tab) { onElegir(p) }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(if (seleccionada) Tema.Boton else Color.Transparent, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        IconoSano(p.icono, 26.dp, if (seleccionada) Tema.SobrePrimario else Tema.TextoSuave)
                    }
                    Txt(
                        p.titulo, 14, if (seleccionada) Tema.Texto else Tema.TextoSuave,
                        negrita = seleccionada, centrado = true, maxLineas = 2, tamMin = 13
                    )
                }
            }
        }
    }
}

/**
 * Esqueleto común: título grande arriba y contenido al centro.
 * Si recibe [pestana], muestra la barra de abajo (pantallas principales); si no, el botón "Volver".
 */
@Composable
fun PantallaBase(
    titulo: String,
    onVolver: () -> Unit,
    textoVolver: String = "← Volver",
    pestana: Pestana? = null,
    onPestana: (Pestana) -> Unit = {},
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tema.Fondo)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Txt(
                titulo, 26, negrita = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp)
                    .semantics { heading() }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = contenido
            )
            if (pestana == null) {
                Spacer(Modifier.height(10.dp))
                BotonSano(textoVolver, color = Tema.BotonSecundario, compacto = true, onClick = onVolver)
            }
        }
        if (pestana != null) BarraInferior(pestana, onPestana)
    }
}
