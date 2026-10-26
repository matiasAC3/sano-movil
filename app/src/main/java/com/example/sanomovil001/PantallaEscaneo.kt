package com.example.sanomovil001

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * "Revisando…": una lista de verificación que muestra qué se mira, qué ya está hecho y qué falta.
 * Las revisiones son reales. Si la persona sale de esta pantalla, la revisión se cancela sola.
 */
@Composable
fun PantallaEscaneo(
    onFinalizar: (ResultadoRevision) -> Unit,
    onCancelar: () -> Unit
) {
    val context = LocalContext.current
    var paso by remember { mutableIntStateOf(0) }
    val total = Escaner.pasos.size

    LaunchedEffect(Unit) {
        val resultado = Escaner.revisar(context) { paso = it }
        onFinalizar(resultado)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tema.Fondo)
            .safeDrawingPadding()
            .padding(20.dp)
    ) {
        Txt("Revisando tu equipo", 28, negrita = true)
        Txt("Solo tardará unos segundos. No tienes que hacer nada.", 18, Tema.TextoSuave)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Escaner.pasos.forEachIndexed { i, texto ->
                val actual = i == paso
                Row(
                    modifier = Modifier.tarjeta(fondo = if (actual) Tema.Tonal else null),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Txt(
                        if (i < paso) "✓" else if (actual) "●" else "○",
                        22, if (i <= paso) Tema.Boton else Tema.TextoSuave, negrita = true,
                        modifier = Modifier.width(34.dp)
                    )
                    Txt(texto, 18, if (actual) Tema.Texto else Tema.TextoSuave, negrita = actual)
                }
            }
        }

        BarraProgreso((paso + 1) / total.toFloat(), Tema.Boton, 10.dp)
        Spacer(Modifier.height(6.dp))
        Txt("Paso ${paso + 1} de $total", 16, Tema.TextoSuave)
        Spacer(Modifier.height(12.dp))
        BotonSano("Cancelar", color = Tema.BotonSecundario, onClick = onCancelar)
    }
}
