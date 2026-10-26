package com.example.sanomovil001

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Una app instalada con señales de riesgo. [nivel] es ATENCION (conviene mirarla) o PELIGRO (señales claras). */
data class AppSospechosa(
    val nombre: String,
    val paquete: String,
    val nivel: Nivel,
    val motivos: List<String>,
    val instaladaEl: Long
)

/** Una revisión del equipo en sí (bloqueo de pantalla, depuración USB, modificaciones del sistema…). */
data class SenalEquipo(
    val titulo: String,
    val detalle: String,
    val nivel: Nivel,
    val penalizacion: Int,
    val guiaId: String?
)

data class InformeAmenazas(
    val apps: List<AppSospechosa>,   // de más a menos grave
    val revisadas: Int,              // cuántas apps de terceros se miraron
    val equipo: List<SenalEquipo>
) {
    val graves: Int get() = apps.count { it.nivel == Nivel.PELIGRO }
    val dudosas: Int get() = apps.count { it.nivel == Nivel.ATENCION }
}

/** Un instalador (.apk) guardado en Descargas, analizado SIN instalarlo. */
data class InstaladorRevisado(
    val nombreArchivo: String,
    val bytes: Long,
    val nivel: Nivel,
    val motivos: List<String>,
    val etiqueta: String?        // nombre de la app que instalaría, si se pudo leer
)

/**
 * Detección de amenazas EN EL EQUIPO (sin Internet, sin enviar nada fuera).
 *
 * Cómo funciona, con honestidad: Android impide que una app lea el código de otras apps, así que esto NO es
 * un antivirus de firmas. Lo que sí se puede mirar, y lo que hace el malware móvil real, es su comportamiento
 * visible: de dónde vino, qué capacidades delicadas tiene (accesibilidad, leer notificaciones, SMS,
 * dibujar sobre otras apps, ser administrador del equipo…), si se esconde, cómo está firmada y si imita a otra app.
 * Ninguna señal sola condena a una app: se suman, y una app que viene de una tienda conocida pesa mucho menos.
 * Además se compara con una lista local (assets/datos/amenazas.json) de paquetes, firmas y archivos conocidos.
 */
internal object Amenazas {

    // Títulos de los hallazgos (Escaner los usa para asignar guías).
    const val T_APPS = "Apps que podrían ser peligrosas"
    const val T_BLOQUEO = "Bloqueo de pantalla"
    const val T_USB = "Depuración por USB"
    const val T_SISTEMA = "Modificaciones del sistema"
    const val T_PROTECT = "Google Play Protect"

    // Umbrales de las reglas (ajustables). Suma de puntos de una app -> qué se le muestra a la persona.
    private const val UMBRAL_ATENCION = 25      // fuera de tienda: "conviene revisarla"
    private const val UMBRAL_PELIGRO = 60       // "señales claras de riesgo"
    private const val UMBRAL_TIENDA = 50        // de tienda: solo se avisa si reúne muchísimas capacidades
    private const val PESO_TIENDA_PCT = 40      // de tienda, las capacidades delicadas cuentan solo este %

    private const val MAX_APPS = 800
    private const val MAX_RESULTADOS = 30
    private const val MAX_INSTALADORES = 40
    private const val MAX_BYTES_HASH = 300L * 1024 * 1024

    private const val P = "android.permission."

    private val PERM_SMS = setOf(P + "READ_SMS", P + "RECEIVE_SMS", P + "SEND_SMS", P + "RECEIVE_MMS", P + "RECEIVE_WAP_PUSH")

    private val PERM_SENSIBLES = setOf(
        P + "CAMERA", P + "RECORD_AUDIO", P + "ACCESS_FINE_LOCATION", P + "ACCESS_BACKGROUND_LOCATION",
        P + "READ_CONTACTS", P + "READ_CALL_LOG", P + "READ_SMS", P + "RECEIVE_SMS", P + "SEND_SMS",
        P + "READ_PHONE_STATE", P + "READ_CALENDAR"
    )

