package com.example.sanomovil001

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foto de perfil: la que la persona eligió, una imagen de la app, o una silueta si no hay ninguna. */
@Composable
fun AvatarPerfil(tam: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val v = Perfil.version
    val datos = remember(v) { Perfil.leer(ctx) }
    val foto = remember(v, datos.tipo) {
        if (datos.tipo == "foto") runCatching { BitmapFactory.decodeFile(Perfil.archivoFoto(ctx).path)?.asImageBitmap() }.getOrNull() else null
    }
    val fondo = if (datos.tipo == "predefinido") Color(Perfil.FONDOS[datos.indice]) else Tema.Tonal

    Box(
        modifier = modifier
            .size(tam)
            .clip(RoundedCornerShape(tam / 3))
            .background(fondo)
            .semantics { contentDescription = "Foto de perfil" },
        contentAlignment = Alignment.Center
    ) {
        when {
            foto != null -> Image(bitmap = foto, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(tam))
            datos.tipo == "predefinido" -> Text(Perfil.AVATARES[datos.indice], fontSize = (tam.value * 0.5f).sp)
            else -> IconoSano(Icono.PERSONA, tam * 0.6f, Tema.TextoSuave)
        }
    }
}

/**
 * Mi perfil: cambiar el nombre y la foto (una propia del teléfono o una imagen de Sano Móvil),
 * y entrar o salir de la cuenta.
 */
@Composable
fun PantallaPerfil(
    sesion: Sesion?,
    onIniciarSesion: () -> Unit,
    onCerrarSesion: () -> Unit,
    onVolver: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(Perfil.leer(ctx).nombre.ifBlank { sesion?.nombre.orEmpty() }) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var verImagenes by remember { mutableStateOf(false) }

    // Selector de fotos de Android: no pide ningún permiso, la persona elige y solo llega esa foto.
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { Perfil.importarFoto(ctx, uri) }
                mensaje = if (ok) "Listo, tu foto ya cambió." else "No se pudo usar esa imagen. Prueba con otra."
            }
        }
    }

    PantallaBase("Mi perfil", onVolver) {
        ZonaScroll {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AvatarPerfil(120.dp)
            }

            OutlinedTextField(
                value = nombre,
                onValueChange = { nuevo ->
                    // Solo letras, espacios y guiones (máximo 30)
                    if (nuevo.length <= 30 && nuevo.all { it.isLetter() || it == ' ' || it == '-' }) {
                        nombre = nuevo
                        Perfil.guardarNombre(ctx, nuevo)
                    }
                },
                label = { Text("¿Cómo quieres que te llamemos?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            BotonSano("Elegir una foto de mi teléfono") {
                elegirFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            BotonSano("Elegir una imagen de Sano Móvil", color = Tema.BotonSecundario) { verImagenes = !verImagenes }

            if (verImagenes) {
                Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Txt("Toca la que más te guste", 16, Tema.TextoSuave)
                    Perfil.AVATARES.chunked(4).forEachIndexed { fila, grupo ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            grupo.forEachIndexed { i, emoji ->
                                val indice = fila * 4 + i
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(Perfil.FONDOS[indice]))
                                        .border(2.dp, Tema.Borde, RoundedCornerShape(20.dp))
                                        .clickable(onClickLabel = "Usar esta imagen") { Perfil.usarPredefinido(ctx, indice) }
                                        .semantics { contentDescription = "Imagen ${indice + 1}" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(emoji, fontSize = (30 * Tema.escala).sp)
                                }
                            }
                        }
                    }
                }
            }
            BotonSano("Quitar mi foto", color = Tema.BotonSecundario, compacto = true) { Perfil.quitarFoto(ctx) }

            Column(modifier = Modifier.tarjeta(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sesion != null) {
                    Txt("Tu cuenta", 14, Tema.TextoSuave, tamMin = 14)
                    Txt(sesion.correo, 18, negrita = true)
                    BotonSano("Cerrar sesión", color = Tema.BotonSecundario, compacto = true, onClick = onCerrarSesion)
                } else {
                    Txt("Estás usando Sano Móvil sin cuenta", 18, negrita = true)
                    Txt("Con una cuenta podrás ver tus equipos en otros dispositivos.", 16, Tema.TextoSuave)
                    BotonSano("Iniciar sesión", compacto = true, onClick = onIniciarSesion)
                }
            }
        }
    }

    mensaje?.let { DialogoMensaje(texto = it, titulo = "Mi perfil") { mensaje = null } }
}
