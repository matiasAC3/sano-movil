package com.example.sanomovil001

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Líquido que se mece como un oleaje dentro de su espacio. [nivel] va de 0 (vacío) a 1 (lleno).
 * Son dos olas con distinta velocidad y tamaño, así el movimiento se siente natural.
 * Si la persona activó "Reducir movimiento", el líquido queda quieto (pero sigue subiendo y bajando de nivel).
 */
@Composable
fun OleajeLiquido(nivel: Float, modifier: Modifier = Modifier) {
    val nivelAnimado by animateFloatAsState(targetValue = nivel.coerceIn(0f, 1f), animationSpec = tween(1400), label = "nivel")

    val fase: Float = if (Tema.reducirMovimiento) {
        0f
    } else {
        val transicion = rememberInfiniteTransition(label = "oleaje")
        val f by transicion.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(durationMillis = 5200, easing = LinearEasing)),
            label = "fase"
        )
        f
    }

    val arriba = Tema.OlaArriba
    val abajo = Tema.OlaAbajo

    Canvas(modifier = modifier) {
        val ancho = size.width
        val alto = size.height
        val superficie = alto * (1f - nivelAnimado)
        val amplitud = 7.dp.toPx()
        val dosPi = (2 * PI).toFloat()

        fun ola(amp: Float, longitud: Float, desfase: Float): Path {
            val camino = Path()
            camino.moveTo(0f, superficie + amp * sin(desfase))
            var x = 0f
            while (x <= ancho) {
                camino.lineTo(x, superficie + amp * sin(dosPi * x / longitud + desfase))
                x += 6f
            }
            camino.lineTo(ancho, alto)
            camino.lineTo(0f, alto)
            camino.close()
            return camino
        }

        // Ola de atrás: más clara, más lenta y en sentido contrario
        drawPath(ola(amplitud * 1.1f, ancho * 0.7f, -fase * 0.8f + 1.3f), color = arriba.copy(alpha = 0.55f))
        // Ola de adelante: con degradado, hacia el fondo más oscuro
        drawPath(
            ola(amplitud, ancho * 0.5f, fase),
            brush = Brush.verticalGradient(listOf(arriba, abajo), startY = superficie - amplitud, endY = alto)
        )
    }
}