    private const val PERM_SUPERPOSICION = P + "SYSTEM_ALERT_WINDOW"
    private const val PERM_INSTALAR = P + "REQUEST_INSTALL_PACKAGES"

    /** Tiendas alternativas conocidas (además de las de [Riesgos.tiendas]). */
    private val TIENDAS_EXTRA = setOf("org.fdroid.fdroid", "org.fdroid.basic", "com.aurora.store")

    private val DOBLE_EXTENSION = Regex(
        "\\.(pdf|docx?|xlsx?|pptx?|jpe?g|png|gif|mp3|mp4|txt|zip|rar)\\.(apk|xapk|apks)$",
        RegexOption.IGNORE_CASE
    )

    // ---------------------------------------------------------------- Evaluación (reglas)

    /** Todo lo que sabemos de una app (instalada) o de un instalador (archivo .apk). */
    private class Entrada(
        val nombre: String,
        val paquete: String,
        val instalada: Boolean,
        val deTienda: Boolean,
        val concedidos: Set<String>,
        val oculta: Boolean = false,
        val depurable: Boolean = false,
        val certDepuracion: Boolean = false,
        val accesibilidad: Boolean = false,
        val notificaciones: Boolean = false,
        val admin: Boolean = false,
        val smsPredeterminada: Boolean = false,
        val superposicion: Boolean = false,
        val instalaApps: Boolean = false,
        val firmas: List<String> = emptyList(),
        val hashApk: String? = null,
        val firmaDistinta: Boolean = false,
        val dobleExtension: Boolean = false
    )

    private class Evaluacion(val nivel: Nivel, val motivos: List<String>)

