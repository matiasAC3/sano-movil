package com.example.sanomovil001

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inicio. De arriba hacia abajo:
 *  1) Cabecera: marca, saludo y tres botones (modo oscuro, ajustes, foto de perfil).
 *  2) Nombre del equipo y modelo.
 *  3) Tarjetas: teléfono con oleaje (toca para ver el estado) y "Escanear" + "Última revisión".
 *  4) "Lo primero": solo si hay algo que resolver.
 *  5) Permisos y Seguridad. Permisos se abre aquí mismo, sin cambiar de pantalla.
 *  6) Barra de abajo con las 4 secciones.
 */
@Composable
fun Pantalla1Principal(
    ultima: UltimaRevision?,
    nombre: String,
    reanudado: Int,
    onRevisar: () -> Unit,
    onEstado: () -> Unit,
    onSeguridad: () -> Unit,
    onAjustes: () -> Unit,
    onPerfil: () -> Unit,
    onGuia: (String) -> Unit,
    onPestana: (Pestana) -> Unit
) {
    val info = remember { Dispositivo.info() }
    val densidad = LocalDensity.current
    var detalleAbierto by rememberSaveable { mutableStateOf(false) }
    var permisosAbiertos by rememberSaveable { mutableStateOf(false) }
    var filaAlto by remember { mutableIntStateOf(0) }

    val alto = (250 * Tema.escala).dp
    val hueco = with(densidad) { 12.dp.toPx() }
    val altoPx = with(densidad) { alto.toPx() }
    val r24 = with(densidad) { 24.dp.toPx() }
    val r12 = with(densidad) { 12.dp.toPx() }

    // Un solo recuadro "abierto": la tarjeta tocada y lo que se despliega debajo forman una sola pieza.
    val formaDetalle = remember(altoPx, hueco) { FormaPestana(LadoPestana.DERECHA, hueco, (altoPx + hueco) / 2f, altoPx, r24, r12) }
    val formaPermisos = remember(filaAlto, hueco) { FormaPestana(LadoPestana.IZQUIERDA, hueco, 0f, filaAlto.toFloat(), r24, r12) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tema.Fondo)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Cabecera(nombre, onAjustes, onPerfil)

            Column {
                Txt("ESTADO DEL EQUIPO", 13, Tema.TextoSuave, negrita = true, espaciado = 1.2f, tamMin = 13)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Txt("Mi Android", 24, negrita = true, modifier = Modifier.weight(1f))
                    Txt(info.modelo, 14, Tema.TextoSuave, maxLineas = 1, tamMin = 13)
                }
            }

            // ---- Teléfono, Escanear y Última revisión. Al tocar "Última revisión" se despliega "Lo primero" debajo.
            Box(modifier = Modifier.fillMaxWidth().then(if (detalleAbierto) Modifier.desplegado(formaDetalle) else Modifier)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TarjetaTelefono(ultima, onEstado, Modifier.weight(1f).height(alto))
                        Column(modifier = Modifier.weight(1f).height(alto), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TarjetaEscanear(onRevisar, Modifier.weight(1f))
                            TarjetaUltima(ultima, detalleAbierto, Modifier.weight(1f)) { detalleAbierto = !detalleAbierto }
                        }
                    }
                    if (detalleAbierto) DetalleUltima(ultima, onRevisar, onGuia, onSeguridad)
                }
            }

            // ---- Permisos (se abre aquí mismo) y Seguridad
            Box(modifier = Modifier.fillMaxWidth().then(if (permisosAbiertos) Modifier.desplegado(formaPermisos) else Modifier)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .onSizeChanged { filaAlto = it.height },
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TarjetaCorta(
                            Icono.PORTAPAPELES, Tema.Tonal, "Permisos",
                            if (ultima == null) "Sin revisar" else "Revisados",
                            if (permisosAbiertos) "▴" else "▾",
                            conTarjeta = !permisosAbiertos,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) { permisosAbiertos = !permisosAbiertos }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(bottom = if (permisosAbiertos) 10.dp else 0.dp)) {
                            TarjetaCorta(
                                Icono.CANDADO, Tema.TintaAzul, "Seguridad",
                                when (ultima?.nivel) {
                                    null -> "Sin revisar"
                                    Nivel.OK -> "Protegido"
                                    Nivel.ATENCION -> "Conviene revisar"
                                    Nivel.PELIGRO -> "Hay algo importante"
                                },
                                "›",
                                conTarjeta = true,
                                modifier = Modifier.fillMaxSize(),
                                onClick = onSeguridad
                            )
                        }
                    }
                    if (permisosAbiertos) PanelPermisos(reanudado, conTarjeta = false)
                }
            }
        }
        BarraInferior(Pestana.INICIO, onPestana)
    }
}

