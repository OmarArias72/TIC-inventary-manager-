package com.example.inventory.ui.sincronizacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EquipoComputo
import com.example.inventory.domain.repository.EquipoComputoRepository
import com.example.inventory.domain.model.EstadoSincronizacion
import com.example.inventory.domain.repository.EquipoRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import com.example.inventory.domain.usecase.Resolucion
import com.example.inventory.domain.usecase.ResolverSincronizacionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SincronizacionUiState(
    val estado: EstadoSincronizacion = EstadoSincronizacion(),
    val equipos: List<Equipo> = emptyList(),
    val equiposComputo: List<EquipoComputo> = emptyList(),
    /** id de sala → "LAB-1 · Laboratorio 1", para mostrar la sala en las comparaciones. */
    val nombresSala: Map<Int, String> = emptyMap(),
    /** Inventario que se está resolviendo (deshabilita sus botones). */
    val procesando: String? = null,
)

@HiltViewModel
class SincronizacionViewModel @Inject constructor(
    repositorio: EquipoRepository,
    computo: EquipoComputoRepository,
    private val sincronizacion: SincronizacionRepository,
    private val resolver: ResolverSincronizacionUseCase,
) : ViewModel() {

    private val procesando = MutableStateFlow<String?>(null)

    val ui: StateFlow<SincronizacionUiState> = combine(
        sincronizacion.estado,
        repositorio.observarNoSincronizados(),
        computo.observarNoSincronizados(),
        computo.observarSalas(),
        procesando,
    ) { estado, equipos, equiposComputo, salas, enProceso ->
        SincronizacionUiState(estado, equipos, equiposComputo, salas.associate { it.id to it.etiqueta }, enProceso)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SincronizacionUiState())

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    fun sincronizarAhora() = sincronizacion.sincronizarAhora()

    fun resolver(inventario: String, resolucion: Resolucion, esComputo: Boolean = false) {
        procesando.value = inventario
        viewModelScope.launch {
            resolver.invoke(inventario, resolucion, esComputo)
                .onSuccess { _mensajes.send("Listo. Se sincronizará en cuanto haya conexión.") }
                .onFailure { e -> _mensajes.send(e.message ?: "No se pudo completar la acción.") }
            procesando.value = null
        }
    }
}
