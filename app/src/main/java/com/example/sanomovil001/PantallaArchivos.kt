package com.example.sanomovil001

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gestor de archivos. Tiene 3 estados:
 *  1) sin permiso  -> explica y pide acceso
 *  2) categoria == null -> menú (Imágenes, Vídeos, Descargas, Aplicaciones)
 *  3) categoria != null -> lista de archivos con acciones
 */
@Composable
fun PantallaArchivos(
    categoria: Categoria?,
    onElegir: (Categoria) -> Unit,
    onVolver: () -> Unit,
    reanudado: Int,
    onPestana: (Pestana) -> Unit
) {
    val context = LocalContext.current

    // Se vuelve a comprobar cada vez que la app regresa al primer plano (por ejemplo, desde Ajustes).
    var acceso by remember(reanudado) { mutableStateOf(Archivos.tieneAcceso(context)) }

    val lanzador = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        acceso = Archivos.tieneAcceso(context)
    }

    when {
        !acceso -> SinAcceso(
            onPestana = onPestana,
            onDarPermiso = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Archivos.abrirAjusteTodosLosArchivos(context)
                } else {
                    lanzador.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }
        )
        categoria == null -> MenuArchivos(onElegir, onPestana, reanudado)
        else -> ListaArchivos(categoria, onVolver)
    }
}

@Composable
private fun SinAcceso(onPestana: (Pestana) -> Unit, onDarPermiso: () -> Unit) {
    val noDisponible = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q // Android 10

    PantallaBase("Gestionar", onVolver = {}, pestana = Pestana.GESTIONAR, onPestana = onPestana) {
        ZonaScroll {
            Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (noDisponible) {
                    Txt("Esta función no está disponible en Android 10 por una restricción del propio sistema.", 18)
                } else {
                    Txt(
                        "Para ayudarte a ordenar tus fotos, vídeos y descargas, necesitamos tu permiso para ver los archivos del equipo.",
                        18
                    )
                    Txt("Esta app no tiene acceso a Internet: tus archivos no salen de tu equipo.", 15, Tema.TextoSuave)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Txt("Se abrirá una pantalla de Android: activa el interruptor y vuelve a esta app.", 15, Tema.TextoSuave)
                    }
                }
            }
            if (!noDisponible) BotonSano("Dar permiso", onClick = onDarPermiso)
        }
    }
}