    /**
     * Suma señales y decide. Devuelve null si no hay nada que mostrar.
     *  - "fijos": engaños claros (imitar otra app, extensión falsa…). Pesan igual venga de donde venga.
     *  - "conducta": capacidades delicadas. Si la app viene de una tienda conocida, cuentan 40 %.
     * Umbrales: ver las constantes de arriba. De tienda, solo se avisa (y nunca como "claro")
     * si reúne muchísimas capacidades a la vez.
     */
    private fun evaluar(e: Entrada, lista: ListaAmenazas): Evaluacion? {
        val conocida = lista.paquetes[e.paquete]
            ?: e.firmas.firstNotNullOfOrNull { lista.certificados[it] }
            ?: e.hashApk?.let { lista.hashes[it] }
        if (conocida != null) {
            return Evaluacion(Nivel.PELIGRO, listOf("Coincide con una amenaza conocida: $conocida."))
        }

        val motivos = ArrayList<String>()
        var fijos = 0
        var conducta = 0

        fun cuando(instalada: String, archivo: String): String = if (e.instalada) instalada else archivo

        val nombreNorm = Catalogo.normalizarNombre(e.nombre)
        if (nombreNorm.isNotEmpty() &&
            lista.marcas.any { nombreNorm in it.nombres && e.paquete !in it.paquetes }
        ) {
            fijos += 60
            motivos.add("Se llama «${e.nombre}», pero no es la app oficial con ese nombre.")
        }
        if (e.dobleExtension) {
            fijos += 60
            motivos.add("El archivo se hace pasar por otra cosa (una foto, un PDF…), pero en realidad es un instalador.")
        }
        if (e.firmaDistinta) {
            fijos += 60
            motivos.add("Dice ser una actualización de una app que ya tienes, pero la firmó otro desarrollador.")
        }

        if (!e.deTienda) {
            if (e.paquete.startsWith("com.android.") || e.paquete.startsWith("com.google.android.")) {
                fijos += 35
                motivos.add("Usa un nombre reservado para apps del sistema o de Google. Es normal solo si la bajaste de una fuente oficial.")
            }
            if (e.certDepuracion) {
                conducta += 25
                motivos.add("Está firmada con una llave de pruebas de programadores, no con la de una empresa.")
            }
            if (e.depurable) {
                conducta += 15
                motivos.add("Es una versión de desarrollo, no una app lista para el público.")
            }
            if (e.oculta) {
                conducta += 15
                motivos.add("No tiene ícono en el menú: no se ve, pero está instalada.")
            }
        }

        val tieneSms = e.concedidos.any { it in PERM_SMS }
        val sensibles = e.concedidos.count { it in PERM_SENSIBLES }

        if (e.accesibilidad) {
            conducta += 35
            motivos.add(cuando(
                "Tiene un servicio de accesibilidad activo: puede leer y tocar la pantalla por ti.",
                "Pide un servicio de accesibilidad: podría leer y tocar la pantalla por ti."
            ))
        }
        if (e.notificaciones) {
            conducta += 25
            motivos.add(cuando(
                "Puede leer tus notificaciones (por ejemplo, los códigos de verificación).",
                "Pediría leer tus notificaciones (por ejemplo, los códigos de verificación)."
            ))
        }
        if (e.admin) {
            conducta += 40
            motivos.add(cuando(
                "Es administradora del equipo: puede dificultar que la quites.",
                "Pediría ser administradora del equipo, lo que dificulta quitarla."
            ))
        }
        if (e.smsPredeterminada) {
            conducta += 30
            motivos.add("Es tu app de mensajes de texto predeterminada.")
        } else if (tieneSms) {
            conducta += 25
            motivos.add(cuando("Puede leer o enviar tus mensajes de texto (SMS).", "Pide leer o enviar mensajes de texto (SMS)."))
        }
        if (e.superposicion) {
            conducta += 20
            motivos.add(cuando(
                "Puede dibujar encima de otras apps; a veces se usa para mostrar pantallas falsas.",
                "Pide dibujar encima de otras apps; a veces se usa para mostrar pantallas falsas."
            ))
        }
        if (e.instalaApps) {
            conducta += 15
            motivos.add(cuando("Puede instalar otras apps por su cuenta.", "Pide instalar otras apps por su cuenta."))
        }
        if (sensibles >= 6) {
            conducta += 15
            motivos.add("Tiene acceso a muchas cosas delicadas a la vez (mensajes, contactos, cámara, micrófono, ubicación…).")
        }

        // Combinaciones típicas del malware bancario móvil.
        if (e.accesibilidad && e.superposicion) {
            conducta += 25
            motivos.add("Accesibilidad más dibujar sobre otras apps: es una combinación que usan algunos troyanos bancarios.")
        }
        if ((tieneSms || e.smsPredeterminada) && (e.accesibilidad || e.notificaciones)) {
            conducta += 20
            motivos.add("Podría ver los códigos de verificación que te manda tu banco.")
        }
        if (e.oculta && !e.deTienda && (e.accesibilidad || e.admin || tieneSms || e.superposicion || e.notificaciones)) {
            conducta += 20
            motivos.add("Está escondida y además tiene accesos delicados.")
        }

        val total = fijos + (if (e.deTienda) conducta * PESO_TIENDA_PCT / 100 else conducta)
        val nivel = when {
            total >= UMBRAL_PELIGRO && (!e.deTienda || fijos >= UMBRAL_PELIGRO) -> Nivel.PELIGRO
            e.deTienda && total >= UMBRAL_TIENDA -> Nivel.ATENCION
            !e.deTienda && total >= UMBRAL_ATENCION -> Nivel.ATENCION
            else -> return null
        }

        if (!e.deTienda && e.instalada) motivos.add(0, "No viene de una tienda conocida.")
        if (e.deTienda && fijos < UMBRAL_PELIGRO) {
            motivos.add("Viene de una tienda, lo que es buena señal, pero reúne muchas capacidades delicadas a la vez.")
        }
        return Evaluacion(nivel, motivos)
    }

    // ---------------------------------------------------------------- Apps instaladas

    fun analizar(ctx: Context): InformeAmenazas {
        val lista = Catalogo.amenazas(ctx)
        val pm = ctx.packageManager

        val conAccesibilidad = serviciosAccesibilidad(ctx)
        val conNotificaciones: Set<String> = runCatching { NotificationManagerCompat.getEnabledListenerPackages(ctx) }
            .getOrDefault(emptySet())
        val administradoras = administradoras(ctx)
        val smsPredeterminada: String? = runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

        @Suppress("DEPRECATION")
        val paquetes = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)

