package com.example.inventory.ui.actualizar

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.services.OcrService
import com.example.inventory.domain.model.AnalisisImagen
import com.example.inventory.domain.model.CampoDestino
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.ObjetivoCaptura
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ResultadoBusqueda
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.usecase.BuscarEquipoPorCodigoUseCase
import com.example.inventory.domain.usecase.GuardarEquipoUseCase
import com.example.inventory.domain.usecase.PermisosInventario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActualizarUiState(
    val datos: DatosEquipo = DatosEquipo(),
    /** Equipo encontrado (se editará). null = no se ha buscado o no existe (se dará de alta). */
    val equipo: Equipo? = null,
    val buscado: Boolean = false,
    val origen: String? = null,
    val estadosPermitidos: List<EstadoEquipo> = listOf(EstadoEquipo.ALTA, EstadoEquipo.MANTENIMIENTO),
    val procesandoImagen: Boolean = false,
    /** Resultado del escáner pendiente de que el usuario elija los campos. */
    val analisis: AnalisisImagen? = null,
    val camposAnalisis: List<CampoDestino> = emptyList(),
    val seleccionAnalisis: Map<CampoDestino, String> = emptyMap(),
    val buscando: Boolean = false,
    val guardando: Boolean = false,
    val error: String? = null,
) {
    val ocupado: Boolean get() = procesandoImagen || buscando || guardando
    val esAlta: Boolean get() = buscado && equipo == null
}

/**
 * "Actualizar inventario": escanear la etiqueta, buscar el equipo (en el dispositivo y, si
 * no está, en el servidor), editarlo y guardar. Funciona sin conexión: el cambio queda
 * pendiente y se sincroniza después.
 */
