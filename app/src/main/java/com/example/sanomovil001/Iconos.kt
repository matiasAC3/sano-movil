package com.example.sanomovil001

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Íconos propios de la app: líneas simples sobre una cuadrícula de 24 x 24 (metáforas literales: casa, candado, cámara...).
 * Cada ícono es un trazo (datos de camino SVG), así no hace falta ninguna librería de íconos.
 */
enum class Icono(val trazo: String) {
    CASA("M3 11 L12 3 L21 11 M5.5 9.5 V20 H10 V14 H14 V20 H18.5 V9.5"),
    EQUIPOS("M8 3 H16 A2 2 0 0 1 18 5 V19 A2 2 0 0 1 16 21 H8 A2 2 0 0 1 6 19 V5 A2 2 0 0 1 8 3 Z M3 9 V15 M21 9 V15 M11 18 H13"),
    CARPETA("M3 7 A2 2 0 0 1 5 5 H9 L11 7.5 H19 A2 2 0 0 1 21 9.5 V17 A2 2 0 0 1 19 19 H5 A2 2 0 0 1 3 17 Z"),
    AYUDA("M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z M9.5 9.6 A2.5 2.5 0 1 1 12.6 12 C12.1 12.3 12 12.7 12 13.4 M12 16.8 V17"),
    LUNA("M21 12.79 A9 9 0 1 1 11.21 3 A7 7 0 0 0 21 12.79 Z"),
    SOL("M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12 Z M12 2 V4 M12 20 V22 M2 12 H4 M20 12 H22 M4.9 4.9 L6.3 6.3 M17.7 17.7 L19.1 19.1 M4.9 19.1 L6.3 17.7 M17.7 6.3 L19.1 4.9"),
    ENGRANAJE("M18.41 10.04 L21.06 10.40 L21.06 13.60 L18.41 13.96 L17.97 15.04 L17.92 15.15 L19.54 17.28 L17.28 19.54 L15.15 17.92 L14.07 18.37 L13.96 18.41 L13.60 21.06 L10.40 21.06 L10.04 18.41 L8.96 17.97 L8.85 17.92 L6.72 19.54 L4.46 17.28 L6.08 15.15 L5.63 14.07 L5.59 13.96 L2.94 13.60 L2.94 10.40 L5.59 10.04 L6.03 8.96 L6.08 8.85 L4.46 6.72 L6.72 4.46 L8.85 6.08 L9.93 5.63 L10.04 5.59 L10.40 2.94 L13.60 2.94 L13.96 5.59 L15.04 6.03 L15.15 6.08 L17.28 4.46 L19.54 6.72 L17.92 8.85 L18.37 9.93 Z M15.2 12 A3.2 3.2 0 1 1 8.8 12 A3.2 3.2 0 1 1 15.2 12 Z"),
    ESCANER("M4 8 V6 A2 2 0 0 1 6 4 H8 M16 4 H18 A2 2 0 0 1 20 6 V8 M20 16 V18 A2 2 0 0 1 18 20 H16 M8 20 H6 A2 2 0 0 1 4 18 V16 M4 12 H20"),
    PORTAPAPELES("M8 5 H6 A2 2 0 0 0 4 7 V19 A2 2 0 0 0 6 21 H18 A2 2 0 0 0 20 19 V7 A2 2 0 0 0 18 5 H16 M9 3 H15 A1 1 0 0 1 16 4 V6 A1 1 0 0 1 15 7 H9 A1 1 0 0 1 8 6 V4 A1 1 0 0 1 9 3 Z M8 12 H16 M8 16 H13"),
    CANDADO("M6 11 H18 A2 2 0 0 1 20 13 V19 A2 2 0 0 1 18 21 H6 A2 2 0 0 1 4 19 V13 A2 2 0 0 1 6 11 Z M8 11 V8 A4 4 0 0 1 16 8 V11 M12 15 V17"),
    MICROFONO("M12 3 A3 3 0 0 1 15 6 V12 A3 3 0 0 1 9 12 V6 A3 3 0 0 1 12 3 Z M5 11 A7 7 0 0 0 19 11 M12 18 V21 M9 21 H15"),
    CAMARA("M4 7 H7.5 L9 5 H15 L16.5 7 H20 A1.5 1.5 0 0 1 21.5 8.5 V18 A1.5 1.5 0 0 1 20 19.5 H4 A1.5 1.5 0 0 1 2.5 18 V8.5 A1.5 1.5 0 0 1 4 7 Z M16 13 A4 4 0 1 1 8 13 A4 4 0 1 1 16 13 Z"),
    UBICACION("M12 21 C12 21 5 14.5 5 9.5 A7 7 0 0 1 19 9.5 C19 14.5 12 21 12 21 Z M14.5 9.5 A2.5 2.5 0 1 1 9.5 9.5 A2.5 2.5 0 1 1 14.5 9.5 Z"),
    CAMPANA("M6 17 V11 A6 6 0 0 1 18 11 V17 L20 19 H4 Z M10 21 A2 2 0 0 0 14 21 M12 3 V5"),
    TELEFONO("M5 4 H9 L11 9 L8.5 10.5 A11 11 0 0 0 13.5 15.5 L15 13 L20 15 V19 A2 2 0 0 1 18 21 A16 16 0 0 1 3 6 A2 2 0 0 1 5 4 Z"),
    MENSAJES("M4 5 H20 A1 1 0 0 1 21 6 V16 A1 1 0 0 1 20 17 H10 L6 21 V17 H4 A1 1 0 0 1 3 16 V6 A1 1 0 0 1 4 5 Z M7 10 H17 M7 13 H13"),
    PERSONA("M15 8 A3 3 0 1 1 9 8 A3 3 0 1 1 15 8 Z M5 20 C5 16 8 14 12 14 C16 14 19 16 19 20"),
    HOJA("M5 19 C5 10 10 5 20 4 C20 14 15 19 6 19 Z M5 19 L14 10"),
    CHECK("M5 12.5 L10 17.5 L19 7"),
    CERRAR("M6 6 L18 18 M18 6 L6 18"),
    ATRAS("M15 5 L8 12 L15 19"),
    IMAGEN("M5 4 H19 A2 2 0 0 1 21 6 V18 A2 2 0 0 1 19 20 H5 A2 2 0 0 1 3 18 V6 A2 2 0 0 1 5 4 Z M10.5 9 A1.5 1.5 0 1 1 7.5 9 A1.5 1.5 0 1 1 10.5 9 Z M3 17 L9 12 L13 16 L16 13 L21 18"),
    VIDEO("M3 6 H16 A1 1 0 0 1 17 7 V17 A1 1 0 0 1 16 18 H3 A1 1 0 0 1 2 17 V7 A1 1 0 0 1 3 6 Z M17 10 L22 7 V17 L17 14 Z"),
    AUDIO("M9 18 V6 L19 4 V16 M9 18 A3 3 0 1 1 3 18 A3 3 0 1 1 9 18 Z M19 16 A3 3 0 1 1 13 16 A3 3 0 1 1 19 16 Z"),
    DOCUMENTO("M7 3 H14 L19 8 V19 A2 2 0 0 1 17 21 H7 A2 2 0 0 1 5 19 V5 A2 2 0 0 1 7 3 Z M14 3 V8 H19 M8 13 H16 M8 17 H13"),
    DESCARGA("M4 15 V19 H20 V15 M12 4 V14 M7.5 9.5 L12 14 L16.5 9.5"),
    APPS("M4 4 H10 V10 H4 Z M14 4 H20 V10 H14 Z M4 14 H10 V20 H4 Z M14 14 H20 V20 H14 Z"),
    ALMACENAMIENTO("M3 14 L6 6 A1 1 0 0 1 7 5 H17 A1 1 0 0 1 18 6 L21 14 V18 A2 2 0 0 1 19 20 H5 A2 2 0 0 1 3 18 Z M3 14 H21 M7 17 H7.5");
}

/** Dibuja un ícono con el color y el tamaño pedidos. */
@Composable
fun IconoSano(icono: Icono, tam: Dp = 24.dp, color: Color = Tema.Texto, grosor: Float = 2f) {
    val camino = remember(icono) { PathParser().parsePathString(icono.trazo).toPath() }
    Canvas(modifier = Modifier.size(tam)) {
        val escala = size.minDimension / 24f
        withTransform({ scale(escala, escala, pivot = Offset.Zero) }) {
            drawPath(camino, color, style = Stroke(width = grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
