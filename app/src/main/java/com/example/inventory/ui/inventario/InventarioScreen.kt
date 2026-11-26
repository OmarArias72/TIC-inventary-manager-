package com.example.inventory.ui.inventario

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventory.domain.model.Equipo
import com.example.inventory.ui.componentes.BarraSuperior
import com.example.inventory.ui.componentes.CamposEquipo
import com.example.inventory.ui.componentes.CargandoLineal
import com.example.inventory.ui.componentes.DialogoCamposDetectados
import com.example.inventory.ui.componentes.rememberCapturadorImagen
import com.example.inventory.ui.componentes.EscanerOcrCard
import com.example.inventory.ui.componentes.EtiquetaSync
import com.example.inventory.ui.componentes.colorEstadoEquipo
import com.example.inventory.ui.componentes.formatFecha

/**
 * Pantalla "Registrar inventario". Solo dibuja el estado del ViewModel y le avisa de las
 * acciones del usuario; no contiene lógica de negocio ni acceso a datos.
 */
@Composable
fun InventarioScreen(
    onAbrirMenu: () -> Unit,
    barraEstado: @Composable () -> Unit,
    viewModel: InventarioViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var inventarioAEliminar by remember { mutableStateOf<String?>(null) }
    var mostrarExportar by remember { mutableStateOf(false) }
    var mostrarBusqueda by rememberSaveable { mutableStateOf(false) }

    val elegirDestino = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) viewModel.guardarArchivoEn(uri) }

    // Eventos de una sola vez (mensajes, abrir selector de archivo, compartir).
    LaunchedEffect(Unit) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                is InventarioEvento.Mensaje -> Toast.makeText(context, evento.texto, Toast.LENGTH_SHORT).show()
                is InventarioEvento.ElegirDestino -> elegirDestino.launch(evento.nombreArchivo)
                is InventarioEvento.Compartir -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_STREAM, evento.uri)
                        putExtra(Intent.EXTRA_TEXT, "Te comparto el reporte del inventario generado desde la aplicación.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartir Inventario"))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior("Inventario con OCR", onAbrirMenu) {
                IconButton(onClick = {
                    if (mostrarBusqueda) viewModel.limpiarBusqueda()
                    mostrarBusqueda = !mostrarBusqueda
                }) {
                    Icon(
                        if (mostrarBusqueda) Icons.Default.SearchOff else Icons.Default.Search,
                        contentDescription = if (mostrarBusqueda) "Cerrar búsqueda" else "Buscar equipo",
                    )
                }
            }
        },
        bottomBar = {
            BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = viewModel::abrirNuevo,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) { Text("Agregar Fila") }
                    Button(
                        onClick = {
                            if (ui.equipos.isNotEmpty()) mostrarExportar = true
                            else Toast.makeText(context, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
                        },
                    ) { Text("Exportar a XLSX") }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            barraEstado()
            if (mostrarBusqueda) {
                BarraBusqueda(
                    texto = ui.busqueda,
                    resultados = ui.equipos.size,
                    total = ui.totalEquipos,
                    analizando = ui.analizandoImagen,
                    onTexto = viewModel::onBusqueda,
                    onLimpiar = viewModel::limpiarBusqueda,
                    onImagen = viewModel::analizarParaBusqueda,
                )
            }
            when {
                ui.cargando -> CargandoLineal(true)
                ui.busqueda.isNotBlank() && ui.equipos.isEmpty() -> SinResultados(
                    texto = ui.busqueda,
                    buscando = ui.buscandoEnServidor,
                    mensaje = ui.mensajeBusqueda,
                    onBuscarEnServidor = viewModel::buscarEnServidor,
                )
                ui.equipos.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No hay registros en la tabla.\nPresiona 'Agregar Fila' para comenzar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    BarraOrden(
                        orden = ui.orden,
                        total = ui.totalFiltrados,
                        onOrden = viewModel::cambiarOrden,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        TablaInventario(
                            items = ui.equipos,
                            puedeEliminar = viewModel::puedeEliminar,
                            onEliminar = { inventarioAEliminar = it },
                            onEditar = viewModel::abrirEdicion,
                        )
                    }
                    Paginacion(
                        pagina = ui.pagina,
                        totalPaginas = ui.totalPaginas,
                        tamanoPagina = ui.tamanoPagina,
                        totalFiltrados = ui.totalFiltrados,
                        onPagina = viewModel::irAPagina,
                        onTamano = viewModel::cambiarTamanoPagina,
                    )
                }
            }
        }
    }

    ui.formulario?.let { form ->
        DialogoEquipo(
            formulario = form,
            analizando = ui.analizandoImagen,
            onCambio = viewModel::onCambioFormulario,
            onImagen = viewModel::analizarParaFormulario,
            onGuardar = viewModel::guardarFormulario,
            onCerrar = viewModel::cerrarFormulario,
        )
    }

    // Resultado del escáner: elegir qué valor va en cada campo
    ui.analisis?.let { pendiente ->
        DialogoCamposDetectados(
            analisis = pendiente.analisis,
            camposPermitidos = pendiente.camposPermitidos,
            seleccionInicial = pendiente.seleccionInicial,
            onAplicar = viewModel::aplicarAnalisis,
            onCancelar = viewModel::cancelarAnalisis,
            textoBoton = if (pendiente.paraBusqueda) "Buscar" else "Aplicar",
        )
    }

    inventarioAEliminar?.let { inventario ->
        AlertDialog(
            onDismissRequest = { inventarioAEliminar = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de eliminar el registro con inventario '$inventario'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.eliminar(inventario)
                        inventarioAEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { inventarioAEliminar = null }) { Text("Cancelar") } },
        )
    }

    if (mostrarExportar) {
        DialogoExportar(
            onDismiss = { mostrarExportar = false },
            onExportar = { desde, hasta, accion ->
                mostrarExportar = false
                viewModel.exportar(desde, hasta, accion)
            },
        )
    }
}

