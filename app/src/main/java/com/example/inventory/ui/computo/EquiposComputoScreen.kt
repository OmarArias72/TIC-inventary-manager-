package com.example.inventory.ui.computo

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventory.domain.model.CatalogosComputo
import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.EquipoComputo
import com.example.inventory.domain.model.ObjetivoCaptura
import com.example.inventory.domain.model.Sala
import com.example.inventory.ui.componentes.BarraSuperior
import com.example.inventory.ui.componentes.CamposEquipo
import com.example.inventory.ui.componentes.CargandoLineal
import com.example.inventory.ui.componentes.DialogoCamposDetectados
import com.example.inventory.ui.componentes.EscanerOcrCard
import com.example.inventory.ui.componentes.EtiquetaSync
import com.example.inventory.ui.componentes.colorEstadoEquipo

/**
 * "Actualizar equipos de cómputo":
 * 1. Elegir la sala.
 * 2. Elegir un equipo de la sala para actualizarlo, o registrar uno nuevo en ella.
 */
@Composable
fun EquiposComputoScreen(
    onAbrirMenu: () -> Unit,
    barraEstado: @Composable () -> Unit,
    viewModel: EquiposComputoViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.mensajes.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    val formulario = ui.formulario
    if (formulario != null) {
        BackHandler { viewModel.cerrarFormulario() }
        FormularioComputoScreen(
            form = formulario,
            salas = ui.salas,
            analizandoImagen = ui.analizandoImagen,
            viewModel = viewModel,
        )
    } else {
        ListaComputoScreen(ui = ui, onAbrirMenu = onAbrirMenu, barraEstado = barraEstado, viewModel = viewModel)
    }

    ui.analisis?.let { analisis ->
        DialogoCamposDetectados(
            analisis = analisis,
            camposPermitidos = ui.camposAnalisis,
            seleccionInicial = ui.seleccionAnalisis,
            onAplicar = viewModel::aplicarAnalisis,
            onCancelar = viewModel::cancelarAnalisis,
        )
    }
}

// ======================= Paso 1 y 2: sala y lista =======================

