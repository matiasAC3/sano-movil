package com.example.sanomovil001

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Categorías del gestor de archivos: qué carpetas mirar y qué tipos de archivo mostrar. */
enum class Categoria(
    val titulo: String,
    val icono: Icono,
    val carpetas: List<String>,
    val extensiones: Set<String>?   // null = todos los archivos
) {
    IMAGENES(
        "Imágenes", Icono.IMAGEN,
        listOf("DCIM", "Pictures", "Download"),
        setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp")
    ),
    VIDEOS(
        "Vídeos", Icono.VIDEO,
        listOf("DCIM", "Movies", "Download"),
        setOf("mp4", "mkv", "3gp", "webm", "mov", "avi")
    ),
    AUDIO(
        "Audio", Icono.AUDIO,
        listOf("Music", "Recordings", "Download"),
        setOf("mp3", "m4a", "wav", "ogg", "aac", "flac", "opus")
    ),
    DOCUMENTOS(
        "Documentos", Icono.DOCUMENTO,
        listOf("Documents", "Download"),
        setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "csv")
    ),
    DESCARGAS("Descargas", Icono.DESCARGA, listOf("Download"), null),
    APLICACIONES("Aplicaciones (APK)", Icono.APPS, listOf("Download"), setOf("apk", "xapk", "apks"))
}

/**
 * Toda la lógica de archivos (sin pantallas), con "doble llave":
 *  1) Solo se puede actuar sobre archivos que la última búsqueda devolvió (lista en memoria).
 *  2) Cada ruta se revisa otra vez: ruta canónica exacta, dentro del almacenamiento, archivo normal, sin enlaces.
 * Así, aunque otra parte del código pidiera borrar una ruta inventada, se rechaza.
 */
internal object Archivos {

    /** Un archivo encontrado, con su tamaño y fecha ya leídos. */
    data class Item(val archivo: File, val bytes: Long, val modificado: Long)

    private const val MAX_LISTA = 500
    private const val MAX_ACCION = 500
    private const val MAX_PROFUNDIDAD = 4
    private const val MAX_RECORRIDO = 3000
    private const val MENSAJE_RECHAZO = "No se pudo completar la acción por seguridad. Vuelve a abrir la lista e inténtalo de nuevo."