/** Lo que se despliega bajo "Última revisión": lo primero que conviene resolver, con su botón. */
@Composable
private fun DetalleUltima(ultima: UltimaRevision?, onRevisar: () -> Unit, onGuia: (String) -> Unit, onSeguridad: () -> Unit) {
    val p = ultima?.prioridad
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            ultima == null -> {
                Txt("Aún no has revisado tu equipo", 20, negrita = true)
                Txt("Cuando quieras, empezamos con calma. Toma menos de un minuto.", 17, Tema.TextoSuave)
                BotonSano("Revisar mi equipo", onClick = onRevisar)
            }
            p != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PuntoNivel(ultima.nivel, 28.dp)
                    Txt("Lo primero", 16, Tema.TextoSuave, negrita = true)
                }
                Txt(p.titulo, 22, negrita = true)
                Txt(p.detalle, 18)
                BotonSano("Ver cómo resolverlo") {
                    if (p.guiaId != null) onGuia(p.guiaId) else onSeguridad()
                }
                BotonSano("Revisar de nuevo", color = Tema.BotonSecundario, compacto = true, onClick = onRevisar)
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PuntoNivel(Nivel.OK, 28.dp)
                    Txt("Todo en orden", 20, Tema.Exito, negrita = true)
                }
                Txt("No hay nada urgente. Puedes estar tranquilo.", 18)
                BotonSano("Revisar de nuevo", color = Tema.BotonSecundario, compacto = true, onClick = onRevisar)
            }
        }
    }
}

@Composable
private fun Cabecera(nombre: String, onAjustes: () -> Unit, onPerfil: () -> Unit) {
    val ctx = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("SanoMovil", 14, Tema.TextoSuave, negrita = true, tamMin = 13)
                IconoSano(Icono.HOJA, 16.dp, Tema.Boton)
            }
            Txt(if (nombre.isBlank()) "Hola" else "Hola, $nombre", 28, negrita = true, maxLineas = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonIcono(
                if (Tema.oscuro) Icono.SOL else Icono.LUNA,
                if (Tema.oscuro) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                Tema.TintaLila
            ) {
                Tema.oscuro = !Tema.oscuro
                Almacen.guardarAjustes(ctx)
            }
            BotonIcono(Icono.ENGRANAJE, "Ajustes", Tema.Tonal, onAjustes)
            AvatarPerfil(48.dp, Modifier.clickable(onClickLabel = "Mi perfil", role = Role.Button, onClick = onPerfil))
        }
    }
}

private data class Medida(val etiqueta: String, val texto: String, val nivel: Float)

/**
 * Teléfono con un líquido que se mece. Muestra una medida a la vez (estado, batería, espacio libre).
 * Para cambiar de medida: deslizar hacia el lado o tocar los puntitos. Al tocar el teléfono se abre el detalle.
 */
