package com.example.sanomovil001

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Colores de toda la app: modo claro, modo oscuro y contraste alto. Si quieres cambiar el look, cámbialo aquí.
 * Paleta salvia (verde suave) sobre fondo cálido. Contrastes del modo claro: texto 12,8:1 · botón con letra blanca 6,1:1 · estados entre 5:1 y 6,4:1.
 * Los cuatro ajustes de abajo los cambia la persona desde la pantalla de Ajustes y se recuerdan.
 */
object Tema {
    var oscuro: Boolean by mutableStateOf(false)
    var altoContraste: Boolean by mutableStateOf(false)
    var reducirMovimiento: Boolean by mutableStateOf(false)

    /** Tamaño de letra elegido por la persona (1 = normal). Al cambiarlo, toda la app se redibuja. */
    var escala: Float by mutableFloatStateOf(1f)

    /** Elige el color según el modo (y, si hace falta, una versión de más contraste). */
    private fun c(claro: Long, oscuroC: Long, claroAlto: Long = claro, oscuroAlto: Long = oscuroC): Color = Color(
        if (oscuro) (if (altoContraste) oscuroAlto else oscuroC) else (if (altoContraste) claroAlto else claro)
    )

    // Superficies
    val Fondo: Color get() = c(0xFFFBFAF6, 0xFF111A16)
    val Tarjeta: Color get() = c(0xFFFFFFFF, 0xFF1A2520)
    val Borde: Color get() = c(0xFFE5E3DA, 0xFF2B3A33, 0xFF7C8A82, 0xFF8FA399)
    val Tonal: Color get() = c(0xFFE2EEE6, 0xFF23352C)          // verde salvia suave
    val TintaAzul: Color get() = c(0xFFDDE8F1, 0xFF223340)
    val TintaLila: Color get() = c(0xFFECE8F4, 0xFF2D2A3B)

    // Botones
    val Boton: Color get() = c(0xFF3F6B57, 0xFF86C8A8, 0xFF2F5646, 0xFFA5DCC1)
    val BotonSecundario: Color get() = Tonal
    val SobrePrimario: Color get() = c(0xFFFFFFFF, 0xFF0E2218)

    // Texto
    val Texto: Color get() = c(0xFF22332B, 0xFFE9F1EC, 0xFF0F1A15, 0xFFFFFFFF)
    val TextoSuave: Color get() = c(0xFF55655D, 0xFFA9BAB1, 0xFF33423A, 0xFFD5E2DA)

    // Estados (siempre van con símbolo y palabra, nunca solo color)
    val Exito: Color get() = c(0xFF2C6A4A, 0xFF7DD3A0)
    val Alerta: Color get() = c(0xFF8A5A00, 0xFFF0BE5A)
    val Peligro: Color get() = c(0xFFA82A2A, 0xFFF29A9A)

    // Teléfono con oleaje
    val Marco: Color get() = c(0xFF2F473C, 0xFF5E8F78)
    val OlaArriba: Color get() = c(0xFFB5D3C2, 0xFF3F7F63)
    val OlaAbajo: Color get() = c(0xFF7BAA90, 0xFF255A43)
    val TextoSobreOla: Color get() = c(0xFF1F3A2E, 0xFFF2FAF5)

    /** Colores de acento para distinguir las secciones. */
    val Acentos: List<Color>
        get() = if (oscuro) {
            listOf(Color(0xFF86C8A8), Color(0xFF8FB8E0), Color(0xFFB9A8E6), Color(0xFFE0B26A))
        } else {
            listOf(Color(0xFF3F6B57), Color(0xFF3F6FA3), Color(0xFF6F5BA6), Color(0xFF9A6A2A))
        }

    /** Color del semáforo para cada nivel. */
    fun colorDe(nivel: Nivel): Color = when (nivel) {
        Nivel.OK -> Exito
        Nivel.ATENCION -> Alerta
        Nivel.PELIGRO -> Peligro
    }

    /** Fondo suave del semáforo (para resaltar un aviso). */
    fun fondoDe(nivel: Nivel): Color = when (nivel) {
        Nivel.OK -> c(0xFFE4F1E8, 0xFF1E3A2D)
        Nivel.ATENCION -> c(0xFFFBEFD2, 0xFF3A2F14)
        Nivel.PELIGRO -> c(0xFFF8E1E1, 0xFF3D2020)
    }
}

/** Tema de toda la app: botones, ventanas, interruptores y casillas usan nuestros colores. */
@Composable
fun TemaSano(contenido: @Composable () -> Unit) {
    val esquema = if (Tema.oscuro) {
        darkColorScheme(
            primary = Tema.Boton, onPrimary = Tema.SobrePrimario,
            background = Tema.Fondo, onBackground = Tema.Texto,
            surface = Tema.Tarjeta, onSurface = Tema.Texto,
            error = Tema.Peligro, outline = Tema.Borde
        )
    } else {
        lightColorScheme(
            primary = Tema.Boton, onPrimary = Tema.SobrePrimario,
            background = Tema.Fondo, onBackground = Tema.Texto,
            surface = Tema.Tarjeta, onSurface = Tema.Texto,
            error = Tema.Peligro, outline = Color(0xFF6F8280)
        )
    }
    MaterialTheme(colorScheme = esquema, content = contenido)
}