        val encontradas = ArrayList<AppSospechosa>()
        var revisadas = 0

        for (info in paquetes) {
            if (revisadas >= MAX_APPS) break
            val ai = info.applicationInfo ?: continue
            val paquete = info.packageName
            if (paquete == ctx.packageName || Riesgos.esDelSistema(ai.flags)) continue
            revisadas++

            val origen = Riesgos.instalador(pm, paquete)
            val deTienda = origen != null && (origen in Riesgos.tiendas || origen in TIENDAS_EXTRA)
            val (pedidos, concedidos) = permisos(info)
            val nombre = runCatching { pm.getApplicationLabel(ai).toString().trim() }.getOrDefault("").take(80)

            // Las firmas solo se leen donde hacen falta (apps de fuera de tienda, o si hay firmas conocidas).
            val certs = if (!deTienda || lista.certificados.isNotEmpty()) firmasInstalada(pm, paquete) else emptyList()

            val entrada = Entrada(
                nombre = nombre.ifEmpty { paquete },
                paquete = paquete,
                instalada = true,
                deTienda = deTienda,
                concedidos = concedidos,
                oculta = pm.getLaunchIntentForPackage(paquete) == null,
                depurable = (ai.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                certDepuracion = !deTienda && certs.any { esCertificadoDeDepuracion(it) },
                accesibilidad = paquete in conAccesibilidad,
                notificaciones = paquete in conNotificaciones,
                admin = paquete in administradoras,
                smsPredeterminada = paquete == smsPredeterminada,
                superposicion = PERM_SUPERPOSICION in pedidos &&
                    opPermitida(ops, pm, "android:system_alert_window", PERM_SUPERPOSICION, ai.uid, paquete),
                instalaApps = PERM_INSTALAR in pedidos &&
                    opPermitida(ops, pm, "android:request_install_packages", PERM_INSTALAR, ai.uid, paquete),
                firmas = certs.map { sha256(it) }
            )
            val ev = evaluar(entrada, lista) ?: continue
            encontradas.add(AppSospechosa(entrada.nombre, paquete, ev.nivel, ev.motivos, info.firstInstallTime))
        }

        val ordenadas = encontradas
            .sortedWith(compareByDescending<AppSospechosa> { it.nivel.ordinal }.thenByDescending { it.motivos.size })
            .take(MAX_RESULTADOS)
        return InformeAmenazas(ordenadas, revisadas, senalesEquipo(ctx))
    }

