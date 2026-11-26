package com.example.inventory.ui.inventario

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.data.services.ExcelService
import com.example.inventory.data.services.OcrService
import com.example.inventory.domain.model.AnalisisImagen
import com.example.inventory.domain.model.CampoDestino
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ObjetivoCaptura
import com.example.inventory.domain.model.ResultadoBusqueda
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.EquipoRepository
import com.example.inventory.domain.usecase.BuscarEquipoPorCodigoUseCase
import com.example.inventory.domain.usecase.EliminarEquipoUseCase
import com.example.inventory.domain.usecase.GuardarEquipoUseCase
import com.example.inventory.domain.usecase.PermisosInventario
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado del formulario (diálogo) de alta/edición. */
data class FormularioEquipo(
    /** null = alta nueva. */
    val inventarioOriginal: String?,
    val datos: DatosEquipo,
    val fechaRegistro: Long?,
    val inventarioEditable: Boolean,
    val estadosPermitidos: List<EstadoEquipo>,
    val guardando: Boolean = false,
    val error: String? = null,
) {
    val esEdicion: Boolean get() = inventarioOriginal != null
}

/** Resultado del OCR esperando a que el usuario elija qué valor va en cada campo. */
data class AnalisisPendiente(
    val analisis: AnalisisImagen,
    val camposPermitidos: List<CampoDestino>,
    val seleccionInicial: Map<CampoDestino, String>,
    /** true = la foto se tomó para buscar; false = para llenar el formulario. */
    val paraBusqueda: Boolean,
)

/** Criterios de orden de la tabla. */
enum class OrdenInventario(val etiqueta: String) {
    FECHA_RECIENTE("Fecha: más recientes"),
    FECHA_ANTIGUA("Fecha: más antiguos"),
    ARTICULO("Tipo de artículo (A-Z)"),
    MARCA("Marca (A-Z)"),
}

data class InventarioUiState(
    val cargando: Boolean = true,
    /** Equipos de la página actual (ya filtrados y ordenados). */
    val equipos: List<Equipo> = emptyList(),
    /** Cuántos coinciden con la búsqueda (todas las páginas). */
    val totalFiltrados: Int = 0,
    val totalEquipos: Int = 0,
    val orden: OrdenInventario = OrdenInventario.FECHA_RECIENTE,
    /** Página actual, empezando en 0. */
    val pagina: Int = 0,
    val totalPaginas: Int = 1,
    val tamanoPagina: Int = TAMANO_PAGINA_INICIAL,
    val rol: Rol = Rol.OTRO,
    val formulario: FormularioEquipo? = null,
    val busqueda: String = "",
    val buscandoEnServidor: Boolean = false,
    val mensajeBusqueda: String? = null,
    val analizandoImagen: Boolean = false,
    val analisis: AnalisisPendiente? = null,
)

const val TAMANO_PAGINA_INICIAL = 20
val TAMANOS_PAGINA = listOf(10, 20, 50)

/** Estado propio de la pantalla (lo que no viene de la base de datos). */
private data class EstadoPantalla(
    val formulario: FormularioEquipo? = null,
    val busqueda: String = "",
    val orden: OrdenInventario = OrdenInventario.FECHA_RECIENTE,
    val pagina: Int = 0,
    val tamanoPagina: Int = TAMANO_PAGINA_INICIAL,
    val buscandoEnServidor: Boolean = false,
    val mensajeBusqueda: String? = null,
    val analizandoImagen: Boolean = false,
    val analisis: AnalisisPendiente? = null,
)

sealed interface InventarioEvento {
    data class Mensaje(val texto: String) : InventarioEvento
    /** Pedir al usuario dónde guardar el archivo (el sistema muestra el selector). */
    data class ElegirDestino(val nombreArchivo: String) : InventarioEvento
    data class Compartir(val uri: Uri) : InventarioEvento
}

enum class AccionExportar { GUARDAR, COMPARTIR }

