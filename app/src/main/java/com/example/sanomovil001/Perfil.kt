package com.example.sanomovil001

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Perfil de la persona: nombre y foto. La foto elegida se copia (ya recortada y reducida) a la carpeta
 * privada de la app, así no hace falta ningún permiso para volver a mostrarla.
 */
internal object Perfil {
    /** Sube cada vez que algo cambia, para que las pantallas se redibujen. */
    var version: Int by mutableIntStateOf(0)

    private const val ARCHIVO = "sano_perfil"
    private const val LADO = 256

    /** Imágenes que trae la app, para quien no quiere usar una foto propia. */
    val AVATARES = listOf("🐱", "🐶", "🐦", "🌻", "🌿", "🦋", "☕", "⭐")
    val FONDOS = listOf(0xFFDDE8F1, 0xFFE2EEE6, 0xFFECE8F4, 0xFFFBEFD2, 0xFFE2EEE6, 0xFFDDE8F1, 0xFFFBEFD2, 0xFFECE8F4)

    data class Datos(val nombre: String, val tipo: String, val indice: Int)   // tipo: "foto", "predefinido" o "ninguno"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun archivoFoto(ctx: Context) = File(ctx.filesDir, "perfil.jpg")

    fun leer(ctx: Context): Datos {
        val p = prefs(ctx)
        val tipo = p.getString("tipo", "ninguno").orEmpty()
        val indice = p.getInt("indice", 0)
        val tipoOk = when (tipo) {
            "foto" -> if (archivoFoto(ctx).exists()) "foto" else "ninguno"
            "predefinido" -> if (indice in AVATARES.indices) "predefinido" else "ninguno"
            else -> "ninguno"
        }
        return Datos(p.getString("nombre", "").orEmpty().take(30), tipoOk, indice)
    }

    fun guardarNombre(ctx: Context, nombre: String) {
        prefs(ctx).edit().putString("nombre", nombre.trim().take(30)).apply()
        version++
    }

    fun usarPredefinido(ctx: Context, indice: Int) {
        if (indice !in AVATARES.indices) return
        archivoFoto(ctx).delete()
        prefs(ctx).edit().putString("tipo", "predefinido").putInt("indice", indice).apply()
        version++
    }

    fun quitarFoto(ctx: Context) {
        archivoFoto(ctx).delete()
        prefs(ctx).edit().putString("tipo", "ninguno").apply()
        version++
    }

    /** Copia una foto elegida por la persona: la recorta en cuadrado y la reduce a 256 x 256. */
    fun importarFoto(ctx: Context, uri: Uri): Boolean {
        val original: Bitmap = runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val mayor = maxOf(info.size.width, info.size.height)
                    if (mayor > 1024) decoder.setTargetSampleSize(mayor / 1024 + 1)
                }
            } else {
                val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
                var muestra = 1
                while (medidas.outWidth / muestra > 1024 || medidas.outHeight / muestra > 1024) muestra *= 2
                val opciones = BitmapFactory.Options().apply { inSampleSize = muestra }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
                    ?: throw IllegalArgumentException()
            }
        }.getOrNull() ?: return false

        val guardada = runCatching {
            val lado = minOf(original.width, original.height)
            val cuadrado = Bitmap.createBitmap(original, (original.width - lado) / 2, (original.height - lado) / 2, lado, lado)
            val final = Bitmap.createScaledBitmap(cuadrado, LADO, LADO, true)
            archivoFoto(ctx).outputStream().use { final.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)

        if (guardada) {
            prefs(ctx).edit().putString("tipo", "foto").apply()
            version++
        }
        return guardada
    }
}