@Composable
private fun DialogoEquipo(
    formulario: FormularioEquipo,
    analizando: Boolean,
    onCambio: (com.example.inventory.domain.model.DatosEquipo) -> Unit,
    onImagen: (android.net.Uri, com.example.inventory.domain.model.ObjetivoCaptura) -> Unit,
    onGuardar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val ocupado = analizando || formulario.guardando
    AlertDialog(
        onDismissRequest = { if (!ocupado) onCerrar() },
        title = { Text(if (formulario.esEdicion) "Editar Registro" else "Nuevo Registro", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CargandoLineal(ocupado)
                // También al editar: permite capturar la serie (el inventario no cambia si ya está en el servidor).
                EscanerOcrCard(
                    habilitado = !ocupado,
                    onImagenLista = onImagen,
                    objetivoInicial = if (formulario.inventarioEditable) {
                        com.example.inventory.domain.model.ObjetivoCaptura.INVENTARIO
                    } else {
                        com.example.inventory.domain.model.ObjetivoCaptura.SERIE
                    },
                )
                if (analizando) Text("Analizando imagen…", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                CamposEquipo(
                    datos = formulario.datos,
                    onCambio = onCambio,
                    estadosPermitidos = formulario.estadosPermitidos,
                    habilitado = !ocupado,
                    inventarioEditable = formulario.inventarioEditable,
                )
                formulario.fechaRegistro?.takeIf { it > 0 }?.let { fecha ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Fecha de registro:", style = MaterialTheme.typography.bodyMedium)
                            Text(formatFecha(fecha), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                formulario.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = onGuardar, enabled = !ocupado) {
                Text(if (formulario.esEdicion) "Actualizar" else "Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onCerrar, enabled = !ocupado) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoExportar(
    onDismiss: () -> Unit,
    onExportar: (Long?, Long?, AccionExportar) -> Unit,
) {
    val context = LocalContext.current
    var filtrarPorFecha by remember { mutableStateOf(false) }
    var fechaInicio by remember { mutableStateOf<Long?>(null) }
    var fechaFin by remember { mutableStateOf<Long?>(null) }

    fun exportar(accion: AccionExportar) {
        if (filtrarPorFecha) {
            val inicio = fechaInicio
            val fin = fechaFin
            if (inicio == null || fin == null) {
                Toast.makeText(context, "Selecciona el rango de fechas", Toast.LENGTH_SHORT).show()
                return
            }
            if (inicio > fin) {
                Toast.makeText(context, "La fecha inicio no puede ser mayor", Toast.LENGTH_SHORT).show()
                return
            }
            onExportar(inicio, fin, accion)
        } else {
            onExportar(null, null, accion)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar Inventario", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Selecciona los registros a exportar:")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = !filtrarPorFecha, onClick = { filtrarPorFecha = false })
                    Text("Todos los registros")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = filtrarPorFecha, onClick = { filtrarPorFecha = true })
                    Text("Filtrar por rango de fecha")
                }
                if (filtrarPorFecha) {
                    SelectorFecha("Fecha Inicio (Desde)", fechaInicio) { fechaInicio = it }
                    SelectorFecha("Fecha Fin (Hasta)", fechaFin) { fechaFin = it }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportar(AccionExportar.GUARDAR) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Guardar en dispositivo")
                }
                Button(
                    onClick = { exportar(AccionExportar.COMPARTIR) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Compartir archivo") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFecha(label: String, fechaMilis: Long?, onFechaSeleccionada: (Long) -> Unit) {
    var mostrarDialogo by remember { mutableStateOf(false) }
    val estado = rememberDatePickerState(initialSelectedDateMillis = fechaMilis ?: System.currentTimeMillis())
    if (mostrarDialogo) {
        DatePickerDialog(
            onDismissRequest = { mostrarDialogo = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let(onFechaSeleccionada)
                    mostrarDialogo = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { mostrarDialogo = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = estado)
        }
    }
    OutlinedButton(onClick = { mostrarDialogo = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (fechaMilis != null) formatFecha(fechaMilis) else label)
    }
}

// ---------------- Búsqueda ----------------

/** Campo de búsqueda por No. de inventario o No. de serie, con opción de escanear la etiqueta. */
@Composable
private fun BarraBusqueda(
    texto: String,
    resultados: Int,
    total: Int,
    analizando: Boolean,
    onTexto: (String) -> Unit,
    onLimpiar: () -> Unit,
    onImagen: (android.net.Uri) -> Unit,
) {
    // Para buscar se analiza la foto completa (sin recorte) para que sea rápido.
    val capturador = rememberCapturadorImagen(recortar = false, onImagen = onImagen)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = texto,
                onValueChange = onTexto,
                label = { Text("Buscar por No. de inventario o de serie") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (texto.isNotEmpty()) {
                            IconButton(onClick = onLimpiar) { Icon(Icons.Default.Clear, contentDescription = "Limpiar") }
                        }
                        IconButton(onClick = capturador.abrirCamara, enabled = !analizando) {
                            if (analizando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.CameraAlt, contentDescription = "Escanear etiqueta para buscar")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth(),
            )
            if (texto.isNotBlank()) {
                Text(
                    "$resultados de $total equipos",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** No hay coincidencias en el dispositivo: ofrecer buscar en el servidor. */
@Composable
private fun SinResultados(
    texto: String,
    buscando: Boolean,
    mensaje: String?,
    onBuscarEnServidor: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No hay equipos en el dispositivo con \"$texto\".", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onBuscarEnServidor, enabled = !buscando) {
            if (buscando) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("Buscar en el servidor")
            }
        }
        mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

// ---------------- Orden y paginación ----------------

/** "Ordenar por: [criterio ▾]" y el total de registros. */
@Composable
private fun BarraOrden(orden: OrdenInventario, total: Int, onOrden: (OrdenInventario) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$total registro(s)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Box {
            AssistChip(
                onClick = { abierto = true },
                label = { Text(orden.etiqueta) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar por", modifier = Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                OrdenInventario.entries.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(opcion.etiqueta, fontWeight = if (opcion == orden) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            onOrden(opcion)
                            abierto = false
                        },
                    )
                }
            }
        }
    }
}

/** Controles de página: tamaño de página, "1–20 de 135" y botones primera/anterior/siguiente/última. */
@Composable
private fun Paginacion(
    pagina: Int,
    totalPaginas: Int,
    tamanoPagina: Int,
    totalFiltrados: Int,
    onPagina: (Int) -> Unit,
    onTamano: (Int) -> Unit,
) {
    var menuTamano by remember { mutableStateOf(false) }
    val desde = if (totalFiltrados == 0) 0 else pagina * tamanoPagina + 1
    val hasta = minOf((pagina + 1) * tamanoPagina, totalFiltrados)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { menuTamano = true }) { Text("$tamanoPagina por página") }
                DropdownMenu(expanded = menuTamano, onDismissRequest = { menuTamano = false }) {
                    TAMANOS_PAGINA.forEach { n ->
                        DropdownMenuItem(
                            text = { Text("$n por página") },
                            onClick = {
                                onTamano(n)
                                menuTamano = false
                            },
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Página ${pagina + 1} de $totalPaginas", style = MaterialTheme.typography.labelLarge)
                Text("$desde–$hasta de $totalFiltrados", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { onPagina(0) }, enabled = pagina > 0) {
                Icon(Icons.Default.FirstPage, contentDescription = "Primera página")
            }
            IconButton(onClick = { onPagina(pagina - 1) }, enabled = pagina > 0) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Página anterior")
            }
            IconButton(onClick = { onPagina(pagina + 1) }, enabled = pagina < totalPaginas - 1) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Página siguiente")
            }
            IconButton(onClick = { onPagina(totalPaginas - 1) }, enabled = pagina < totalPaginas - 1) {
                Icon(Icons.Default.LastPage, contentDescription = "Última página")
            }
        }
    }
}

// ---------------- Tabla ----------------
private val ANCHO_NO_INVENTARIO = 110.dp
private val ANCHO_NO_SERIE = 120.dp
private val ANCHO_ARTICULO = 110.dp
private val ANCHO_MARCA = 100.dp
private val ANCHO_MODELO = 100.dp
private val ANCHO_OBSERVACION = 160.dp
private val ANCHO_ESTADO = 110.dp
private val ANCHO_FECHA = 100.dp
private val ANCHO_SYNC = 130.dp
private val ANCHO_ACCION = 160.dp

@Composable
private fun TablaInventario(
    items: List<Equipo>,
    puedeEliminar: (Equipo) -> Boolean,
    onEliminar: (String) -> Unit,
    onEditar: (Equipo) -> Unit,
) {
    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(vertical = 10.dp),
        ) {
            CeldaTabla("No. Inventario", ANCHO_NO_INVENTARIO, esEncabezado = true)
            CeldaTabla("No. Serie", ANCHO_NO_SERIE, esEncabezado = true)
            CeldaTabla("Artículo", ANCHO_ARTICULO, esEncabezado = true)
            CeldaTabla("Marca", ANCHO_MARCA, esEncabezado = true)
            CeldaTabla("Modelo", ANCHO_MODELO, esEncabezado = true)
            CeldaTabla("Observaciones", ANCHO_OBSERVACION, esEncabezado = true)
            CeldaTabla("Estado", ANCHO_ESTADO, esEncabezado = true)
            CeldaTabla("Fecha", ANCHO_FECHA, esEncabezado = true)
            CeldaTabla("Sincronización", ANCHO_SYNC, esEncabezado = true)
            CeldaTabla("Acciones", ANCHO_ACCION, esEncabezado = true)
        }
        HorizontalDivider()
        LazyColumn {
            itemsIndexed(items, key = { _, item -> item.inventario }) { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.background(
                        if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    CeldaTabla(item.inventario, ANCHO_NO_INVENTARIO)
                    CeldaTabla(item.serie, ANCHO_NO_SERIE)
                    CeldaTabla(item.articulo, ANCHO_ARTICULO)
                    CeldaTabla(item.marca, ANCHO_MARCA)
                    CeldaTabla(item.modelo, ANCHO_MODELO)
                    CeldaTabla(item.caracteristicas, ANCHO_OBSERVACION)
                    Text(
                        text = item.estado.name,
                        color = colorEstadoEquipo(item.estado, MaterialTheme.colorScheme),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(ANCHO_ESTADO)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    CeldaTabla(formatFecha(item.fechaRegistro), ANCHO_FECHA)
                    Box(modifier = Modifier.width(ANCHO_SYNC).padding(horizontal = 8.dp)) {
                        EtiquetaSync(item.estadoSync)
                    }
                    Row(
                        modifier = Modifier.width(ANCHO_ACCION),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onEditar(item) }) {
                            Text("Editar", color = MaterialTheme.colorScheme.primary)
                        }
                        if (puedeEliminar(item)) {
                            TextButton(onClick = { onEliminar(item.inventario) }) {
                                Text("Borrar", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CeldaTabla(texto: String, ancho: Dp, esEncabezado: Boolean = false) {
    Text(
        text = texto,
        modifier = Modifier
            .width(ancho)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        fontWeight = if (esEncabezado) FontWeight.Bold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = if (esEncabezado) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    )
}
