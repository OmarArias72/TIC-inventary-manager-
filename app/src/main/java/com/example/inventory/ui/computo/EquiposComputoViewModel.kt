package com.example.inventory.ui.computo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.services.OcrService
import com.example.inventory.domain.model.AnalisisImagen
import com.example.inventory.domain.model.CambioContrasena
import com.example.inventory.domain.model.CampoDestino
import com.example.inventory.domain.model.ContrasenasEquipo
import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.EquipoComputo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ObjetivoCaptura
import com.example.inventory.domain.model.ResultadoBusqueda
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.model.Sala
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.EquipoComputoRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import com.example.inventory.domain.usecase.BuscarEquipoPorCodigoUseCase
import com.example.inventory.domain.usecase.GuardarEquipoComputoUseCase
import com.example.inventory.domain.usecase.PermisosInventario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sala elegida en el selector. idSala = null significa "Sin sala asignada". */
data class FiltroSala(val idSala: Int?)

data class FormularioComputo(
    /** null = registro nuevo. */
    val inventarioOriginal: String?,
    val datos: DatosComputo,
    val inventarioEditable: Boolean,
    val estadosPermitidos: List<EstadoEquipo>,
    val tieneContrasena: Boolean = false,
    val tieneUltimaContrasena: Boolean = false,
    val cambioContrasenaPendiente: Boolean = false,
    val puedeVerContrasenas: Boolean = false,
    val contrasenaNueva: String = "",
    val borrarContrasena: Boolean = false,
    val motivo: String = "",
    val contrasenas: ContrasenasEquipo? = null,
    val cargandoContrasenas: Boolean = false,
    val buscandoEnInventario: Boolean = false,
    val mensaje: String? = null,
    val actualizadoPor: String? = null,
    val estadoSync: EstadoSync? = null,
    val guardando: Boolean = false,
    val error: String? = null,
) {
    val esEdicion: Boolean get() = inventarioOriginal != null
}

data class ComputoUiState(
    val salas: List<Sala> = emptyList(),
    val filtro: FiltroSala? = null,
    val equipos: List<EquipoComputo> = emptyList(),
    val totalEnSala: Int = 0,
    val busqueda: String = "",
    val rol: Rol = Rol.OTRO,
    val formulario: FormularioComputo? = null,
    val analizandoImagen: Boolean = false,
    val analisis: AnalisisImagen? = null,
    val camposAnalisis: List<CampoDestino> = emptyList(),
    val seleccionAnalisis: Map<CampoDestino, String> = emptyMap(),
)

private data class EstadoPantalla(
    val busqueda: String = "",
    val formulario: FormularioComputo? = null,
    val analizandoImagen: Boolean = false,
    val analisis: AnalisisImagen? = null,
    val camposAnalisis: List<CampoDestino> = emptyList(),
    val seleccionAnalisis: Map<CampoDestino, String> = emptyMap(),
)