@Composable
private fun TarjetaTelefono(ultima: UltimaRevision?, onAbrir: () -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val medidas = remember(ultima) {
        val bat = Dispositivo.bateria(ctx)
        val alm = Dispositivo.almacenamiento()
        listOf(
            Medida("Estado", if (ultima != null) "${ultima.puntaje}%" else "—", (ultima?.puntaje ?: 0) / 100f),
            Medida(
                if (bat?.cargando == true) "Batería (cargando)" else "Batería",
                if (bat != null && bat.porcentaje >= 0) "${bat.porcentaje}%" else "—",
                (bat?.porcentaje ?: 0).coerceAtLeast(0) / 100f
            ),
            Medida(
                "Espacio libre",
                if (alm != null) "${alm.libre * 100 / alm.total}%" else "—",
                if (alm != null) alm.libre.toFloat() / alm.total.toFloat() else 0f
            )
        )
    }
    var pagina by remember { mutableIntStateOf(0) }
    var arrastre by remember { mutableFloatStateOf(0f) }
    val n = medidas.size
    val m = medidas[pagina % n]
    val marco = RoundedCornerShape(36.dp)
    val interior = RoundedCornerShape(29.dp)

    Box(
        modifier = modifier
            .shadow(6.dp, marco)
            .background(Tema.Marco, marco)
            .padding(7.dp)
            .clip(interior)
            .background(Tema.Tarjeta)
            .pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragStart = { arrastre = 0f },
                    onDragEnd = {
                        if (arrastre < -60f) pagina = (pagina + 1) % n
                        else if (arrastre > 60f) pagina = (pagina - 1 + n) % n
                    },
                    onDragCancel = {}
                ) { _, delta -> arrastre += delta }
            }
            .clickable(onClickLabel = "Ver el estado del equipo", onClick = onAbrir)
            .semantics { contentDescription = "${m.etiqueta}: ${m.texto}" }
    ) {
        OleajeLiquido(nivel = m.nivel, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Txt(m.texto, 30, Tema.TextoSobreOla, negrita = true)
            Txt(m.etiqueta, 15, Tema.TextoSobreOla, centrado = true)
        }

        // Puntitos: indican la medida actual; tocarlos pasa a la siguiente
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clickable(onClickLabel = "Cambiar de medida") { pagina = (pagina + 1) % n }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(n) { i ->
                Box(
                    Modifier
                        .size(if (i == pagina % n) 9.dp else 7.dp)
                        .background(Tema.TextoSobreOla.copy(alpha = if (i == pagina % n) 0.9f else 0.4f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun TarjetaEscanear(onClick: () -> Unit, modifier: Modifier) {
    val forma = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Tema.TintaAzul)
            .clickable(onClickLabel = "Escanear el equipo", role = Role.Button, onClick = onClick)
    ) {
        // Adorno suave en la esquina
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
                .size(54.dp)
                .background(Color.White.copy(alpha = 0.25f), CircleShape)
        )
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
            CuadroIcono(Icono.ESCANER, Tema.Tarjeta, Tema.Boton, 48.dp)
            Spacer(Modifier.height(8.dp))
            Txt("Escanear", 19, negrita = true)
            Txt("Analizar equipo", 14, Tema.TextoSuave, tamMin = 14)
        }
    }
}

@Composable
private fun TarjetaUltima(ultima: UltimaRevision?, abierta: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (ultima == null) Tema.TextoSuave else Tema.colorDe(ultima.nivel)
    // Cerrada es una tarjeta; abierta forma parte del recuadro grande que se despliega.
    val base = if (abierta) modifier.fillMaxWidth().clickable(onClickLabel = "Ocultar el detalle", onClick = onClick).padding(16.dp)
    else modifier.tarjeta(onClick)
    Column(modifier = base, verticalArrangement = Arrangement.Center) {
        Txt("Última revisión", 14, Tema.TextoSuave, tamMin = 14)
        Txt(cuando(ultima), 18, negrita = true)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Txt(
                if (ultima == null) "Sin datos" else Escaner.etiquetaDe(ultima.nivel), 14, color,
                negrita = true, tamMin = 14, modifier = Modifier.weight(1f)
            )
            Txt(if (abierta) "▴" else "▾", 18, Tema.TextoSuave, tamMin = 14)
        }
    }
}

@Composable
private fun TarjetaCorta(
    icono: Icono, fondoIcono: Color, titulo: String, estado: String, indicador: String,
    conTarjeta: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    val base = if (conTarjeta) modifier.tarjeta(onClick)
    else modifier.clickable(onClickLabel = "Ocultar", onClick = onClick).padding(16.dp)
    Column(modifier = base, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CuadroIcono(icono, fondoIcono, Tema.Boton, 48.dp)
            Txt(indicador, 22, Tema.TextoSuave, tamMin = 14)
        }
        Column {
            Txt(titulo, 19, negrita = true)
            Txt(estado, 15, Tema.TextoSuave)
        }
    }
}

private fun cuando(u: UltimaRevision?): String {
    if (u == null) return "Aún sin revisar"
    val dia = SimpleDateFormat("yyyyMMdd", Locale.US)
    val hoy = dia.format(Date())
    val ayer = dia.format(Date(System.currentTimeMillis() - 86_400_000L))
    val cuando = when (dia.format(Date(u.fecha))) {
        hoy -> "Hoy"
        ayer -> "Ayer"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(u.fecha))
    }
    return "$cuando, " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(u.fecha))
}