@HiltViewModel
class ActualizarViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val buscarEquipo: BuscarEquipoPorCodigoUseCase,
    private val guardarEquipo: GuardarEquipoUseCase,
    private val ocr: OcrService,
) : ViewModel() {

    private val _ui = MutableStateFlow(ActualizarUiState(estadosPermitidos = permitidos(null)))
    val ui: StateFlow<ActualizarUiState> = _ui.asStateFlow()

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    private fun permitidos(estado: EstadoEquipo?): List<EstadoEquipo> =
        PermisosInventario.estadosPermitidos(auth.usuario.value?.rol ?: Rol.OTRO, estado)

    fun onCambio(datos: DatosEquipo) {
        _ui.update { it.copy(datos = datos, error = null) }
    }

    /** Analiza la foto y deja que el usuario elija qué valor va en cada campo. */
    fun procesarImagen(uri: Uri, objetivo: ObjetivoCaptura) {
        _ui.update { it.copy(procesandoImagen = true, error = null) }
        viewModelScope.launch {
            val analisis = ocr.analizarImagen(uri)
            // Si el equipo ya está en el servidor, su No. de inventario no se puede cambiar.
            val campos = CampoDestino.entries.filter { it != CampoDestino.INVENTARIO || _ui.value.equipo?.existeEnServidor != true }
            _ui.update {
                it.copy(
                    procesandoImagen = false,
                    analisis = analisis,
                    camposAnalisis = campos,
                    seleccionAnalisis = analisis.seleccionInicial(objetivo, campos.toSet()),
                )
            }
        }
    }

    fun cancelarAnalisis() {
        _ui.update { it.copy(analisis = null) }
    }

    /**
     * Aplica los valores elegidos. Si todavía no se ha buscado el equipo, se busca
     * con el No. de inventario (o con la serie, si solo se capturó la serie).
     */
    fun aplicarAnalisis(asignacion: Map<CampoDestino, String>) {
        val antes = _ui.value
        _ui.update {
            val d = it.datos
            it.copy(
                analisis = null,
                datos = d.copy(
                    inventario = asignacion[CampoDestino.INVENTARIO] ?: d.inventario,
                    serie = asignacion[CampoDestino.SERIE] ?: d.serie,
                    marca = asignacion[CampoDestino.MARCA] ?: d.marca,
                    modelo = asignacion[CampoDestino.MODELO] ?: d.modelo,
                ),
            )
        }
        val nuevoInventario = asignacion[CampoDestino.INVENTARIO]
        val nuevaSerie = asignacion[CampoDestino.SERIE]
        // Lo demás que se capturó (serie, marca, modelo) se aplica sobre el equipo encontrado.
        val capturados = asignacion - CampoDestino.INVENTARIO
        when {
            nuevoInventario != null && nuevoInventario != antes.equipo?.inventario ->
                buscar(nuevoInventario, capturados = capturados)
            !antes.buscado && nuevaSerie != null ->
                buscar(nuevaSerie, conservarCapturados = true, capturados = capturados - CampoDestino.SERIE)
        }
    }

    /**
     * Busca por No. de inventario o de serie.
     * @param codigo si es null se usa el No. de inventario escrito (o la serie si no hay inventario).
     * @param conservarCapturados si no se encuentra, conserva lo capturado en lugar de usar el código como inventario.
     * @param capturados valores leídos de la etiqueta que se ponen sobre los del equipo encontrado.
     */
    fun buscar(
        codigo: String? = null,
        conservarCapturados: Boolean = false,
        capturados: Map<CampoDestino, String> = emptyMap(),
    ) {
        val datos = _ui.value.datos
        val texto = (codigo ?: datos.inventario.ifBlank { datos.serie }).trim()
        if (texto.isEmpty()) {
            _ui.update { it.copy(error = "Escribe o escanea el No. de inventario o de serie.") }
            return
        }
        val porSerie = conservarCapturados || (codigo == null && datos.inventario.isBlank())
        _ui.update { it.copy(buscando = true, error = null) }
        viewModelScope.launch {
            when (val r = buscarEquipo(texto)) {
                is ResultadoBusqueda.Encontrado -> {
                    if (r.equipo.estadoSync == EstadoSync.CONFLICTO) {
                        _ui.update { it.copy(buscando = false) }
                        _mensajes.send("Este equipo tiene un conflicto. Resuélvelo en Sincronización.")
                        return@launch
                    }
                    _ui.update {
                        it.copy(
                            buscando = false,
                            buscado = true,
                            equipo = r.equipo,
                            datos = r.equipo.datos.let { d ->
                                d.copy(
                                    serie = capturados[CampoDestino.SERIE] ?: d.serie,
                                    marca = capturados[CampoDestino.MARCA] ?: d.marca,
                                    modelo = capturados[CampoDestino.MODELO] ?: d.modelo,
                                )
                            },
                            estadosPermitidos = permitidos(r.equipo.estado),
                            origen = if (r.desdeServidor) "Datos descargados del servidor" else "Datos del dispositivo",
                        )
                    }
                }
                ResultadoBusqueda.NoEncontrado -> {
                    marcarComoAlta(texto, porSerie, "No existe en el inventario: al guardar se registrará como nuevo.")
                }
                is ResultadoBusqueda.SoloLocal -> {
                    marcarComoAlta(texto, porSerie, "${r.motivo} Al guardar se registrará como nuevo.")
                }
            }
        }
    }

    private fun marcarComoAlta(codigo: String, porSerie: Boolean, mensaje: String) {
        _ui.update {
            it.copy(
                buscando = false,
                buscado = true,
                equipo = null,
                // Si se buscó por serie, el código no es el inventario: se deja lo capturado.
                datos = if (porSerie) it.datos else it.datos.copy(inventario = codigo),
                estadosPermitidos = permitidos(null),
                origen = mensaje,
            )
        }
    }

    fun guardar() {
        val estado = _ui.value
        if (!estado.buscado) {
            _ui.update { it.copy(error = "Primero busca el equipo (botón de lupa o escáner).") }
            return
        }
        _ui.update { it.copy(guardando = true, error = null) }
        viewModelScope.launch {
            guardarEquipo(estado.equipo?.inventario, estado.datos)
                .onSuccess {
                    _ui.value = ActualizarUiState(estadosPermitidos = permitidos(null))
                    _mensajes.send("Cambios guardados. Se sincronizarán cuando haya conexión.")
                }
                .onFailure { e -> _ui.update { it.copy(guardando = false, error = e.message) } }
        }
    }

    fun limpiar() {
        _ui.value = ActualizarUiState(estadosPermitidos = permitidos(null))
    }
}
