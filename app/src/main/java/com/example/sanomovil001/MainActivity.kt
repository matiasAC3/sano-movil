package com.example.sanomovil001

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Las pantallas de la app. Inicio, Mis equipos, Gestionar y Guías de ayuda tienen la barra de abajo. */
enum class Pantalla {
    PRINCIPAL, ECOSISTEMA, ARCHIVOS, GUIA_AYUDA,
    ESCANEO, RESULTADO, DIAGNOSTICO, SEGURIDAD, AJUSTES, PERFIL
}

class MainActivity : ComponentActivity() {

    // Sube cada vez que la app vuelve al primer plano (por ejemplo, al regresar de Ajustes).
    // Las pantallas lo usan para volver a leer permisos y archivos.
    private var reanudado by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Almacen.cargarAjustes(this)   // modo oscuro, contraste, movimiento y tamaño de letra de la vez anterior
        setContent {
            // Los íconos de la barra de arriba y de abajo cambian según el modo elegido.
            val oscuro = Tema.oscuro
            DisposableEffect(oscuro) {
                val estilo = if (oscuro) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose {}
            }
            TemaSano {
                SanoMovilApp(reanudado)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reanudado++
    }
}

@Composable
fun SanoMovilApp(reanudado: Int) {
    val context = LocalContext.current

    var pantalla by rememberSaveable { mutableStateOf(Pantalla.PRINCIPAL) }
    var categoria by rememberSaveable { mutableStateOf<Categoria?>(null) }
    var subDiagnostico by rememberSaveable { mutableStateOf<SubDiagnostico?>(null) }
    var guiaId by rememberSaveable { mutableStateOf<String?>(null) }
    var subEco by rememberSaveable { mutableStateOf<SubEcosistema?>(null) }
    var ecoId by rememberSaveable { mutableStateOf<String?>(null) }

    var aviso by remember { mutableStateOf<Pair<String, String>?>(null) }
    var revision by remember { mutableStateOf<ResultadoRevision?>(null) }
    var ultima by remember { mutableStateOf(Almacen.leer(context)) }

    // Cuenta: sin sesión y sin haber elegido "continuar sin cuenta", primero se muestra el inicio de sesión.
    var sesion by remember { mutableStateOf(SesionLocal.leer(context)) }
    var omitido by remember { mutableStateOf(SesionLocal.omitido(context)) }
    var verLogin by remember { mutableStateOf(false) }

    if (verLogin || (sesion == null && !omitido)) {
        PantallaLogin(
            onEntrar = { s ->
                SesionLocal.guardar(context, s)
                sesion = s
                verLogin = false
            },
            onOmitir = {
                SesionLocal.marcarOmitido(context)
                omitido = true
                verLogin = false
            }
        )
        return
    }

    // Un solo lugar decide a dónde se vuelve (botón "Volver" y botón "atrás" del celular).
    fun atras() {
        when {
            pantalla == Pantalla.ESCANEO -> {
                aviso = "Revisión cancelada" to "Cancelaste la revisión de tu equipo."
                pantalla = Pantalla.PRINCIPAL
            }
            pantalla == Pantalla.ARCHIVOS && categoria != null -> categoria = null
            pantalla == Pantalla.DIAGNOSTICO && subDiagnostico != null -> subDiagnostico = null
            pantalla == Pantalla.ECOSISTEMA && subEco != null -> subEco = null
            pantalla == Pantalla.GUIA_AYUDA && guiaId != null -> guiaId = null
            else -> pantalla = Pantalla.PRINCIPAL
        }
    }

    fun abrirGuia(id: String?) {
        guiaId = id
        pantalla = Pantalla.GUIA_AYUDA
    }

    // Cambio entre las cuatro secciones principales (barra de abajo).
    fun irAPestana(p: Pestana) {
        when (p) {
            Pestana.INICIO -> pantalla = Pantalla.PRINCIPAL
            Pestana.EQUIPOS -> {
                subEco = null
                ecoId = null
                pantalla = Pantalla.ECOSISTEMA
            }
            Pestana.GESTIONAR -> {
                categoria = null
                pantalla = Pantalla.ARCHIVOS
            }
            Pestana.AYUDA -> abrirGuia(null)
        }
    }

    BackHandler(enabled = pantalla != Pantalla.PRINCIPAL) { atras() }

    val nombre = remember(Perfil.version, sesion) { Perfil.leer(context).nombre.ifBlank { sesion?.nombre.orEmpty() } }

    when (pantalla) {
        Pantalla.PRINCIPAL -> Pantalla1Principal(
            ultima = ultima,
            nombre = nombre,
            reanudado = reanudado,
            onRevisar = { pantalla = Pantalla.ESCANEO },
            onEstado = {
                subDiagnostico = null
                pantalla = Pantalla.DIAGNOSTICO
            },
            onSeguridad = { pantalla = Pantalla.SEGURIDAD },
            onAjustes = { pantalla = Pantalla.AJUSTES },
            onPerfil = { pantalla = Pantalla.PERFIL },
            onGuia = { abrirGuia(it) },
            onPestana = { irAPestana(it) }
        )

        Pantalla.ECOSISTEMA -> PantallaEcosistema(
            sub = subEco,
            dispositivoId = ecoId,
            ultima = ultima,
            onAgregar = { subEco = SubEcosistema.AGREGAR },
            onAbrir = { id ->
                ecoId = id
                subEco = SubEcosistema.FICHA
            },
            onListo = { subEco = null },
            onGuia = { abrirGuia(it) },
            onVolver = { atras() },
            onPestana = { irAPestana(it) }
        )

        Pantalla.ARCHIVOS -> PantallaArchivos(
            categoria = categoria,
            onElegir = { categoria = it },
            onVolver = { atras() },
            reanudado = reanudado,
            onPestana = { irAPestana(it) }
        )

        Pantalla.GUIA_AYUDA -> Pantalla4GuiaAyuda(
            guiaId = guiaId,
            onElegir = { guiaId = it },
            onVolver = { atras() },
            onPestana = { irAPestana(it) }
        )

        Pantalla.ESCANEO -> PantallaEscaneo(
            onFinalizar = { r ->
                revision = r
                Almacen.guardar(context, r)
                ultima = Almacen.leer(context)
                pantalla = Pantalla.RESULTADO
            },
            onCancelar = { atras() }
        )

        Pantalla.RESULTADO -> {
            val r = revision
            if (r == null) {
                // Si no hay resultado en memoria (por ejemplo, tras cerrarse la app), se vuelve al inicio.
                LaunchedEffect(Unit) { pantalla = Pantalla.PRINCIPAL }
            } else {
                PantallaResultado(
                    revision = r,
                    onGuia = { abrirGuia(it) },
                    onVolver = { pantalla = Pantalla.PRINCIPAL }
                )
            }
        }

        Pantalla.DIAGNOSTICO -> PantallaDiagnostico(
            sub = subDiagnostico,
            onElegir = { subDiagnostico = it },
            onGuia = { abrirGuia(it) },
            onArchivos = {
                categoria = null
                pantalla = Pantalla.ARCHIVOS
            },
            onVolver = { atras() }
        )

        Pantalla.SEGURIDAD -> PantallaSeguridad(
            onGuia = { abrirGuia(it) },
            onArchivosApk = {
                categoria = Categoria.APLICACIONES
                pantalla = Pantalla.ARCHIVOS
            },
            onVolver = { atras() },
            reanudado = reanudado
        )

        Pantalla.AJUSTES -> PantallaAjustes(
            onPerfil = { pantalla = Pantalla.PERFIL },
            onVolver = { atras() }
        )

        Pantalla.PERFIL -> PantallaPerfil(
            sesion = sesion,
            onIniciarSesion = { verLogin = true },
            onCerrarSesion = {
                Autenticacion.proveedor.cerrarSesion()
                SesionLocal.cerrar(context)
                sesion = null
                omitido = true
            },
            onVolver = { atras() }
        )
    }

    aviso?.let { (titulo, texto) ->
        DialogoMensaje(texto = texto, titulo = titulo) { aviso = null }
    }
}