    private fun permisos(info: PackageInfo): Pair<Set<String>, Set<String>> {
        val nombres = info.requestedPermissions ?: return emptySet<String>() to emptySet()
        val banderas = info.requestedPermissionsFlags
        val pedidos = HashSet<String>()
        val concedidos = HashSet<String>()
        for (i in nombres.indices) {
            val n = nombres[i] ?: continue
            pedidos.add(n)
            val b = banderas?.getOrNull(i) ?: 0
            if ((b and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) concedidos.add(n)
        }
        return pedidos to concedidos
    }

    private fun serviciosAccesibilidad(ctx: Context): Set<String> = runCatching {
        val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .mapNotNull { it.resolveInfo?.serviceInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

    private fun administradoras(ctx: Context): Set<String> = runCatching {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.activeAdmins?.map { it.packageName }?.toSet().orEmpty()
    }.getOrDefault(emptySet())

    /** "Mostrar sobre otras apps" / "instalar apps": se mira el permiso especial (AppOps). Si no se puede saber, false. */
    private fun opPermitida(
        ops: AppOpsManager?, pm: PackageManager, op: String, permiso: String, uid: Int, paquete: String
    ): Boolean = runCatching {
        if (ops == null) return@runCatching false
        @Suppress("DEPRECATION")
        val modo = ops.checkOpNoThrow(op, uid, paquete)
        modo == AppOpsManager.MODE_ALLOWED ||
            (modo == AppOpsManager.MODE_DEFAULT && pm.checkPermission(permiso, paquete) == PackageManager.PERMISSION_GRANTED)
    }.getOrDefault(false)

    // ---------------------------------------------------------------- Firmas

    @Suppress("DEPRECATION")
    private fun firmasInstalada(pm: PackageManager, paquete: String): List<ByteArray> = runCatching {
        val flag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        firmasDe(pm.getPackageInfo(paquete, flag))
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun firmasDe(pi: PackageInfo): List<ByteArray> = runCatching {
        if (Build.VERSION.SDK_INT >= 28) pi.signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        else pi.signatures?.map { it.toByteArray() }.orEmpty()
    }.getOrDefault(emptyList())

    /** Las apps de Android Studio en modo prueba se firman con "CN=Android Debug". Una app pública nunca debería. */
    private fun esCertificadoDeDepuracion(der: ByteArray): Boolean = runCatching {
        val c = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        c.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true)
    }.getOrDefault(false)

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    private fun sha256(b: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(b))

    private fun sha256Archivo(f: File): String? = runCatching {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        hex(md.digest())
    }.getOrNull()

    // ---------------------------------------------------------------- El equipo en sí

    private fun senalesEquipo(ctx: Context): List<SenalEquipo> {
        val r = ArrayList<SenalEquipo>()

        // 1) Bloqueo de pantalla
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        when (km?.isDeviceSecure) {
            false -> r.add(
                SenalEquipo(
                    T_BLOQUEO,
                    "Tu equipo no tiene PIN, patrón, contraseña ni huella. Si alguien lo toma, puede entrar a tus mensajes, fotos y cuentas.",
                    Nivel.ATENCION, 10, "bloqueo-pantalla"
                )
            )
            true -> r.add(SenalEquipo(T_BLOQUEO, "Tu equipo está protegido con un bloqueo de pantalla.", Nivel.OK, 0, null))
            null -> Unit
        }

        // 2) Depuración por USB
        val adb = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.ADB_ENABLED, 0) }.getOrDefault(0) == 1
        r.add(
            if (adb) SenalEquipo(
                T_USB,
                "La depuración por USB está activada: un computador conectado por cable podría controlar tu teléfono. Si no eres programador, conviene apagarla.",
                Nivel.ATENCION, 5, "depuracion-usb"
            ) else SenalEquipo(T_USB, "La depuración por USB está apagada.", Nivel.OK, 0, null)
        )

        // 3) Señales de root (sistema modificado)
        r.add(
            if (indiciosDeRoot(ctx)) SenalEquipo(
                T_SISTEMA,
                "Encontramos señales de que el sistema fue modificado para dar control total (root). Eso quita capas de protección de Android. Esta detección no es 100 % segura.",
                Nivel.ATENCION, 10, "sistema-modificado"
            ) else SenalEquipo(T_SISTEMA, "No vimos señales de que el sistema esté modificado.", Nivel.OK, 0, null)
        )

        // 4) Google Play Protect: solo se avisa si CLARAMENTE está apagado (no todas las marcas lo informan igual).
        val protectApagado = runCatching {
            val global = Settings.Global.getInt(ctx.contentResolver, "package_verifier_enable", 1)
            val usuario = Settings.Secure.getInt(ctx.contentResolver, "package_verifier_user_consent", 0)
            global == 0 || usuario == -1
        }.getOrDefault(false)
        if (protectApagado) {
            r.add(
                SenalEquipo(
                    T_PROTECT,
                    "Parece que Google Play Protect está apagado. Es el analizador gratuito de Google que revisa tus apps.",
                    Nivel.ATENCION, 8, "play-protect"
                )
            )
        }
        return r
    }

    @Suppress("DEPRECATION")
    private fun indiciosDeRoot(ctx: Context): Boolean {
        val rutas = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su",
            "/vendor/bin/su", "/su/bin/su", "/system/app/Superuser.apk", "/system/xbin/daemonsu"
        )
        if (rutas.any { runCatching { File(it).exists() }.getOrDefault(false) }) return true
        val apps = listOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser",
            "com.noshufou.android.su", "me.weishu.kernelsu"
        )
        return apps.any { runCatching { ctx.packageManager.getApplicationInfo(it, 0) }.isSuccess }
    }

    // ---------------------------------------------------------------- Instaladores (.apk) guardados

    /**
     * Revisa los instaladores de Descargas SIN instalarlos: lee su "manifiesto" (permisos, servicios, firma),
     * calcula su huella (SHA-256) y la compara con la lista local. Necesita el acceso a archivos que ya usa «Gestionar».
     */
    suspend fun revisarInstaladores(ctx: Context): List<InstaladorRevisado> {
        val lista = Catalogo.amenazas(ctx)
        val pm = ctx.packageManager
        val items = Archivos.listarSinRegistrar(Categoria.APLICACIONES).take(MAX_INSTALADORES)
        val salida = ArrayList<InstaladorRevisado>()

        for (item in items) {
            currentCoroutineContext().ensureActive()
            val f = item.archivo
            val nombre = f.name.take(80)
            val hash = if (item.bytes in 1..MAX_BYTES_HASH) sha256Archivo(f) else null
            val doble = DOBLE_EXTENSION.containsMatchIn(f.name)
            val pi = if (f.extension.equals("apk", ignoreCase = true)) infoDeApk(pm, f) else null

            val entrada: Entrada
            var etiqueta: String? = null
            if (pi != null) {
                val (pedidos, _) = permisos(pi)
                val certs = firmasDe(pi)
                val firmas = certs.map { sha256(it) }
                etiqueta = pi.applicationInfo?.let { runCatching { pm.getApplicationLabel(it).toString().trim().take(80) }.getOrNull() }
                    ?.takeIf { it.isNotEmpty() }
                val declaradosEnInstalada = firmasInstalada(pm, pi.packageName).map { sha256(it) }
                entrada = Entrada(
                    nombre = etiqueta ?: pi.packageName,
                    paquete = pi.packageName,
                    instalada = false,
                    deTienda = false,
                    concedidos = pedidos,
                    certDepuracion = certs.any { esCertificadoDeDepuracion(it) },
                    accesibilidad = pi.services?.any { it.permission == P + "BIND_ACCESSIBILITY_SERVICE" } == true,
                    notificaciones = pi.services?.any { it.permission == P + "BIND_NOTIFICATION_LISTENER_SERVICE" } == true,
                    admin = pi.receivers?.any { it.permission == P + "BIND_DEVICE_ADMIN" } == true,
                    superposicion = PERM_SUPERPOSICION in pedidos,
                    instalaApps = PERM_INSTALAR in pedidos,
                    firmas = firmas,
                    hashApk = hash,
                    firmaDistinta = declaradosEnInstalada.isNotEmpty() && firmas.isNotEmpty() &&
                        declaradosEnInstalada.none { it in firmas },
                    dobleExtension = doble
                )
            } else {
                // .xapk/.apks (paquetes comprimidos) o un .apk que no se pudo leer: solo huella y nombre.
                entrada = Entrada(
                    nombre = f.nameWithoutExtension.take(80),
                    paquete = "",
                    instalada = false,
                    deTienda = false,
                    concedidos = emptySet(),
                    hashApk = hash,
                    dobleExtension = doble
                )
            }

            val ev = evaluar(entrada, lista)
            salida.add(
                InstaladorRevisado(
                    nombre, item.bytes, ev?.nivel ?: Nivel.OK, ev?.motivos.orEmpty(), etiqueta
                )
            )
        }
        return salida.sortedByDescending { it.nivel.ordinal }
    }

    private fun infoDeApk(pm: PackageManager, f: File): PackageInfo? {
        @Suppress("DEPRECATION")
        val firma = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or firma
        @Suppress("DEPRECATION")
        val pi = runCatching { pm.getPackageArchiveInfo(f.path, flags) }.getOrNull() ?: return null
        // Truco conocido: sin esto, el nombre de la app no se puede leer desde un archivo sin instalar.
        pi.applicationInfo?.let {
            it.sourceDir = f.path
            it.publicSourceDir = f.path
        }
        return pi
    }
}