@HiltViewModel
class InventarioViewModel @Inject constructor(
    repositorio: EquipoRepository,
    private val auth: AuthRepository,
    private val guardarEquipo: GuardarEquipoUseCase,
    private val eliminarEquipo: EliminarEquipoUseCase,
    private val buscarEquipo: BuscarEquipoPorCodigoUseCase,
    private val excel: ExcelService,
    private val ocr: OcrService,
) : ViewModel() {

    private val pantalla = MutableStateFlow(EstadoPantalla())
    private val todos = MutableStateFlow<List<Equipo>>(emptyList())

    val ui: StateFlow<InventarioUiState> = combine(
        repositorio.observarEquipos(),
        auth.usuario,
        pantalla,
    ) { equipos, usuario, p ->
        todos.value = equipos
        val filtrados = ordenar(filtrar(equipos, p.busqueda), p.orden)
        val totalPaginas = maxOf(1, (filtrados.size + p.tamanoPagina - 1) / p.tamanoPagina)
        // Si la lista se achica (búsqueda, eliminación) la página se ajusta a la última disponible.
        val pagina = p.pagina.coerceIn(0, totalPaginas - 1)
        InventarioUiState(
            cargando = false,
            equipos = filtrados.drop(pagina * p.tamanoPagina).take(p.tamanoPagina),
            totalFiltrados = filtrados.size,
            totalEquipos = equipos.size,
            orden = p.orden,
            pagina = pagina,
            totalPaginas = totalPaginas,
            tamanoPagina = p.tamanoPagina,
            rol = usuario?.rol ?: Rol.OTRO,
            formulario = p.formulario,
            busqueda = p.busqueda,
            buscandoEnServidor = p.buscandoEnServidor,
            mensajeBusqueda = p.mensajeBusqueda,
            analizandoImagen = p.analizandoImagen,
            analisis = p.analisis,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventarioUiState())

    private val _eventos = Channel<InventarioEvento>(Channel.BUFFERED)
    val eventos: Flow<InventarioEvento> = _eventos.receiveAsFlow()

    /** Lista a exportar mientras el usuario elige dónde guardar. */
    private var pendienteDeGuardar: List<Equipo> = emptyList()

    private val rol: Rol get() = auth.usuario.value?.rol ?: Rol.OTRO

    // ---------- Búsqueda ----------

    private fun ordenar(equipos: List<Equipo>, orden: OrdenInventario): List<Equipo> = when (orden) {
        OrdenInventario.FECHA_RECIENTE -> equipos.sortedByDescending { it.fechaRegistro }
        OrdenInventario.FECHA_ANTIGUA -> equipos.sortedBy { it.fechaRegistro }
        // Dentro del mismo artículo/marca, los más recientes primero
        OrdenInventario.ARTICULO -> equipos.sortedWith(
            compareBy<Equipo, String>(String.CASE_INSENSITIVE_ORDER) { it.articulo }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.marca }
                .thenByDescending { it.fechaRegistro }
        )
        OrdenInventario.MARCA -> equipos.sortedWith(
            compareBy<Equipo, String>(String.CASE_INSENSITIVE_ORDER) { it.marca }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.modelo }
                .thenByDescending { it.fechaRegistro }
        )
    }

    /** Coincidencia parcial, sin distinguir mayúsculas, en No. de inventario o No. de serie. */
    private fun filtrar(equipos: List<Equipo>, texto: String): List<Equipo> {
        val q = texto.trim()
        if (q.isEmpty()) return equipos
        return equipos.filter {
            it.inventario.contains(q, ignoreCase = true) || it.serie.contains(q, ignoreCase = true)
        }
    }

    fun onBusqueda(texto: String) {
        pantalla.update { it.copy(busqueda = texto, mensajeBusqueda = null, pagina = 0) }
    }

    // ---------- Orden y paginación ----------

    fun cambiarOrden(orden: OrdenInventario) = pantalla.update { it.copy(orden = orden, pagina = 0) }

    fun cambiarTamanoPagina(tamano: Int) = pantalla.update { it.copy(tamanoPagina = tamano, pagina = 0) }

    fun irAPagina(pagina: Int) {
        val total = ui.value.totalPaginas
        pantalla.update { it.copy(pagina = pagina.coerceIn(0, total - 1)) }
    }

    fun limpiarBusqueda() = onBusqueda("")

    /** El equipo no está en el dispositivo: consultar el servidor (coincidencia exacta). */
    fun buscarEnServidor() {
        val codigo = pantalla.value.busqueda.trim()
        if (codigo.isEmpty()) return
        pantalla.update { it.copy(buscandoEnServidor = true, mensajeBusqueda = null) }
        viewModelScope.launch {
            val mensaje = when (val r = buscarEquipo(codigo)) {
                // Se guarda en el dispositivo; el filtro lo mostrará solo si coincide con lo escrito.
                is ResultadoBusqueda.Encontrado ->
                    if (r.desdeServidor) {
                        pantalla.update { it.copy(busqueda = r.equipo.inventario) }
                        "Encontrado en el servidor y guardado en el dispositivo."
                    } else {
                        null
                    }
                ResultadoBusqueda.NoEncontrado -> "No hay ningún equipo registrado con ese No. de inventario o de serie."
                is ResultadoBusqueda.SoloLocal -> r.motivo
            }
            pantalla.update { it.copy(buscandoEnServidor = false, mensajeBusqueda = mensaje) }
        }
    }

    // ---------- Formulario ----------

    fun abrirNuevo() {
        pantalla.update {
            it.copy(
                formulario = FormularioEquipo(
                    inventarioOriginal = null,
                    datos = DatosEquipo(),
                    fechaRegistro = null,
                    inventarioEditable = true,
                    estadosPermitidos = PermisosInventario.estadosPermitidos(rol, null),
                )
            )
        }
    }

    fun abrirEdicion(equipo: Equipo) {
        if (equipo.estadoSync == EstadoSync.CONFLICTO) {
            _eventos.trySend(InventarioEvento.Mensaje("Este equipo tiene un conflicto. Resuélvelo en Sincronización."))
            return
        }
        pantalla.update {
            it.copy(
                formulario = FormularioEquipo(
                    inventarioOriginal = equipo.inventario,
                    datos = equipo.datos,
                    fechaRegistro = equipo.fechaRegistro,
                    inventarioEditable = !equipo.existeEnServidor,
                    estadosPermitidos = PermisosInventario.estadosPermitidos(rol, equipo.estado),
                )
            )
        }
    }

    fun cerrarFormulario() {
        pantalla.update { it.copy(formulario = null) }
    }

    fun onCambioFormulario(datos: DatosEquipo) {
        pantalla.update { p -> p.copy(formulario = p.formulario?.copy(datos = datos, error = null)) }
    }

    fun guardarFormulario() {
        val f = pantalla.value.formulario ?: return
        pantalla.update { p -> p.copy(formulario = p.formulario?.copy(guardando = true, error = null)) }
        viewModelScope.launch {
            guardarEquipo(f.inventarioOriginal, f.datos)
                .onSuccess {
                    pantalla.update { it.copy(formulario = null) }
                    _eventos.send(InventarioEvento.Mensaje("Guardado. Se sincronizará cuando haya conexión."))
                }
                .onFailure { e ->
                    pantalla.update { p -> p.copy(formulario = p.formulario?.copy(guardando = false, error = e.message)) }
                }
        }
    }

    // ---------- Escáner ----------

    /** Foto tomada desde el formulario: analizar y proponer valores para los campos. */
    fun analizarParaFormulario(uri: Uri, objetivo: ObjetivoCaptura) {
        val editable = pantalla.value.formulario?.inventarioEditable ?: true
        val campos = CampoDestino.entries.filter { editable || it != CampoDestino.INVENTARIO }
        analizar(uri, objetivo, campos, paraBusqueda = false)
    }

    /** Foto tomada desde la barra de búsqueda: el valor elegido se usa como texto de búsqueda. */
    fun analizarParaBusqueda(uri: Uri) {
        analizar(uri, ObjetivoCaptura.INVENTARIO, listOf(CampoDestino.INVENTARIO, CampoDestino.SERIE), paraBusqueda = true)
    }

    private fun analizar(uri: Uri, objetivo: ObjetivoCaptura, campos: List<CampoDestino>, paraBusqueda: Boolean) {
        pantalla.update { it.copy(analizandoImagen = true) }
        viewModelScope.launch {
            val analisis = ocr.analizarImagen(uri)
            var seleccion = analisis.seleccionInicial(objetivo, campos.toSet())
            if (paraBusqueda && seleccion.isEmpty()) {
                // Sin inventario claro: proponer la serie.
                seleccion = analisis.seleccionInicial(ObjetivoCaptura.SERIE, campos.toSet())
            }
            pantalla.update {
                it.copy(
                    analizandoImagen = false,
                    analisis = AnalisisPendiente(analisis, campos, seleccion, paraBusqueda),
                )
            }
        }
    }

    fun cancelarAnalisis() {
        pantalla.update { it.copy(analisis = null) }
    }

    /** El usuario confirmó qué valor va en cada campo. */
    fun aplicarAnalisis(asignacion: Map<CampoDestino, String>) {
        val pendiente = pantalla.value.analisis ?: return
        if (pendiente.paraBusqueda) {
            val texto = asignacion[CampoDestino.INVENTARIO] ?: asignacion[CampoDestino.SERIE] ?: ""
            pantalla.update { it.copy(analisis = null, busqueda = texto, mensajeBusqueda = null) }
            return
        }
        pantalla.update { p ->
            val f = p.formulario
            if (f == null) {
                p.copy(analisis = null)
            } else {
                val d = f.datos
                p.copy(
                    analisis = null,
                    formulario = f.copy(
                        datos = d.copy(
                            inventario = if (f.inventarioEditable) asignacion[CampoDestino.INVENTARIO] ?: d.inventario else d.inventario,
                            serie = asignacion[CampoDestino.SERIE] ?: d.serie,
                            marca = asignacion[CampoDestino.MARCA] ?: d.marca,
                            modelo = asignacion[CampoDestino.MODELO] ?: d.modelo,
                        ),
                        error = null,
                    ),
                )
            }
        }
    }

    // ---------- Eliminar ----------

    fun puedeEliminar(equipo: Equipo): Boolean = PermisosInventario.puedeEliminar(rol, equipo)

    fun eliminar(inventario: String) {
        viewModelScope.launch {
            eliminarEquipo(inventario)
                .onSuccess { _eventos.send(InventarioEvento.Mensaje("Equipo eliminado.")) }
                .onFailure { e -> _eventos.send(InventarioEvento.Mensaje(e.message ?: "No se pudo eliminar.")) }
        }
    }

    // ---------- Exportar (siempre todo el inventario, no solo el resultado de la búsqueda) ----------

    /** @param desde @param hasta fechas del DatePicker (inicio del día), o null para todos. */
    fun exportar(desde: Long?, hasta: Long?, accion: AccionExportar) {
        val lista = if (desde != null && hasta != null) {
            val finDelDia = hasta + 86_399_999L // incluir todo el día final
            todos.value.filter { it.fechaRegistro in desde..finDelDia }
        } else {
            todos.value
        }
        if (lista.isEmpty()) {
            _eventos.trySend(InventarioEvento.Mensaje("No hay registros en el rango seleccionado."))
            return
        }
        when (accion) {
            AccionExportar.GUARDAR -> {
                pendienteDeGuardar = lista
                val fecha = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.getDefault()).format(java.util.Date())
                _eventos.trySend(InventarioEvento.ElegirDestino("Inventario_$fecha.xlsx"))
            }
            AccionExportar.COMPARTIR -> viewModelScope.launch {
                try {
                    _eventos.send(InventarioEvento.Compartir(excel.crearArchivoParaCompartir(lista)))
                } catch (e: Exception) {
                    _eventos.send(InventarioEvento.Mensaje("No se pudo generar el archivo: ${e.message}"))
                }
            }
        }
    }

    fun guardarArchivoEn(uri: Uri) {
        val lista = pendienteDeGuardar
        pendienteDeGuardar = emptyList()
        viewModelScope.launch {
            try {
                excel.guardarEnUri(lista, uri)
                _eventos.send(InventarioEvento.Mensaje("Archivo guardado exitosamente."))
            } catch (e: Exception) {
                _eventos.send(InventarioEvento.Mensaje("No se pudo guardar el archivo: ${e.message}"))
            }
        }
    }
}