@Composable
private fun ListaComputoScreen(
    ui: ComputoUiState,
    onAbrirMenu: () -> Unit,
    barraEstado: @Composable () -> Unit,
    viewModel: EquiposComputoViewModel,
) {
    Scaffold(
        topBar = { BarraSuperior("Equipos de cómputo", onAbrirMenu) },
        floatingActionButton = {
            if (ui.filtro != null) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::abrirNuevo,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Registrar equipo") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            barraEstado()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Selecciona la sala", fontWeight = FontWeight.Bold)
                if (ui.salas.isEmpty()) {
                    Text(
                        "No hay salas descargadas. Conéctate y sincroniza, o agrega una sala en el dispositivo " +
                            "con la opción \"Agregar sala…\" de la lista.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = viewModel::sincronizar) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Text("  Sincronizar")
                    }
                }
                SelectorSala(
                    salas = ui.salas,
                    seleccion = ui.filtro,
                    onSeleccion = viewModel::seleccionarSala,
                    incluirSinSala = true,
                    onCrearSala = { c, n, u -> viewModel.crearSalaLocal(c, n, u) { viewModel.seleccionarSala(FiltroSala(it.id)) } },
                )
            }

            if (ui.filtro == null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Elige una sala para ver sus equipos de cómputo o registrar uno nuevo.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ListaDeSala(ui = ui, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ListaDeSala(ui: ComputoUiState, viewModel: EquiposComputoViewModel) {
    Column {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2. Elige el equipo (${ui.totalEnSala} en la sala)", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = ui.busqueda,
                onValueChange = viewModel::onBusqueda,
                label = { Text("Buscar por No. de inventario o serie") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (ui.equipos.isEmpty()) {
            Text(
                if (ui.totalEnSala == 0) "Esta sala no tiene equipos registrados. Usa \"Registrar equipo\"."
                else "Ningún equipo coincide con la búsqueda.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.equipos, key = { it.inventario }) { equipo ->
                TarjetaComputo(equipo = equipo, onClick = { viewModel.abrirEdicion(equipo) })
            }
        }
    }
}

@Composable
private fun TarjetaComputo(equipo: EquipoComputo, onClick: () -> Unit) {
    val d = equipo.datos
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(equipo.inventario, fontWeight = FontWeight.Bold)
                    EtiquetaSync(equipo.estadoSync)
                }
                Text("${d.base.marca} ${d.base.modelo}".trim(), style = MaterialTheme.typography.bodySmall)
                val hardware = listOf(d.procesador, d.ram, d.almacenamiento).filter { it.isNotBlank() }.joinToString(" · ")
                if (hardware.isNotEmpty()) Text(hardware, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.base.estado.name, color = colorEstadoEquipo(d.base.estado, MaterialTheme.colorScheme), style = MaterialTheme.typography.labelMedium)
                    if (d.estadoComputadora.isNotBlank()) Text(d.estadoComputadora, style = MaterialTheme.typography.labelMedium)
                }
                equipo.ultimoError?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Selector de sala. seleccion = null → no se ha elegido; FiltroSala(null) → "Sin sala asignada". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorSala(
    salas: List<Sala>,
    seleccion: FiltroSala?,
    onSeleccion: (FiltroSala) -> Unit,
    incluirSinSala: Boolean,
    etiqueta: String = "Sala",
    habilitado: Boolean = true,
    onCrearSala: ((codigo: String, nombre: String, ubicacion: String) -> Unit)? = null,
) {
    var abierto by remember { mutableStateOf(false) }
    var nuevaSala by remember { mutableStateOf(false) }
    if (nuevaSala && onCrearSala != null) {
        DialogoNuevaSala(
            onCrear = { c, n, u ->
                onCrearSala(c, n, u)
                nuevaSala = false
            },
            onCancelar = { nuevaSala = false },
        )
    }
    val texto = when {
        seleccion == null -> ""
        seleccion.idSala == null -> "Sin sala asignada"
        else -> salas.firstOrNull { it.id == seleccion.idSala }?.etiqueta ?: "Sala ${seleccion.idSala}"
    }
    ExposedDropdownMenuBox(expanded = abierto && habilitado, onExpandedChange = { if (habilitado) abierto = it }) {
        OutlinedTextField(
            value = texto,
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            placeholder = { Text("Elige una sala") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            enabled = habilitado,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = abierto && habilitado, onDismissRequest = { abierto = false }) {
            salas.forEach { sala ->
                DropdownMenuItem(
                    text = { Text(sala.etiqueta) },
                    onClick = {
                        onSeleccion(FiltroSala(sala.id))
                        abierto = false
                    },
                )
            }
            if (incluirSinSala) {
                DropdownMenuItem(
                    text = { Text("Sin sala asignada") },
                    onClick = {
                        onSeleccion(FiltroSala(null))
                        abierto = false
                    },
                )
            }
            if (onCrearSala != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Agregar sala…", color = MaterialTheme.colorScheme.primary) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        abierto = false
                        nuevaSala = true
                    },
                )
            }
        }
    }
}

/** Alta de una sala solo en el dispositivo (para trabajar antes de que exista el servidor). */
@Composable
private fun DialogoNuevaSala(
    onCrear: (codigo: String, nombre: String, ubicacion: String) -> Unit,
    onCancelar: () -> Unit,
) {
    var codigo by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Agregar sala") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se guarda solo en este dispositivo. Al sincronizar se empareja por código con las salas del " +
                        "servidor; si no existe allá, el administrador debe crearla con el mismo código.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value = codigo, onValueChange = { codigo = it }, label = { Text("Código * (ej. LAB-1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ubicacion, onValueChange = { ubicacion = it }, label = { Text("Ubicación") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onCrear(codigo, nombre, ubicacion) }, enabled = codigo.isNotBlank()) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

// ======================= Formulario (registrar / actualizar) =======================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormularioComputoScreen(
    form: FormularioComputo,
    salas: List<Sala>,
    analizandoImagen: Boolean,
    viewModel: EquiposComputoViewModel,
) {
    val d = form.datos
    val ocupado = form.guardando || analizandoImagen || form.buscandoEnInventario
    val cambiar: (DatosComputo) -> Unit = viewModel::onCambio

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.esEdicion) "Actualizar ${form.inventarioOriginal}" else "Registrar equipo de cómputo") },
                navigationIcon = {
                    IconButton(onClick = viewModel::cerrarFormulario) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CargandoLineal(ocupado)
            form.actualizadoPor?.let {
                Text("Última actualización por: $it", style = MaterialTheme.typography.bodySmall)
            }

            EscanerOcrCard(
                habilitado = !ocupado,
                onImagenLista = viewModel::analizarImagen,
                objetivoInicial = if (form.inventarioEditable) ObjetivoCaptura.INVENTARIO else ObjetivoCaptura.SERIE,
            )
            if (analizandoImagen) Text("Analizando imagen…", style = MaterialTheme.typography.bodySmall)

            Seccion("Equipo")
            CamposEquipo(
                datos = d.base,
                onCambio = { cambiar(d.copy(base = it)) },
                estadosPermitidos = form.estadosPermitidos,
                habilitado = !ocupado,
                inventarioEditable = form.inventarioEditable,
                accionInventario = if (!form.esEdicion) {
                    {
                        IconButton(onClick = viewModel::buscarEnInventarioGeneral, enabled = !ocupado) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar en el inventario general")
                        }
                    }
                } else null,
            )
            form.mensaje?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            SelectorSala(
                salas = salas,
                seleccion = FiltroSala(d.idSala),
                onSeleccion = { cambiar(d.copy(idSala = it.idSala)) },
                incluirSinSala = false,
                etiqueta = "Sala *",
                habilitado = !ocupado,
                onCrearSala = { c, n, u -> viewModel.crearSalaLocal(c, n, u) { sala -> cambiar(d.copy(idSala = sala.id)) } },
            )
            if (salas.firstOrNull { it.id == d.idSala }?.esLocal == true) {
                Text(
                    "Sala creada en el dispositivo: al sincronizar se usará la sala del servidor con el mismo código.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Seccion("Hardware y software")
            Campo("Procesador", d.procesador, !ocupado) { cambiar(d.copy(procesador = it)) }
            Campo("RAM (ej. 8 GB)", d.ram, !ocupado) { cambiar(d.copy(ram = it)) }
            Campo("Almacenamiento (ej. SSD 256 GB)", d.almacenamiento, !ocupado) { cambiar(d.copy(almacenamiento = it)) }
            Campo("Sistema operativo", d.sistemaOperativo, !ocupado) { cambiar(d.copy(sistemaOperativo = it)) }
            Campo("Especificaciones de hardware", d.especificacionesHardware, !ocupado, multilinea = true) {
                cambiar(d.copy(especificacionesHardware = it))
            }
            Campo("Software instalado", d.softwareInstalado, !ocupado, multilinea = true) {
                cambiar(d.copy(softwareInstalado = it))
            }

            Seccion("Estado de los componentes")
            SelectorOpcion("Computadora", d.estadoComputadora, CatalogosComputo.ESTADO_COMPUTADORA, !ocupado) { cambiar(d.copy(estadoComputadora = it)) }
            SelectorOpcion("CPU", d.estadoCpu, CatalogosComputo.ESTADO_CPU, !ocupado) { cambiar(d.copy(estadoCpu = it)) }
            SelectorOpcion("Monitor", d.estadoMonitor, CatalogosComputo.ESTADO_MONITOR, !ocupado) { cambiar(d.copy(estadoMonitor = it)) }
            SelectorOpcion("Teclado", d.estadoTeclado, CatalogosComputo.ESTADO_TECLADO, !ocupado) { cambiar(d.copy(estadoTeclado = it)) }
            SelectorOpcion("Mouse", d.estadoMouse, CatalogosComputo.ESTADO_MOUSE, !ocupado) { cambiar(d.copy(estadoMouse = it)) }
            SelectorOpcion("Cable Ethernet", d.estadoCableEthernet, CatalogosComputo.ESTADO_ETHERNET, !ocupado) { cambiar(d.copy(estadoCableEthernet = it)) }
            Campo("Observaciones técnicas", d.observaciones, !ocupado, multilinea = true) { cambiar(d.copy(observaciones = it)) }

            Seccion("Contraseña del equipo")
            SeccionContrasena(form = form, habilitado = !ocupado, viewModel = viewModel)

            Seccion("Bitácora")
            Campo("Motivo del cambio (ej. \"Se cambió el mouse\")", form.motivo, !ocupado, multilinea = true, onCambio = viewModel::onMotivo)

            form.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::guardar, enabled = !ocupado, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (form.esEdicion) "Guardar cambios" else "Registrar equipo")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SeccionContrasena(form: FormularioComputo, habilitado: Boolean, viewModel: EquiposComputoViewModel) {
    var ver by remember { mutableStateOf(false) }
    Text(
        when {
            form.cambioContrasenaPendiente -> "Hay un cambio de contraseña guardado que falta sincronizar."
            form.tieneContrasena -> "El equipo tiene contraseña registrada."
            else -> "El equipo no tiene contraseña registrada."
        },
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = form.contrasenaNueva,
        onValueChange = viewModel::onContrasenaNueva,
        label = { Text(if (form.tieneContrasena) "Nueva contraseña (vacío = no cambiar)" else "Contraseña (opcional)") },
        singleLine = true,
        enabled = habilitado && !form.borrarContrasena,
        visualTransformation = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { ver = !ver }) {
                Icon(if (ver) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Mostrar u ocultar")
            }
        },
        supportingText = { Text("La anterior se conserva como \"última contraseña\" en el servidor.") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (form.tieneContrasena) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.borrarContrasena, onCheckedChange = viewModel::onBorrarContrasena, enabled = habilitado)
            Text("Quitar la contraseña", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (form.puedeVerContrasenas) {
        OutlinedButton(onClick = viewModel::verContrasenas, enabled = habilitado && !form.cargandoContrasenas) {
            if (form.cargandoContrasenas) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            else Text("Ver contraseñas (requiere conexión, queda en la bitácora)")
        }
    }
    form.contrasenas?.let { c ->
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Actual: ${c.actual ?: "—"}", fontWeight = FontWeight.Medium)
                Text("Última: ${c.ultima ?: "—"}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Seccion(titulo: String) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(titulo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Campo(
    etiqueta: String,
    valor: String,
    habilitado: Boolean,
    multilinea: Boolean = false,
    onCambio: (String) -> Unit,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta) },
        singleLine = !multilinea,
        minLines = if (multilinea) 2 else 1,
        enabled = habilitado,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Lista desplegable de un catálogo, con opción "Sin especificar" (se envía vacío). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorOpcion(
    etiqueta: String,
    valor: String,
    opciones: List<String>,
    habilitado: Boolean,
    onSeleccion: (String) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = abierto && habilitado, onExpandedChange = { if (habilitado) abierto = it }) {
        OutlinedTextField(
            value = valor.ifBlank { "Sin especificar" },
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            enabled = habilitado,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = abierto && habilitado, onDismissRequest = { abierto = false }) {
            (listOf("") + opciones).forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.ifBlank { "Sin especificar" }) },
                    onClick = {
                        onSeleccion(opcion)
                        abierto = false
                    },
                )
            }
        }
    }
}
