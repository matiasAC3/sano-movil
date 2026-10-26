package com.example.sanomovil001

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Abrir pantallas de Ajustes. Solo se aceptan claves de una lista fija,
 * o la ficha de una app instalada y visible (nombre de paquete validado).
 */
internal object Ajustes {
    private val PAQUETE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    private val destinos: Map<String, List<String>> = mapOf(
        "actualizaciones" to listOf("android.settings.SYSTEM_UPDATE_SETTINGS", Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS),
        "bateria" to listOf(Settings.ACTION_BATTERY_SAVER_SETTINGS, Settings.ACTION_SETTINGS),
        "almacenamiento" to listOf(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Settings.ACTION_SETTINGS),
        "accesibilidad" to listOf(Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS),
        "privacidad" to listOf(Settings.ACTION_PRIVACY_SETTINGS, Settings.ACTION_SETTINGS),
        "seguridad" to listOf(Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS),
        "desarrollador" to listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS),
        "notificaciones-acceso" to listOf(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, Settings.ACTION_SETTINGS),
        "superposicion" to listOf(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Settings.ACTION_SETTINGS),
        "instalar-desconocidas" to listOf(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Settings.ACTION_SETTINGS),
        "general" to listOf(Settings.ACTION_SETTINGS)
    )

    val clavesValidas: Set<String> get() = destinos.keys

    fun abrir(ctx: Context, clave: String?): Boolean {
        if (clave == null) return false
        val acciones = destinos[clave] ?: return false
        for (accion in acciones) {
            if (lanzar(ctx, Intent(accion))) return true
        }
        return false
    }

    fun abrirDeApp(ctx: Context, paquete: String?): Boolean {
        if (paquete == null || paquete.length > 255 || !PAQUETE.matches(paquete)) return false
        if (ctx.packageManager.getLaunchIntentForPackage(paquete) == null) return false
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquete, null))
        return lanzar(ctx, intent)
    }

    /**
     * Ficha de ajustes de CUALQUIER app instalada (aunque no tenga ícono en el menú: las apps escondidas
     * son justo las que más conviene poder abrir). Se valida el nombre de paquete y que esté instalada.
     */
    @Suppress("DEPRECATION")
    fun abrirFichaApp(ctx: Context, paquete: String?): Boolean {
        if (paquete == null || paquete.length > 255 || !PAQUETE.matches(paquete)) return false
        if (runCatching { ctx.packageManager.getApplicationInfo(paquete, 0) }.isFailure) return false
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", paquete, null))
        return lanzar(ctx, intent)
    }

    private fun lanzar(ctx: Context, intent: Intent): Boolean =
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
