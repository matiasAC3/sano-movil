package com.example.sanomovil001

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Permisos de las apps, dentro del inicio: se elige un permiso (Micrófono, Cámara, Ubicación...)
 * y debajo aparecen las apps que lo tienen. Al tocar una app se abre su configuración para cambiarlo.
 */
@Composable
fun PanelPermisos(reanudado: Int, conTarjeta: Boolean = true) {
    val context = LocalContext.current
    val info = remember { Dispositivo.info() }
    var apps by remember { mutableStateOf<List<AppConPermisos>?>(null) }
    // null = ninguna lista abierta: al tocar de nuevo el permiso elegido, se ocultan las apps
    var elegido by rememberSaveable { mutableStateOf<TipoPermiso?>(TipoPermiso.CAMARA) }
    var elegida by remember { mutableStateOf<AppConPermisos?>(null) }

    // Se vuelve a leer cada vez que la app regresa al primer plano (por ejemplo, al volver de Ajustes).
    LaunchedEffect(reanudado) {
        apps = withContext(Dispatchers.IO) { Permisos.cargar(context) }
    }

    val comunes = listOf(TipoPermiso.MICROFONO, TipoPermiso.CAMARA, TipoPermiso.UBICACION)
    val otros = listOf(TipoPermiso.NOTIFICACIONES, TipoPermiso.TELEFONO, TipoPermiso.MENSAJES)

    // Lista de apps de un permiso (se muestra justo debajo del permiso elegido)
    @Composable
    fun ListaApps(tipo: TipoPermiso) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Tema.Fondo, RoundedCornerShape(20.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Txt("Aplicaciones con acceso a ${tipo.acceso}", 15, Tema.TextoSuave, negrita = true)

            val conAcceso = apps.orEmpty()
                .filter { tipo in it.estados }
                .sortedWith(
                    compareByDescending<AppConPermisos> { it.estados[tipo]?.nivel?.ordinal ?: 0 }
                        .thenBy { it.nombre.lowercase() }
                )

            when {
                tipo == TipoPermiso.NOTIFICACIONES && info.sdk < 33 -> {
                    Txt(
                        "Tu equipo usa Android ${info.release}. En esta versión, Android no permite ver qué apps tienen las notificaciones activadas.",
                        16
                    )
                    BotonSano("Abrir ajustes", compacto = true) { Ajustes.abrir(context, "general") }
                }
                apps == null -> Cargando()
                conAcceso.isEmpty() -> Txt("Ninguna app tiene este permiso.", 17, Tema.Exito, negrita = true)
                else -> conAcceso.forEach { app -> FilaAppPermiso(app, tipo) { elegida = app } }
            }
        }
    }

    Column(
        modifier = if (conTarjeta) Modifier.tarjeta() else Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Txt(if (conTarjeta) "Permisos" else "Elige un permiso", if (conTarjeta) 22 else 16, if (conTarjeta) Tema.Texto else Tema.TextoSuave, negrita = conTarjeta)
            Txt("Más comunes", 15, Tema.TextoSuave)
        }

        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            comunes.forEach { t -> ChipPermiso(t, t == elegido) { elegido = if (elegido == t) null else t } }
        }
        if (elegido != null && elegido in comunes) ListaApps(elegido!!)

        Txt("Otros permisos", 15, Tema.TextoSuave)
        otros.forEach { t ->
            FilaOtroPermiso(t, t == elegido) { elegido = if (elegido == t) null else t }
            if (elegido == t) ListaApps(t)
        }
    }

    elegida?.let { app ->
        DialogoSano(
            onDismissRequest = { elegida = null },
            title = { Text("Cambiar permiso de ${app.nombre}") },
            text = {
                Text(
                    "Se abrirá la configuración de esta app.\n\n${(elegido ?: TipoPermiso.CAMARA).instruccion}\n\n" +
                        "Los nombres pueden variar un poco según la marca de tu equipo."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    elegida = null
                    Ajustes.abrirDeApp(context, app.paquete)
                }) { Text("Abrir configuración") }
            },
            dismissButton = { TextButton(onClick = { elegida = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ChipPermiso(tipo: TipoPermiso, seleccionado: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .clip(forma)
            .background(if (seleccionado) Tema.Tonal else Tema.Tarjeta)
            .border(1.dp, Tema.Borde, forma)
            .clickable(role = Role.Tab, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconoSano(tipo.icono, 24.dp, if (seleccionado) Tema.Boton else Tema.TextoSuave)
            Txt(tipo.titulo, 17, if (seleccionado) Tema.Texto else Tema.TextoSuave, negrita = seleccionado)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(if (seleccionado) Tema.Boton else androidx.compose.ui.graphics.Color.Transparent)
        )
    }
}

@Composable
private fun FilaOtroPermiso(tipo: TipoPermiso, seleccionado: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (seleccionado) Tema.Tonal else Tema.Fondo)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconoSano(tipo.icono, 24.dp, Tema.TextoSuave)
        Txt(tipo.titulo, 18, negrita = seleccionado)
    }
}

@Composable
private fun FilaAppPermiso(app: AppConPermisos, tipo: TipoPermiso, onClick: () -> Unit) {
    val estado = app.estados[tipo]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Tema.Tarjeta)
            .clickable(onClickLabel = "Cambiar el permiso de ${app.nombre}", role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icono != null) {
            Image(bitmap = app.icono, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier.size(40.dp).background(Tema.Tonal, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Txt(app.nombre.take(1).uppercase(), 18, negrita = true) }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Txt(app.nombre, 17, negrita = true, maxLineas = 1)
            if (estado != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (estado.nivel != Nivel.OK) {
                        PuntoNivel(estado.nivel, 16.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Txt(estado.texto, 14, if (estado.nivel == Nivel.OK) Tema.TextoSuave else Tema.colorDe(estado.nivel), tamMin = 14)
                }
            }
        }
        Txt("›", 26, Tema.TextoSuave)
    }
}
