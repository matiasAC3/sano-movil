package com.example.sanomovil001

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private fun Context.actividad(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Inicio de sesión: correo y contraseña, o "Continuar con Google". También se puede seguir sin cuenta.
 * Reglas de seguridad de esta pantalla:
 *  - la contraseña vive solo en memoria mientras se escribe (no se guarda ni se incluye al girar la pantalla)
 *  - se borra en cuanto se envía
 *  - nunca se pide la contraseña de Google aquí: Google tiene su propia ventana para eso
 */
@Composable
fun PantallaLogin(onEntrar: (Sesion) -> Unit, onOmitir: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val proveedor = Autenticacion.proveedor

    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }      // a propósito NO es rememberSaveable
    var verClave by remember { mutableStateOf(false) }
    var registrando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }

    fun resolver(r: ResultadoAuth) {
        trabajando = false
        when (r) {
            is ResultadoAuth.Exito -> {
                clave = ""
                onEntrar(r.sesion)
            }
            is ResultadoAuth.Error -> error = r.mensaje
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tema.Fondo)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("SanoMovil", 16, Tema.TextoSuave, negrita = true)
            IconoSano(Icono.HOJA, 18.dp, Tema.Boton)
        }
        Txt(if (registrando) "Crea tu cuenta" else "Qué bueno verte", 30, negrita = true)
        Txt("Con tu cuenta puedes ver tus equipos en otros dispositivos. Si prefieres, sigue sin cuenta.", 17, Tema.TextoSuave)

        if (proveedor.esDemo) {
            Column(modifier = Modifier.tarjeta(fondo = Tema.fondoDe(Nivel.ATENCION))) {
                Txt("Modo demostración: aún no está conectado a Google de verdad. Los datos no se verifican.", 15)
            }
        }

        OutlinedTextField(
            value = correo,
            onValueChange = { if (it.length <= 254) correo = it.trim() },
            label = { Text("Correo electrónico") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = clave,
            onValueChange = { if (it.length <= 64) clave = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { TextButton(onClick = { verClave = !verClave }) { Text(if (verClave) "Ocultar" else "Mostrar") } },
            modifier = Modifier.fillMaxWidth()
        )

        error?.let { Txt(it, 16, Tema.Peligro, negrita = true) }

        BotonSano(
            if (registrando) "Crear cuenta" else "Entrar",
            activo = !trabajando
        ) {
            error = null
            val problema = Autenticacion.revisar(correo, clave)
            if (problema != null) {
                error = problema
            } else {
                trabajando = true
                scope.launch {
                    resolver(
                        if (registrando) proveedor.registrar(correo, clave) else proveedor.iniciarSesion(correo, clave)
                    )
                }
            }
        }

        TextButton(onClick = { registrando = !registrando; error = null }, modifier = Modifier.fillMaxWidth()) {
            Txt(if (registrando) "Ya tengo cuenta" else "Es mi primera vez: crear cuenta", 17, Tema.Boton, negrita = true)
        }

        Txt("o", 16, Tema.TextoSuave, centrado = true, modifier = Modifier.fillMaxWidth())

        BotonSano("Continuar con Google", color = Tema.BotonSecundario, activo = !trabajando) {
            val actividad = ctx.actividad()
            if (actividad == null) {
                error = "No se pudo abrir Google en este momento."
            } else {
                error = null
                trabajando = true
                scope.launch { resolver(proveedor.conGoogle(actividad)) }
            }
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onOmitir, modifier = Modifier.fillMaxWidth()) {
            Txt("Continuar sin cuenta", 18, Tema.TextoSuave, negrita = true)
        }
    }
}
