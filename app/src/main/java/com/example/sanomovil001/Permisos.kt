package com.example.sanomovil001

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Una app instalada y los permisos que tiene concedidos (solo los que nos interesan). */
data class AppConPermisos(
    val nombre: String,
    val paquete: String,
    val icono: ImageBitmap?,
    val estados: Map<TipoPermiso, EstadoPermiso>
)

/** Lectura de permisos de las apps. Android permite LEER esto, pero no cambiarlo desde otra app. */
internal object Permisos {

    private const val PERM_NOTIFICACIONES = "android.permission.POST_NOTIFICATIONS"       // Android 13+
    private const val PERM_UBICACION_SEGUNDO_PLANO = "android.permission.ACCESS_BACKGROUND_LOCATION" // Android 10+
    private const val MAX_APPS = 400

    /** Devuelve solo las apps con icono en el menú (las que el usuario reconoce) y que tengan algún permiso de la lista. */
    fun cargar(context: Context): List<AppConPermisos> {
        val pm = context.packageManager

        @Suppress("DEPRECATION")
        val paquetes = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        val resultado = mutableListOf<AppConPermisos>()

        for (info in paquetes) {
            if (resultado.size >= MAX_APPS) break
            val paquete = info.packageName
            if (paquete == context.packageName) continue                 // no mostrarnos a nosotros mismos
            if (pm.getLaunchIntentForPackage(paquete) == null) continue  // solo apps "visibles" para el usuario
            val appInfo = info.applicationInfo ?: continue

            val pedidos = info.requestedPermissions?.toSet() ?: emptySet()

            fun concedido(permiso: String): Boolean =
                permiso in pedidos &&
                    pm.checkPermission(permiso, paquete) == PackageManager.PERMISSION_GRANTED

            val estados = mutableMapOf<TipoPermiso, EstadoPermiso>()

            if (concedido(Manifest.permission.CAMERA)) {
                estados[TipoPermiso.CAMARA] = EstadoPermiso("Permitido", Nivel.OK)
            }

            if (concedido(Manifest.permission.RECORD_AUDIO)) {
                estados[TipoPermiso.MICROFONO] = EstadoPermiso("Permitido", Nivel.OK)
            }

            // Antes de Android 13 no existe este permiso, así que no se puede saber.
            if (Build.VERSION.SDK_INT >= 33 && concedido(PERM_NOTIFICACIONES)) {
                estados[TipoPermiso.NOTIFICACIONES] = EstadoPermiso("Puede enviarte avisos", Nivel.OK)
            }

            if (concedido(Manifest.permission.CALL_PHONE) || concedido(Manifest.permission.READ_PHONE_STATE)) {
                estados[TipoPermiso.TELEFONO] = EstadoPermiso("Permitido", Nivel.OK)
            }

            if (concedido(Manifest.permission.READ_SMS) || concedido(Manifest.permission.SEND_SMS) ||
                concedido(Manifest.permission.RECEIVE_SMS)
            ) {
                estados[TipoPermiso.MENSAJES] = EstadoPermiso("Permitido", Nivel.OK)
            }

            if (concedido(Manifest.permission.ACCESS_FINE_LOCATION) ||
                concedido(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                // Antes de Android 10 no había "solo mientras usas la app": tener ubicación era tenerla siempre.
                val siempre = Build.VERSION.SDK_INT < 29 || concedido(PERM_UBICACION_SEGUNDO_PLANO)
                estados[TipoPermiso.UBICACION] = if (siempre) {
                    EstadoPermiso("Siempre, aunque no uses la app", Nivel.ATENCION)
                } else {
                    EstadoPermiso("Solo mientras usas la app", Nivel.OK)
                }
            }

            if (estados.isEmpty()) continue

            val icono = runCatching { appInfo.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull()

            resultado.add(
                AppConPermisos(
                    nombre = pm.getApplicationLabel(appInfo).toString().take(80),
                    paquete = paquete,
                    icono = icono,
                    estados = estados
                )
            )
        }

        return resultado.sortedBy { it.nombre.lowercase() }
    }
}