@Composable
private fun MenuArchivos(onElegir: (Categoria) -> Unit, onPestana: (Pestana) -> Unit, reanudado: Int) {
    var resumenes by remember { mutableStateOf<Map<Categoria, Pair<Int, Long>>>(emptyMap()) }
    val alm = remember(reanudado) { Dispositivo.almacenamiento() }

    // Se cuenta categoría por categoría, así las tarjetas se van llenando sin esperar a todas.
    LaunchedEffect(reanudado) {
        for (c in Categoria.values()) {
            val r = withContext(Dispatchers.IO) { Archivos.resumen(c) }
            resumenes = resumenes + (c to r)
        }
    }

    PantallaBase("Gestionar", onVolver = {}, pestana = Pestana.GESTIONAR, onPestana = onPestana) {
        ZonaScroll {
            if (alm != null) {
                val usado = alm.usado.toFloat() / alm.total.toFloat()
                val color = if (usado > 0.9f) Tema.Peligro else if (usado > 0.8f) Tema.Alerta else Tema.Boton
                Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CuadroIcono(Icono.ALMACENAMIENTO, Tema.Tonal, Tema.Boton, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Txt("Almacenamiento", 19, negrita = true)
                            Txt("${Archivos.tamano(alm.libre)} libres de ${Archivos.tamano(alm.total)}", 16, Tema.TextoSuave)
                        }
                    }
                    BarraProgreso(usado, color, 10.dp)
                    Txt("${(usado * 100).toInt()} % ocupado", 15, Tema.TextoSuave)
                }
            }

            Txt("Tus archivos", 20, negrita = true)
            Categoria.values().toList().chunked(2).forEach { par ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    par.forEach { c ->
                        TarjetaCategoria(c, resumenes[c], Modifier.weight(1f).fillMaxHeight()) { onElegir(c) }
                    }
                    if (par.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Una categoría: ícono de color, nombre y cuánto hay dentro. */
@Composable
private fun TarjetaCategoria(categoria: Categoria, resumen: Pair<Int, Long>?, modifier: Modifier, onClick: () -> Unit) {
    val acento = Tema.Acentos[categoria.ordinal % 4]
    val detalle = when {
        resumen == null -> "Contando…"
        resumen.first == 0 -> "Sin archivos"
        else -> "${Archivos.tamano(resumen.second)} · ${resumen.first}${if (resumen.first >= Archivos.LIMITE_RECORRIDO) "+" else ""} archivos"
    }
    Column(modifier = modifier.tarjeta(onClick), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CuadroIcono(categoria.icono, acento.copy(alpha = 0.14f), acento, 52.dp)
        Column {
            Txt(categoria.titulo, 18, negrita = true, maxLineas = 2)
            Txt(detalle, 15, Tema.TextoSuave)
        }
    }
}

@Composable
private fun ListaArchivos(categoria: Categoria, onVolver: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var encontrados by remember(categoria) { mutableStateOf<List<Archivos.Item>?>(null) }
    var marcados by remember(categoria) { mutableStateOf<Set<String>>(emptySet()) }
    var recarga by remember { mutableIntStateOf(0) }
    var trabajando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var pedirConfirmacion by remember { mutableStateOf(false) }

    // Buscar archivos en segundo plano (puede tardar si hay muchos)
    LaunchedEffect(categoria, recarga) {
        encontrados = withContext(Dispatchers.IO) { Archivos.listar(categoria) }
        marcados = emptySet()
    }

    val lista = encontrados
    val elegidos = lista.orEmpty()
        .filter { it.archivo.absolutePath in marcados }
        .map { it.archivo }

    // Ejecuta una acción pesada en segundo plano, muestra "Trabajando…" y luego el resultado
    fun ejecutar(trabajo: () -> String) {
        scope.launch {
            trabajando = true
            val texto = withContext(Dispatchers.IO) { trabajo() }
            trabajando = false
            mensaje = texto
            recarga++
        }
    }

    PantallaBase(categoria.titulo, onVolver, textoVolver = "← Volver") {
        when {
            lista == null -> Cargando(Modifier.weight(1f))

            lista.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Txt("No encontramos archivos de este tipo.", 17, Tema.TextoSuave, centrado = true)
            }

            else -> {
                Txt("${lista.size} archivos · ${marcados.size} seleccionados", 14, Tema.TextoSuave)

                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(lista, key = { it.archivo.absolutePath }) { item ->
                        val ruta = item.archivo.absolutePath
                        FilaArchivo(item, marcado = ruta in marcados) {
                            marcados = if (ruta in marcados) marcados - ruta else marcados + ruta
                        }
                    }
                }

                if (marcados.isEmpty()) {
                    TextButton(
                        onClick = { marcados = lista.map { it.archivo.absolutePath }.toSet() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Txt("Seleccionar todo", 16, Tema.Boton, negrita = true)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonSano("Comprimir", Modifier.weight(1f), compacto = true) {
                            ejecutar { Archivos.comprimir(context, elegidos) }
                        }
                        BotonSano("Copiar", Modifier.weight(1f), compacto = true) {
                            ejecutar { Archivos.copiar(context, elegidos) }
                        }
                        BotonSano("Eliminar", Modifier.weight(1f), color = Tema.Peligro, compacto = true) {
                            pedirConfirmacion = true
                        }
                    }
                    TextButton(onClick = { marcados = emptySet() }, modifier = Modifier.fillMaxWidth()) {
                        Txt("Quitar selección", 15, Tema.TextoSuave)
                    }
                }
            }
        }
    }

    if (pedirConfirmacion) {
        DialogoSano(
            onDismissRequest = { pedirConfirmacion = false },
            title = { Text("¿Eliminar ${elegidos.size} archivo(s)?") },
            text = { Text("Esta acción no se puede deshacer. Los archivos se borrarán de tu equipo.") },
            confirmButton = {
                TextButton(onClick = {
                    pedirConfirmacion = false
                    ejecutar { Archivos.eliminar(context, elegidos) }
                }) { Text("Sí, eliminar") }
            },
            dismissButton = { TextButton(onClick = { pedirConfirmacion = false }) { Text("No, cancelar") } }
        )
    }

    if (trabajando) {
        DialogoSano(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Trabajando…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Espera un momento, por favor.")
                }
            }
        )
    }

    mensaje?.let { texto -> DialogoMensaje(texto = texto) { mensaje = null } }
}

@Composable
private fun FilaArchivo(item: Archivos.Item, marcado: Boolean, onCambiar: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onCambiar() }
            .tarjeta(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = marcado,
            onCheckedChange = { onCambiar() },
            colors = CheckboxDefaults.colors(
                checkedColor = Tema.Boton,
                uncheckedColor = Tema.TextoSuave,
                checkmarkColor = Tema.Texto
            )
        )
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 28.dp)
                .background(Tema.Tonal, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Txt(Archivos.etiqueta(item.archivo), 11, Tema.TextoSuave, negrita = true)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Txt(item.archivo.name, 16, maxLineas = 2)
            Txt("${Archivos.tamano(item.bytes)} · ${Archivos.fecha(item.modificado)}", 13, Tema.TextoSuave)
        }
    }
}
