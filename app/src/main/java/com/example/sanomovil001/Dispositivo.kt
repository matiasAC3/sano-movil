package com.example.sanomovil001

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlin.math.abs

/** Datos del propio equipo. Todo es solo lectura y no necesita permisos especiales. */
internal object Dispositivo {

    fun info(): InfoDispositivo = InfoDispositivo(
        marca = Build.MANUFACTURER.orEmpty().take(40),
        modelo = Build.MODEL.orEmpty().take(60),
        release = Build.VERSION.RELEASE.orEmpty().take(20),
        sdk = Build.VERSION.SDK_INT,
        parche = Build.VERSION.SECURITY_PATCH.orEmpty().take(20)
    )

    fun bateria(ctx: Context): BateriaInfo? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null

        val nivel = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val escala = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val porcentaje = if (nivel >= 0 && escala > 0) nivel * 100 / escala else -1

        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val tempC: Double? = if (temp == Int.MIN_VALUE) null else temp / 10.0

        val salud = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "buena"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "sobrecalentada"
            BatteryManager.BATTERY_HEALTH_DEAD -> "agotada"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "sobrevoltaje"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "falla"
            BatteryManager.BATTERY_HEALTH_COLD -> "fria"
            else -> "desconocida"
        }

        val estado = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val cargando = estado == BatteryManager.BATTERY_STATUS_CHARGING ||
            estado == BatteryManager.BATTERY_STATUS_FULL

        // Ciclos de carga: solo Android 14 o superior, y solo si el fabricante lo informa.
        val ciclos = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) else -1

        // Horas aproximadas al ritmo de consumo de este momento. Si el dato no es fiable, se omite.
        var horas: Double? = null
        runCatching {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val corriente = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val carga = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            if (!cargando && corriente != Long.MIN_VALUE && corriente != 0L &&
                carga != Long.MIN_VALUE && carga > 0
            ) {
                val h = carga.toDouble() / abs(corriente).toDouble()
                if (h in 0.5..200.0) horas = h
            }
        } // si falla, simplemente no hay estimación

        return BateriaInfo(porcentaje, tempC, salud, cargando, ciclos, horas)
    }

    fun almacenamiento(): AlmacenamientoInfo? = runCatching {
        val st = StatFs(Environment.getDataDirectory().path)
        if (st.totalBytes > 0 && st.availableBytes in 0..st.totalBytes) {
            AlmacenamientoInfo(st.totalBytes, st.availableBytes)
        } else null
    }.getOrNull()
}
