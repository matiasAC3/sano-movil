package com.example.sanomovil001

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class LadoPestana { IZQUIERDA, DERECHA }

/**
 * Contorno de UN solo recuadro "abierto": una pestaña (una tarjeta de arriba, a la izquierda o a la derecha)
 * unida a un panel que ocupa todo el ancho. Así la tarjeta y lo que se despliega se ven como una sola pieza.
 *  - [hueco]: separación entre las dos columnas de tarjetas (en píxeles)
 *  - [arriba] y [base]: dónde empieza y dónde termina la pestaña; desde [base] hacia abajo es el panel
 */
class FormaPestana(
    private val lado: LadoPestana,
    private val hueco: Float,
    private val arriba: Float,
    private val base: Float,
    private val radio: Float,
    private val radioInterno: Float
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val mitad = (w - hueco) / 2f
        val vertices: List<Offset>
        val radios: List<Float>
        if (lado == LadoPestana.IZQUIERDA) {
            vertices = listOf(Offset(0f, arriba), Offset(mitad, arriba), Offset(mitad, base), Offset(w, base), Offset(w, h), Offset(0f, h))
            radios = listOf(radio, radio, radioInterno, radio, radio, radio)
        } else {
            val x0 = w - mitad
            vertices = listOf(Offset(x0, arriba), Offset(w, arriba), Offset(w, h), Offset(0f, h), Offset(0f, base), Offset(x0, base))
            radios = listOf(radio, radio, radio, radio, radio, radioInterno)
        }
        return Outline.Generic(rutaRedondeada(vertices, radios))
    }

    /** Une los vértices con líneas y redondea cada esquina con una curva suave. */
    private fun rutaRedondeada(v: List<Offset>, radios: List<Float>): Path {
        val ruta = Path()
        val n = v.size
        for (i in 0 until n) {
            val anterior = v[(i + n - 1) % n]
            val actual = v[i]
            val siguiente = v[(i + 1) % n]
            val haciaAtras = anterior - actual
            val haciaAdelante = siguiente - actual
            val r = minOf(radios[i], haciaAtras.getDistance() / 2f, haciaAdelante.getDistance() / 2f)
            val a = actual + haciaAtras / haciaAtras.getDistance() * r
            val b = actual + haciaAdelante / haciaAdelante.getDistance() * r
            if (i == 0) ruta.moveTo(a.x, a.y) else ruta.lineTo(a.x, a.y)
            ruta.quadraticBezierTo(actual.x, actual.y, b.x, b.y)
        }
        ruta.close()
        return ruta
    }
}

/** Fondo, borde y sombra suave con la forma del recuadro abierto (sin recortar lo que queda fuera de la forma). */
fun Modifier.desplegado(forma: Shape): Modifier = this
    .shadow(1.dp, forma, clip = false)
    .background(Tema.Tarjeta, forma)
    .border(1.dp, Tema.Borde, forma)
