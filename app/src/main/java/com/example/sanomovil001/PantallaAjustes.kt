package com.example.sanomovil001

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Ajustes de la app: perfil, apariencia, tamaño de letra y transparencia sobre la privacidad. */
@Composable
fun PantallaAjustes(onPerfil: () -> Unit, onVolver: () -> Unit) {
    val ctx = LocalContext.current
    val guardar = { Almacen.guardarAjustes(ctx) }
    // ¿La app pide permiso de Internet? Se lee de la propia app, así lo que se muestra es siempre verdad.
    val usaInternet = remember {
        @Suppress("DEPRECATION")
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.contains("android.permission.INTERNET") == true
        }.getOrDefault(false)
    }

    PantallaBase("Ajustes", onVolver) {
        ZonaScroll {
            Row(modifier = Modifier.tarjeta(onClick = onPerfil), verticalAlignment = Alignment.CenterVertically) {
                AvatarPerfil(56.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Txt("Mi perfil", 19, negrita = true)
                    Txt("Cambiar mi nombre y mi foto", 16, Tema.TextoSuave)
                }
                Txt("›", 30, Tema.TextoSuave)
            }

            Txt("Apariencia", 20, negrita = true)
            FilaInterruptor("Modo oscuro", "Fondos oscuros, más suaves de noche", Tema.oscuro) { Tema.oscuro = it; guardar() }
            FilaInterruptor("Contraste alto", "Letras y bordes más marcados", Tema.altoContraste) { Tema.altoContraste = it; guardar() }
            FilaInterruptor("Reducir movimiento", "Detiene el oleaje y las animaciones", Tema.reducirMovimiento) { Tema.reducirMovimiento = it; guardar() }

            Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("Tamaño de letra", 18, negrita = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Normal" to 1f, "Grande" to 1.2f, "Muy grande" to 1.4f).forEach { (nombre, valor) ->
                        BotonSano(
                            nombre, Modifier.weight(1f),
                            color = if (Tema.escala == valor) Tema.Boton else Tema.BotonSecundario,
                            compacto = true
                        ) { Tema.escala = valor; guardar() }
                    }
                }
            }

            Txt("Privacidad", 20, negrita = true)
            Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(
                    if (usaInternet) "Sano Móvil usa Internet solo para tu cuenta." else "Sano Móvil no tiene acceso a Internet.",
                    18, negrita = true
                )
                Txt(
                    if (usaInternet) "La revisión de tu equipo, tus archivos y tus permisos se quedan en tu teléfono."
                    else "Lo que ves aquí se queda en tu teléfono: nada se envía a ningún sitio.",
                    16, Tema.TextoSuave
                )
            }

            Txt("Sano Móvil · versión 1.0 (prototipo)", 14, Tema.TextoSuave)
        }
    }
}

@Composable
private fun FilaInterruptor(titulo: String, detalle: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(modifier = Modifier.tarjeta(onClick = { alCambiar(!activo) }), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Txt(titulo, 18, negrita = true)
            Txt(detalle, 15, Tema.TextoSuave)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = activo, onCheckedChange = alCambiar)
    }
}
