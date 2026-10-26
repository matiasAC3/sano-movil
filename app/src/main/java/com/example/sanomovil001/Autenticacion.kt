package com.example.sanomovil001

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.delay

/** La cuenta con la que la persona entró. Nunca se guarda la contraseña. */
data class Sesion(val correo: String, val nombre: String)

sealed interface ResultadoAuth {
    data class Exito(val sesion: Sesion) : ResultadoAuth
    data class Error(val mensaje: String) : ResultadoAuth
}

/**
 * Lo que la pantalla de inicio de sesión necesita de "un servicio de cuentas".
 * Hoy se usa [AutenticacionDemo] (sin conexión real). Para conectar con Google de verdad se escribe otra clase
 * que cumpla esta misma interfaz (ver FIREBASE_PASO_A_PASO.md) y se cambia UNA línea en [Autenticacion.proveedor].
 */
interface ProveedorAutenticacion {
    val esDemo: Boolean
    suspend fun iniciarSesion(correo: String, clave: String): ResultadoAuth
    suspend fun registrar(correo: String, clave: String): ResultadoAuth
    suspend fun conGoogle(actividad: Activity): ResultadoAuth
    fun cerrarSesion()
}

internal object Autenticacion {
    /** ← Aquí se cambia a la versión con Firebase/Google cuando esté lista. */
    var proveedor: ProveedorAutenticacion = AutenticacionDemo

    private val CORREO = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,}$")

    fun correoValido(c: String): Boolean = c.length <= 254 && CORREO.matches(c)
    fun claveValida(c: String): Boolean = c.length in 8..64

    /** Mensaje para la persona si algo está mal escrito (o null si todo está bien). */
    fun revisar(correo: String, clave: String): String? = when {
        !correoValido(correo) -> "Revisa el correo: debe verse como nombre@ejemplo.com"
        !claveValida(clave) -> "La contraseña debe tener entre 8 y 64 caracteres."
        else -> null
    }

    /** "martina.perez@gmail.com" -> "Martina" */
    fun nombreDesdeCorreo(correo: String): String =
        correo.substringBefore('@').substringBefore('.').filter { it.isLetter() }.take(30)
            .replaceFirstChar { it.uppercase() }
}

/**
 * MODO DEMOSTRACIÓN: comprueba que lo escrito tenga forma de correo y contraseña, pero NO verifica ninguna cuenta.
 * Sirve para mostrar el flujo en la presentación. No es seguridad real.
 */
internal object AutenticacionDemo : ProveedorAutenticacion {
    override val esDemo = true

    private suspend fun entrar(correo: String, clave: String): ResultadoAuth {
        val problema = Autenticacion.revisar(correo, clave)
        if (problema != null) return ResultadoAuth.Error(problema)
        delay(500)
        return ResultadoAuth.Exito(Sesion(correo, Autenticacion.nombreDesdeCorreo(correo)))
    }

    override suspend fun iniciarSesion(correo: String, clave: String) = entrar(correo, clave)
    override suspend fun registrar(correo: String, clave: String) = entrar(correo, clave)

    override suspend fun conGoogle(actividad: Activity): ResultadoAuth {
        delay(500)
        return ResultadoAuth.Exito(Sesion("cuenta.demo@gmail.com", "Cuenta demo"))
    }

    override fun cerrarSesion() {}
}

/** Recuerda quién entró (solo correo y nombre) y si la persona prefirió usar la app sin cuenta. */
internal object SesionLocal {
    private const val ARCHIVO = "sano_sesion"
    private val CORREO = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,190}\\.[A-Za-z]{2,}$")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun leer(ctx: Context): Sesion? {
        val correo = prefs(ctx).getString("correo", null) ?: return null
        val nombre = prefs(ctx).getString("nombre", "").orEmpty().take(30)
        return if (correo.length <= 254 && CORREO.matches(correo)) Sesion(correo, nombre) else null
    }

    fun guardar(ctx: Context, s: Sesion) {
        prefs(ctx).edit().putString("correo", s.correo).putString("nombre", s.nombre.take(30)).putBoolean("omitido", false).apply()
    }

    fun cerrar(ctx: Context) {
        prefs(ctx).edit().remove("correo").remove("nombre").putBoolean("omitido", true).apply()
    }

    fun omitido(ctx: Context): Boolean = prefs(ctx).getBoolean("omitido", false)

    fun marcarOmitido(ctx: Context) {
        prefs(ctx).edit().putBoolean("omitido", true).apply()
    }
}
