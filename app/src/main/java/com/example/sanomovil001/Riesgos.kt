package com.example.sanomovil001

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityManager

/**
 * Señales de riesgo sencillas (no es un antivirus):
 *  - apps instaladas fuera de una tienda conocida
 *  - servicios de accesibilidad activados por apps que no son del sistema
 * Que algo aparezca aquí NO significa que sea malo; significa "vale la pena mirarlo".
 */
internal object Riesgos {
    private const val MAX = 40

    internal val tiendas = setOf(
        "com.android.vending",             // Google Play
        "com.sec.android.app.samsungapps", // Samsung Galaxy Store
        "com.huawei.appmarket",
        "com.xiaomi.mipicks",
        "com.xiaomi.market",
        "com.amazon.venezia",
        "com.heytap.market",
        "com.oppo.market",
        "com.vivo.appstore"
    )

    fun analizar(ctx: Context): RiesgosEquipo = RiesgosEquipo(fueraDeTienda(ctx), accesibilidad(ctx))

    internal fun esDelSistema(flags: Int): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    @Suppress("DEPRECATION")
    internal fun instalador(pm: PackageManager, paquete: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(paquete).installingPackageName
        else pm.getInstallerPackageName(paquete)
    }.getOrNull()

    private fun fueraDeTienda(ctx: Context): List<AppSimple> {
        val pm = ctx.packageManager
        val salida = ArrayList<AppSimple>()
        @Suppress("DEPRECATION")
        val instaladas = pm.getInstalledApplications(0)
        for (info in instaladas) {
            if (salida.size >= MAX) break
            val paquete = info.packageName
            if (paquete == ctx.packageName || esDelSistema(info.flags)) continue
            if (pm.getLaunchIntentForPackage(paquete) == null) continue
            val origen = instalador(pm, paquete)
            if (origen == null || origen !in tiendas) {
                salida.add(AppSimple(pm.getApplicationLabel(info).toString().take(80), paquete))
            }
        }
        return salida
    }

    private fun accesibilidad(ctx: Context): List<AppSimple> {
        val pm = ctx.packageManager
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val salida = ArrayList<AppSimple>()
        val vistos = HashSet<String>()
        for (s in am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (salida.size >= MAX) break
            val paquete = s.resolveInfo?.serviceInfo?.packageName ?: continue
            if (paquete == ctx.packageName || !vistos.add(paquete)) continue
            @Suppress("DEPRECATION")
            val info = runCatching { pm.getApplicationInfo(paquete, 0) }.getOrNull() ?: continue
            if (esDelSistema(info.flags)) continue
            salida.add(AppSimple(pm.getApplicationLabel(info).toString().take(80), paquete))
        }
        return salida
    }
}