/**
 * Pantalla "Equipos de cómputo": 1) elegir sala, 2) elegir un equipo de la sala para
 * actualizarlo, o registrar uno nuevo en esa sala. Todo se guarda primero en el dispositivo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EquiposComputoViewModel @Inject constructor(
    private val repositorio: EquipoComputoRepository,
    private val auth: AuthRepository,
    private val sincronizacion: SincronizacionRepository,
    private val guardarEquipo: GuardarEquipoComputoUseCase,
    private val buscarEnInventario: BuscarEquipoPorCodigoUseCase,
    private val ocr: OcrService,
) : ViewModel() {

    private val filtro = MutableStateFlow<FiltroSala?>(null)
    private val pantalla = MutableStateFlow(EstadoPantalla())

    private val equiposDeSala: Flow<List<EquipoComputo>> = filtro.flatMapLatest { f ->
        if (f == null) flowOf(emptyList()) else repositorio.observarPorSala(f.idSala)
    }

    val ui: StateFlow<ComputoUiState> = combine(
        repositorio.observarSalas(),
        filtro,
        equiposDeSala,
        auth.usuario,
        pantalla,
    ) { salas, f, equipos, usuario, p ->
        val q = p.busqueda.trim()
        ComputoUiState(
            salas = salas,
            filtro = f,
            equipos = if (q.isEmpty()) equipos else equipos.filter {
                it.inventario.contains(q, ignoreCase = true) || it.datos.base.serie.contains(q, ignoreCase = true)
            },
            totalEnSala = equipos.size,
            busqueda = p.busqueda,
            rol = usuario?.rol ?: Rol.OTRO,
            formulario = p.formulario,
            analizandoImagen = p.analizandoImagen,
            analisis = p.analisis,
            camposAnalisis = p.camposAnalisis,
            seleccionAnalisis = p.seleccionAnalisis,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ComputoUiState())

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    private val rol: Rol get() = auth.usuario.value?.rol ?: Rol.OTRO

    init {
        // Sin salas descargadas (primera vez): pedir una sincronización.
        viewModelScope.launch {
            if (repositorio.observarSalas().first().isEmpty()) sincronizacion.sincronizarAhora()
        }
    }

    // ---------- Sala y lista ----------

    fun seleccionarSala(nueva: FiltroSala) {
        filtro.value = nueva
        pantalla.update { it.copy(busqueda = "") }
    }

    fun onBusqueda(texto: String) = pantalla.update { it.copy(busqueda = texto) }

    fun sincronizar() = sincronizacion.sincronizarAhora()

    /** Crea una sala solo en el dispositivo y la entrega a [alCrear] para seleccionarla. */
    fun crearSalaLocal(codigo: String, nombre: String, ubicacion: String, alCrear: (Sala) -> Unit) {
        viewModelScope.launch {
            repositorio.crearSalaLocal(codigo, nombre, ubicacion)
                .onSuccess { sala ->
                    alCrear(sala)
                    _mensajes.send("Sala ${sala.codigo} creada en el dispositivo.")
                }
                .onFailure { e -> _mensajes.send(e.message ?: "No se pudo crear la sala.") }
        }
    }

    // ---------- Formulario ----------

    fun abrirNuevo() {
        val sala = filtro.value?.idSala
        actualizarFormulario {
            FormularioComputo(
                inventarioOriginal = null,
                datos = DatosComputo(idSala = sala),
                inventarioEditable = true,
                estadosPermitidos = PermisosInventario.estadosPermitidos(rol, null),
            )
        }
    }

    fun abrirEdicion(equipo: EquipoComputo) {
        if (equipo.estadoSync == EstadoSync.CONFLICTO) {
            _mensajes.trySend("Este equipo tiene un conflicto. Resuélvelo en Sincronización.")
            return
        }
        actualizarFormulario {
            FormularioComputo(
                inventarioOriginal = equipo.inventario,
                datos = equipo.datos,
                inventarioEditable = !equipo.existeEnServidor,
                estadosPermitidos = PermisosInventario.estadosPermitidos(rol, equipo.datos.base.estado),
                tieneContrasena = equipo.tieneContrasena,
                tieneUltimaContrasena = equipo.tieneUltimaContrasena,
                cambioContrasenaPendiente = equipo.cambioContrasenaPendiente,
                puedeVerContrasenas = equipo.idServidor != null && (equipo.tieneContrasena || equipo.tieneUltimaContrasena),
                actualizadoPor = equipo.actualizadoPor,
                estadoSync = equipo.estadoSync,
            )
        }
    }

    fun cerrarFormulario() = pantalla.update { it.copy(formulario = null) }

    private fun actualizarFormulario(cambio: (FormularioComputo?) -> FormularioComputo?) {
        pantalla.update { it.copy(formulario = cambio(it.formulario)) }
    }

    /** Modifica el formulario abierto (si no hay formulario no hace nada). */
    private fun editarFormulario(cambio: (FormularioComputo) -> FormularioComputo) {
        pantalla.update { p -> p.copy(formulario = p.formulario?.let(cambio)) }
    }

    fun onCambio(datos: DatosComputo) = editarFormulario { it.copy(datos = datos, error = null) }
    fun onContrasenaNueva(texto: String) = editarFormulario { it.copy(contrasenaNueva = texto, borrarContrasena = false, error = null) }
    fun onBorrarContrasena(borrar: Boolean) = editarFormulario { it.copy(borrarContrasena = borrar, contrasenaNueva = if (borrar) "" else it.contrasenaNueva) }
    fun onMotivo(texto: String) = editarFormulario { it.copy(motivo = texto) }

    /**
     * Al registrar: si el No. de inventario (o serie) ya existe en el inventario general,
     * se toman de ahí artículo, marca, modelo, serie y estado. El backend le agrega el detalle de cómputo.
     */
    fun buscarEnInventarioGeneral() {
        val f = pantalla.value.formulario ?: return
        val codigo = f.datos.base.inventario.ifBlank { f.datos.base.serie }.trim()
        if (codigo.isEmpty()) {
            editarFormulario { it.copy(mensaje = "Escribe o escanea el No. de inventario.") }
            return
        }
        editarFormulario { it.copy(buscandoEnInventario = true, mensaje = null) }
        viewModelScope.launch {
            val yaEsComputo = repositorio.obtener(codigo)
            if (yaEsComputo != null && yaEsComputo.inventario != f.inventarioOriginal) {
                editarFormulario {
                    it.copy(buscandoEnInventario = false, mensaje = "Ese equipo ya está registrado como equipo de cómputo. Ábrelo desde su sala para actualizarlo.")
                }
                return@launch
            }
            val mensaje: String
            when (val r = buscarEnInventario(codigo)) {
                is ResultadoBusqueda.Encontrado -> {
                    val base = r.equipo.datos
                    editarFormulario {
                        it.copy(
                            datos = it.datos.copy(base = if (it.inventarioEditable) base else base.copy(inventario = it.datos.base.inventario)),
                            estadosPermitidos = PermisosInventario.estadosPermitidos(rol, base.estado),
                        )
                    }
                    mensaje = "Datos tomados del inventario general" + if (r.desdeServidor) " (servidor)." else "."
                }
                ResultadoBusqueda.NoEncontrado -> mensaje = "No está en el inventario general: se registrará como equipo nuevo."
                is ResultadoBusqueda.SoloLocal -> mensaje = r.motivo
            }
            editarFormulario { it.copy(buscandoEnInventario = false, mensaje = mensaje) }
        }
    }

    fun verContrasenas() {
        val inventario = pantalla.value.formulario?.inventarioOriginal ?: return
        editarFormulario { it.copy(cargandoContrasenas = true) }
        viewModelScope.launch {
            repositorio.verContrasenas(inventario)
                .onSuccess { c -> editarFormulario { it.copy(cargandoContrasenas = false, contrasenas = c) } }
                .onFailure { e ->
                    editarFormulario { it.copy(cargandoContrasenas = false) }
                    _mensajes.send(e.message ?: "No se pudieron consultar las contraseñas.")
                }
        }
    }

    fun guardar() {
        val f = pantalla.value.formulario ?: return
        val contrasena = when {
            f.borrarContrasena -> CambioContrasena.Borrar
            f.contrasenaNueva.isNotEmpty() -> CambioContrasena.Nueva(f.contrasenaNueva)
            else -> CambioContrasena.SinCambio
        }
        editarFormulario { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            guardarEquipo(f.inventarioOriginal, f.datos, contrasena, f.motivo)
                .onSuccess {
                    // Mostrar la sala donde quedó el equipo
                    filtro.value = FiltroSala(f.datos.idSala)
                    cerrarFormulario()
                    _mensajes.send("Guardado. Se sincronizará cuando haya conexión.")
                }
                .onFailure { e -> editarFormulario { it.copy(guardando = false, error = e.message) } }
        }
    }

    // ---------- Escáner ----------

    fun analizarImagen(uri: Uri, objetivo: ObjetivoCaptura) {
        val editable = pantalla.value.formulario?.inventarioEditable ?: true
        val campos = CampoDestino.entries.filter { editable || it != CampoDestino.INVENTARIO }
        pantalla.update { it.copy(analizandoImagen = true) }
        viewModelScope.launch {
            val analisis = ocr.analizarImagen(uri)
            pantalla.update {
                it.copy(
                    analizandoImagen = false,
                    analisis = analisis,
                    camposAnalisis = campos,
                    seleccionAnalisis = analisis.seleccionInicial(objetivo, campos.toSet()),
                )
            }
        }
    }

    fun cancelarAnalisis() = pantalla.update { it.copy(analisis = null) }

    fun aplicarAnalisis(asignacion: Map<CampoDestino, String>) {
        pantalla.update { it.copy(analisis = null) }
        editarFormulario { f ->
            f.copy(
                datos = f.datos.copy(
                    base = f.datos.base.copy(
                        inventario = if (f.inventarioEditable) asignacion[CampoDestino.INVENTARIO] ?: f.datos.base.inventario
                        else f.datos.base.inventario,
                        serie = asignacion[CampoDestino.SERIE] ?: f.datos.base.serie,
                        marca = asignacion[CampoDestino.MARCA] ?: f.datos.base.marca,
                        modelo = asignacion[CampoDestino.MODELO] ?: f.datos.base.modelo,
                    )
                )
            )
        }
        // Registro nuevo con inventario capturado: buscarlo en el inventario general para completar los datos.
        val f = pantalla.value.formulario
        if (f != null && !f.esEdicion && asignacion.containsKey(CampoDestino.INVENTARIO)) buscarEnInventarioGeneral()
    }
}