    private val permitidos: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())

    @Suppress("DEPRECATION")
    private fun raiz(): File = Environment.getExternalStorageDirectory().canonicalFile

    /** Aquí guardamos lo que crea la app (zips y copias). */
    private fun carpetaApp(): File = File(raiz(), "Download/SanoMovil").also { it.mkdirs() }

    // ---------- Permiso de acceso ----------

    fun tieneAcceso(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> false   // Android 10: el acceso clásico ya no existe
        else ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    /** Android 11 o superior: abre la pantalla donde el usuario activa "Acceso a todos los archivos". */
    @RequiresApi(Build.VERSION_CODES.R)
    fun abrirAjusteTodosLosArchivos(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
            )
        }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    // ---------- Buscar archivos ----------

    fun listar(categoria: Categoria): List<Item> {
        val base = raiz()
        val encontrados = mutableListOf<File>()
        for (nombre in categoria.carpetas) {
            recorrer(File(base, nombre), categoria.extensiones, 0, encontrados)
        }
        val items = encontrados
            .distinctBy { it.path }
            .map { Item(it, it.length(), it.lastModified()) }
            .sortedByDescending { it.modificado }
            .take(MAX_LISTA)

        synchronized(permitidos) {
            permitidos.clear()
            permitidos.addAll(items.map { it.archivo.path })
        }
        return items
    }

    /** Cuántos archivos hay y cuánto pesan (para las tarjetas de Gestionar). No toca la lista de archivos permitidos. */
    fun resumen(categoria: Categoria): Pair<Int, Long> {
        val unicos = buscar(categoria).distinctBy { it.path }
        return unicos.size to unicos.sumOf { it.length() }
    }

    /** Lo mismo que [listar], pero sin cambiar la "lista permitida" del gestor: para revisiones de seguridad que corren aparte. */
    fun listarSinRegistrar(categoria: Categoria): List<Item> =
        buscar(categoria).distinctBy { it.path }.map { Item(it, it.length(), it.lastModified()) }
            .sortedByDescending { it.modificado }.take(MAX_LISTA)

    const val LIMITE_RECORRIDO = MAX_RECORRIDO

    private fun buscar(categoria: Categoria): List<File> {
        val base = raiz()
        val encontrados = mutableListOf<File>()
        for (nombre in categoria.carpetas) recorrer(File(base, nombre), categoria.extensiones, 0, encontrados)
        return encontrados
    }

    private fun recorrer(carpeta: File, extensiones: Set<String>?, profundidad: Int, salida: MutableList<File>) {
        if (profundidad > MAX_PROFUNDIDAD || salida.size >= MAX_RECORRIDO) return
        val hijos = carpeta.listFiles() ?: return
        for (f in hijos) {
            if (f.name.startsWith(".")) continue                // ocultos
            if (Files.isSymbolicLink(f.toPath())) continue      // enlaces: nunca los seguimos
            if (f.isDirectory) {
                recorrer(f, extensiones, profundidad + 1, salida)
            } else if (f.isFile && (extensiones == null || f.extension.lowercase() in extensiones)) {
                salida.add(f)
            }
        }
    }

    // ---------- Validación ----------

    private fun validar(archivos: List<File>): List<File> {
        if (archivos.isEmpty() || archivos.size > MAX_ACCION) throw SecurityException()
        val prefijo = raiz().path + File.separator
        return archivos.distinctBy { it.path }.map { f ->
            val r = f.path
            if (r !in permitidos) throw SecurityException()
            if (f.canonicalPath != r || !r.startsWith(prefijo) || !f.isFile || Files.isSymbolicLink(f.toPath())) {
                throw SecurityException()
            }
            f
        }
    }

    // ---------- Acciones ----------

    fun comprimir(context: Context, pedidos: List<File>): String {
        val archivos = runCatching { validar(pedidos) }.getOrElse { return MENSAJE_RECHAZO }
        val marca = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val destino = File(carpetaApp(), "Comprimido_$marca.zip")
        return runCatching {
            ZipOutputStream(destino.outputStream().buffered()).use { zip ->
                val usados = mutableSetOf<String>()
                for (f in archivos) {
                    var nombre = f.name
                    var n = 1
                    while (!usados.add(nombre)) {
                        nombre = nombreConNumero(f.name, n)
                        n++
                    }
                    zip.putNextEntry(ZipEntry(nombre))     // solo el nombre, nunca una ruta
                    f.inputStream().use { entrada -> entrada.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            avisarAlSistema(context, listOf(destino))
            "Listo. Se creó «${destino.name}» en la carpeta Descargas > SanoMovil."
        }.getOrElse {
            destino.delete()
            "No se pudo comprimir. Revisa que quede espacio libre e inténtalo de nuevo."
        }
    }

    fun copiar(context: Context, pedidos: List<File>): String {
        val archivos = runCatching { validar(pedidos) }.getOrElse { return MENSAJE_RECHAZO }
        val carpeta = File(carpetaApp(), "Copias").also { it.mkdirs() }
        val nuevos = mutableListOf<File>()
        var fallos = 0
        for (f in archivos) {
            runCatching {
                var destino = File(carpeta, f.name)
                var n = 1
                while (destino.exists()) {
                    destino = File(carpeta, nombreConNumero(f.name, n))
                    n++
                }
                f.copyTo(destino)
                nuevos.add(destino)
            }.onFailure { fallos++ }
        }
        avisarAlSistema(context, nuevos)
        var texto = "Se copiaron ${nuevos.size} archivo(s) a Descargas > SanoMovil > Copias."
        if (fallos > 0) texto += " No se pudieron copiar $fallos."
        return texto
    }

    fun eliminar(context: Context, pedidos: List<File>): String {
        val archivos = runCatching { validar(pedidos) }.getOrElse { return MENSAJE_RECHAZO }
        var borrados = 0
        var fallos = 0
        for (f in archivos) {
            if (f.delete()) borrados++ else fallos++
        }
        synchronized(permitidos) { permitidos.removeAll(archivos.map { it.path }.toSet()) }
        avisarAlSistema(context, archivos)
        var texto = "Se eliminaron $borrados archivo(s)."
        if (fallos > 0) texto += " No se pudieron eliminar $fallos."
        return texto
    }

    /** "foto.jpg" + 2  ->  "foto (2).jpg" */
    private fun nombreConNumero(nombre: String, n: Int): String {
        val base = nombre.substringBeforeLast('.', nombre)
        val ext = if (nombre.contains('.')) "." + nombre.substringAfterLast('.') else ""
        return "$base ($n)$ext"
    }

    /** Avisa a la galería y a otras apps que hubo cambios (si no, podrían seguir mostrando archivos borrados). */
    private fun avisarAlSistema(context: Context, archivos: List<File>) {
        if (archivos.isEmpty()) return
        MediaScannerConnection.scanFile(context, archivos.map { it.absolutePath }.toTypedArray(), null, null)
    }

    // ---------- Para mostrar en pantalla ----------

    fun tamano(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    fun fecha(milisegundos: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(milisegundos))

    /** Etiqueta corta del tipo de archivo (por ejemplo "JPG"). */
    fun etiqueta(f: File): String = f.extension.uppercase().take(4).ifEmpty { "ARCH" }
}
