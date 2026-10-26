package com.example.sanomovil001

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seguridad.
 *  1) Resumen con el sello de estado.
 *  2) Apps para revisar (si hay), cada una con sus motivos en palabras simples.
 *  3) El equipo en sí (bloqueo, depuración USB, modificaciones, Play Protect).
 *  4) Instaladores (.apk) guardados, revisados sin instalarlos.
 *  5) Guías y límites honestos.
 * Nada sale del equipo: la app no tiene permiso de Internet.
 */
@Composable
fun PantallaSeguridad(
    onGuia: (String) -> Unit,
    onArchivosApk: () -> Unit,
    onVolver: () -> Unit,
    reanudado: Int
) {
    val context = LocalContext.current
    var informe by remember { mutableStateOf<InformeAmenazas?>(null) }
    var instaladores by remember { mutableStateOf<List<InstaladorRevisado>?>(null) }
    var acceso by remember { mutableStateOf(Archivos.tieneAcceso(context)) }
    var appElegida by remember { mutableStateOf<AppSospechosa?>(null) }
    var instaladorElegido by remember { mutableStateOf<InstaladorRevisado?>(null) }

    // Se vuelve a revisar cada vez que la app regresa al primer plano (por ejemplo, tras desinstalar algo).
    LaunchedEffect(reanudado) {
        val tiene = Archivos.tieneAcceso(context)
        acceso = tiene
        informe = withContext(Dispatchers.IO) { Amenazas.analizar(context) }
        instaladores = if (tiene) withContext(Dispatchers.IO) { Amenazas.revisarInstaladores(context) } else emptyList()
    }

    PantallaBase("Seguridad", onVolver) {
        val inf = informe
        if (inf == null) {
            Txt("Buscando señales de amenazas… Solo tardará unos segundos.", 17, Tema.TextoSuave)
            Cargando(Modifier.weight(1f))
        } else {
            val apks = instaladores
            val niveles = inf.apps.map { it.nivel } + inf.equipo.map { it.nivel } + apks.orEmpty().map { it.nivel }
            val general = niveles.maxByOrNull { it.ordinal } ?: Nivel.OK

            ZonaScroll {
                Resumen(inf, general)

                if (inf.apps.isNotEmpty()) {
                    Txt("Apps para revisar", 22, negrita = true)
                    inf.apps.forEach { app -> FilaAmenaza(app) { appElegida = app } }
                }

                Txt("Tu equipo", 22, negrita = true)
                inf.equipo.forEach { s -> TarjetaSenal(s, onGuia) }

                Txt("Instaladores guardados", 22, negrita = true)
                SeccionInstaladores(acceso, apks, context, onGuia) { instaladorElegido = it }

                Txt("Más para revisar", 22, negrita = true)
                TarjetaAccion("Quién lee tus notificaciones", "Ahí llegan los códigos de tu banco", Tema.Acentos[1]) { onGuia("notificaciones-acceso") }
                TarjetaAccion("Quién dibuja sobre otras apps", "Lo usan algunas pantallas falsas", Tema.Acentos[2]) { onGuia("superposicion") }
                TarjetaAccion("Google Play Protect", "El analizador gratuito de Google", Tema.Acentos[3]) { onGuia("play-protect") }

                BotonSano("Qué hacer con una app peligrosa", color = Tema.BotonSecundario, compacto = true) { onGuia("app-sospechosa") }
                BotonSano("Creo que ya me afectó", color = Tema.BotonSecundario, compacto = true) { onGuia("si-ya-te-afectaron") }

                Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Txt("Qué puede y qué no puede hacer", 17, negrita = true)
                    Txt(
                        "Esta revisión busca señales de riesgo dentro de tu equipo y no envía nada a Internet. " +
                            "Android no permite que una app mire el código de otras, así que no es un antivirus completo: " +
                            "no puede garantizar que no haya nada. Mantén Google Play Protect activo, instala solo desde tiendas conocidas " +
                            "y desconfía de enlaces que te pidan instalar algo.",
                        15, Tema.TextoSuave
                    )
                }
            }
        }
    }

    appElegida?.let { app ->
        DialogoSano(
            onDismissRequest = { appElegida = null },
            title = { Text(app.nombre) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Txt("Por qué aparece aquí:", 16, negrita = true)
                    app.motivos.forEach { Txt("• $it", 16) }
                    if (app.instaladaEl > 0L) Txt("Se instaló ${haceTiempo(app.instaladaEl)}.", 15, Tema.TextoSuave)
                    Txt(
                        "Que aparezca aquí no prueba que sea un virus. Si no la reconoces ni la usas, lo más seguro es desinstalarla: " +
                            "en la pantalla que se abre, toca «Desinstalar».",
                        15, Tema.TextoSuave
                    )
                    TextButton(onClick = { appElegida = null; onGuia("app-sospechosa") }) { Text("Ver qué hacer paso a paso") }
                    TextButton(onClick = { appElegida = null; onGuia("app-no-desinstala") }) { Text("No me deja desinstalarla") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val paquete = app.paquete
                    appElegida = null
                    Ajustes.abrirFichaApp(context, paquete)
                }) { Text("Abrir configuración") }
            },
            dismissButton = { TextButton(onClick = { appElegida = null }) { Text("Cerrar") } }
        )
    }

    instaladorElegido?.let { ins ->
        DialogoSano(
            onDismissRequest = { instaladorElegido = null },
            title = { Text(ins.etiqueta ?: ins.nombreArchivo) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Txt("Archivo: ${ins.nombreArchivo} (${Archivos.tamano(ins.bytes)})", 15, Tema.TextoSuave)
                    if (ins.motivos.isEmpty()) {
                        Txt(
                            "No encontramos señales de riesgo en este instalador. Aun así, si ya instalaste la app, " +
                                "conviene borrar el instalador.",
                            16
                        )
                    } else {
                        Txt("Por qué aparece aquí:", 16, negrita = true)
                        ins.motivos.forEach { Txt("• $it", 16) }
                        Txt("Si no sabes de dónde salió, no lo instales y bórralo.", 15, Tema.TextoSuave)
                    }
                    TextButton(onClick = { instaladorElegido = null; onGuia("instaladores-apk") }) { Text("Ver la guía") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    instaladorElegido = null
                    onArchivosApk()
                }) { Text("Ir a Gestionar") }
            },
            dismissButton = { TextButton(onClick = { instaladorElegido = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun Resumen(inf: InformeAmenazas, general: Nivel) {
    val graves = inf.graves
    val dudosas = inf.dudosas
    val frase = when {
        graves > 0 ->
            (if (graves == 1) "1 app tiene señales claras de riesgo." else "$graves apps tienen señales claras de riesgo.") +
                " Empieza por la primera de la lista."
        dudosas > 0 ->
            (if (dudosas == 1) "1 app conviene mirarla con calma." else "$dudosas apps conviene mirarlas con calma.")
        general != Nivel.OK -> "Tus apps se ven bien. Hay otros puntos del equipo para mirar."
        else -> "No encontramos señales de apps peligrosas entre las ${inf.revisadas} que revisamos."
    }
    Row(modifier = Modifier.tarjeta(fondo = Tema.fondoDe(general)), verticalAlignment = Alignment.CenterVertically) {
        SelloEstado(general, 72.dp)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(Escaner.etiquetaDe(general), 22, Tema.colorDe(general), negrita = true)
            Txt(frase, 16)
        }
    }
}

@Composable
private fun FilaAmenaza(app: AppSospechosa, onClick: () -> Unit) {
    Row(
        modifier = Modifier.tarjeta(onClick = onClick, fondo = Tema.fondoDe(app.nivel)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PuntoNivel(app.nivel, 30.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Txt(app.nombre, 19, negrita = true, maxLineas = 1)
            Txt(
                if (app.nivel == Nivel.PELIGRO) "Señales claras de riesgo" else "Conviene revisarla",
                15, Tema.colorDe(app.nivel), negrita = true
            )
            app.motivos.firstOrNull { !it.startsWith("No viene de una tienda") }?.let { Txt(it, 15, Tema.TextoSuave, maxLineas = 2) }
        }
        Txt("›", 30, Tema.TextoSuave)
    }
}

@Composable
private fun TarjetaSenal(s: SenalEquipo, onGuia: (String) -> Unit) {
    Row(modifier = Modifier.tarjeta(fondo = if (s.nivel == Nivel.OK) null else Tema.fondoDe(s.nivel)), verticalAlignment = Alignment.Top) {
        PuntoNivel(s.nivel, 28.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(s.titulo, 18, negrita = true)
            Txt(s.detalle, 16)
            if (s.guiaId != null && s.nivel != Nivel.OK) {
                BotonSano("Ver cómo resolverlo", compacto = true) { onGuia(s.guiaId) }
            }
        }
    }
}

@Composable
private fun SeccionInstaladores(
    acceso: Boolean,
    apks: List<InstaladorRevisado>?,
    context: android.content.Context,
    onGuia: (String) -> Unit,
    onElegir: (InstaladorRevisado) -> Unit
) {
    when {
        !acceso -> {
            val android10 = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
            Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(
                    if (android10) "En Android 10 el sistema no permite revisar tus archivos, así que no podemos mirar los instaladores guardados."
                    else "Los instaladores (.apk) son una puerta común para el malware. Para mirar los que tienes en Descargas necesitamos el mismo acceso a archivos que usa «Mis archivos». Se revisan sin instalarlos.",
                    16
                )
                if (!android10 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    BotonSano("Dar acceso a archivos", compacto = true) { Archivos.abrirAjusteTodosLosArchivos(context) }
                }
            }
        }
        apks == null -> Cargando(Modifier.fillMaxWidth())
        apks.isEmpty() -> Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt("No hay instaladores en tus descargas.", 17, Tema.Exito, negrita = true)
            Txt("Eso es lo ideal: las apps de las tiendas no dejan instaladores guardados.", 15, Tema.TextoSuave)
        }
        else -> {
            val n = apks.size
            Txt(
                if (n == 1) "Tienes 1 instalador guardado. Si ya no lo necesitas, bórralo." else "Tienes $n instaladores guardados. Si ya no los necesitas, bórralos.",
                16, Tema.TextoSuave
            )
            apks.forEach { ins ->
                Row(
                    modifier = Modifier.tarjeta(onClick = { onElegir(ins) }, fondo = if (ins.nivel == Nivel.OK) null else Tema.fondoDe(ins.nivel)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PuntoNivel(ins.nivel, 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Txt(ins.etiqueta ?: ins.nombreArchivo, 17, negrita = true, maxLineas = 1)
                        Txt(
                            when (ins.nivel) {
                                Nivel.PELIGRO -> "Señales claras de riesgo"
                                Nivel.ATENCION -> "Conviene revisarlo"
                                Nivel.OK -> "Sin señales de riesgo"
                            } + " · " + Archivos.tamano(ins.bytes),
                            15, Tema.TextoSuave, maxLineas = 1
                        )
                    }
                    Txt("›", 28, Tema.TextoSuave)
                }
            }
            BotonSano("Guía: instaladores guardados", color = Tema.BotonSecundario, compacto = true) { onGuia("instaladores-apk") }
        }
    }
}

private fun haceTiempo(fecha: Long): String {
    val dias = (System.currentTimeMillis() - fecha) / 86_400_000L
    return when {
        dias <= 0L -> "hoy"
        dias == 1L -> "ayer"
        dias < 60L -> "hace $dias días"
        else -> "hace ${dias / 30} meses"
    }
}
