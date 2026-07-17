package com.example.inventory

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun crearUriParaCamara(context: Context): Uri {
    val file = File(context.cacheDir, "captura_ocr_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

class InventarioViewModel(private val repository: DatabaseRepository) : ViewModel() {
    var filas by mutableStateOf<List<ItemInventario>>(emptyList())
        private set

    init { cargarDatos() }

    fun cargarDatos() {
        viewModelScope.launch { filas = repository.obtenerTodos() }
    }

    fun guardarFila(item: ItemInventario) {
        viewModelScope.launch {
            if (item.id == 0) repository.insertarItem(item) else repository.actualizarItem(item)
            cargarDatos()
        }
    }

    fun eliminarFila(id: Int) {
        viewModelScope.launch {
            repository.eliminarItem(id)
            cargarDatos()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventarioScreen(viewModel: InventarioViewModel) {
    val context = LocalContext.current
    val excelService = remember { ExcelService(context) }

    var mostrarDialogo by remember { mutableStateOf(false) }
    var itemSeleccionado by remember { mutableStateOf<ItemInventario?>(null) }
    var idAEliminar by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inventario con Escáner OCR") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = {
                        itemSeleccionado = ItemInventario()
                        mostrarDialogo = true
                    }) { Text("Agregar Fila") }
                    Button(onClick = { excelService.exportarInventario(viewModel.filas) }) { Text("Exportar a XLSX") }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (viewModel.filas.isEmpty()) {
                Text("No hay registros en la tabla.", modifier = Modifier.padding(16.dp))
            } else {
                TablaInventario(
                    items = viewModel.filas,
                    onEliminar = { idAEliminar = it },
                    onEditar = { item ->
                        itemSeleccionado = item.copy()
                        mostrarDialogo = true
                    }
                )
            }
        }

        if (mostrarDialogo && itemSeleccionado != null) {
            DialogoInventario(
                itemInicial = itemSeleccionado!!,
                onDismiss = { mostrarDialogo = false },
                onGuardar = { itemGuardado ->
                    viewModel.guardarFila(itemGuardado)
                    mostrarDialogo = false
                }
            )
        }

        if (idAEliminar != null) {
            AlertDialog(
                onDismissRequest = { idAEliminar = null },
                title = { Text("Confirmar eliminación") },
                text = { Text("¿Estás seguro de que deseas eliminar este registro?") },
                confirmButton = {
                    Button(onClick = { viewModel.eliminarFila(idAEliminar!!); idAEliminar = null }) { Text("Eliminar") }
                },
                dismissButton = {
                    TextButton(onClick = { idAEliminar = null }) { Text("Cancelar") }
                }
            )
        }
    }
}

@Composable
fun DialogoInventario(
    itemInicial: ItemInventario,
    onDismiss: () -> Unit,
    onGuardar: (ItemInventario) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ocrService = remember { OcrService() }

    var noInventario by remember { mutableStateOf(itemInicial.noInventario) }
    var noSerie by remember { mutableStateOf(itemInicial.noSerie) }
    var articulo by remember { mutableStateOf(itemInicial.articulo) } // NUEVO ESTADO
    var marca by remember { mutableStateOf(itemInicial.marca) }
    var modelo by remember { mutableStateOf(itemInicial.modelo) }
    var ubicacion by remember { mutableStateOf(itemInicial.ubicacion) }
    var observacion by remember { mutableStateOf(itemInicial.observacion) }

    var deteccionAutomatica by remember { mutableStateOf(false) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    // ESTADO PARA MOSTRAR LA NUEVA PANTALLA DE BÚSQUEDA
    var mostrarPantallaBusqueda by remember { mutableStateOf(false) }

    val procesarImagenExtraida = { uri: Uri ->
        coroutineScope.launch {
            val datos = ocrService.procesarImagen(context, uri)
            if (!datos["codigo_barras"].isNullOrEmpty()) noInventario = datos["codigo_barras"]!!
            if (!datos["numero_serie"].isNullOrEmpty()) noSerie = datos["numero_serie"]!!
        }
    }

    val cropImageLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) result.uriContent?.let { procesarImagenExtraida(it) }
    }
    val automaticCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && imageUri != null) procesarImagenExtraida(imageUri!!)
    }
    val automaticGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) procesarImagenExtraida(uri)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            if (deteccionAutomatica) {
                val uri = crearUriParaCamara(context)
                imageUri = uri
                automaticCameraLauncher.launch(uri)
            } else {
                cropImageLauncher.launch(CropImageContractOptions(uri = null, cropImageOptions = CropImageOptions(imageSourceIncludeGallery = false, imageSourceIncludeCamera = true)))
            }
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
        }
    }

    if (mostrarPantallaBusqueda) {
        PantallaBusquedaServidor(
            onDismiss = { mostrarPantallaBusqueda = false },
            onSeleccionar = { selSerie, selArticulo, selMarca, selModelo ->
                noSerie = selSerie
                articulo = selArticulo
                marca = selMarca
                modelo = selModelo
                mostrarPantallaBusqueda = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemInicial.id == 0) "Nuevo Registro" else "Editar Registro") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón para abrir búsqueda en BD
                Button(
                    onClick = { mostrarPantallaBusqueda = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Buscar Catálogo / BD Servidor")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small).padding(4.dp)
                ) {
                    Checkbox(checked = deteccionAutomatica, onCheckedChange = { deteccionAutomatica = it })
                    Text("Detección Automática (Sin Recorte)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = noInventario, onValueChange = { noInventario = it }, label = { Text("No. Inventario") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            if (deteccionAutomatica) {
                                val uri = crearUriParaCamara(context)
                                imageUri = uri
                                automaticCameraLauncher.launch(uri)
                            } else {
                                cropImageLauncher.launch(CropImageContractOptions(uri = null, cropImageOptions = CropImageOptions(imageSourceIncludeGallery = false, imageSourceIncludeCamera = true)))
                            }
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) { Icon(Icons.Default.CameraAlt, contentDescription = "Tomar foto") }
                    IconButton(onClick = {
                        if (deteccionAutomatica) automaticGalleryLauncher.launch("image/*")
                        else cropImageLauncher.launch(CropImageContractOptions(uri = null, cropImageOptions = CropImageOptions(imageSourceIncludeGallery = true, imageSourceIncludeCamera = false)))
                    }) { Icon(Icons.Default.Image, contentDescription = "Elegir galería") }
                }

                OutlinedTextField(value = noSerie, onValueChange = { noSerie = it }, label = { Text("No. Serie") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = articulo, onValueChange = { articulo = it }, label = { Text("Artículo") }, singleLine = true, modifier = Modifier.fillMaxWidth()) // NUEVO CAMPO
                OutlinedTextField(value = marca, onValueChange = { marca = it }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ubicacion, onValueChange = { ubicacion = it }, label = { Text("Ubicación") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = observacion, onValueChange = { observacion = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onGuardar(itemInicial.copy(
                    noInventario = noInventario,
                    noSerie = noSerie,
                    articulo = articulo,
                    marca = marca,
                    modelo = modelo,
                    ubicacion = ubicacion,
                    observacion = observacion
                ))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// -- NUEVA PANTALLA DE BÚSQUEDA A PANTALLA COMPLETA --
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaBusquedaServidor(
    onDismiss: () -> Unit,
    onSeleccionar: (serie: String, articulo: String, marca: String, modelo: String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resultados by remember { mutableStateOf<List<Sugerencia>>(emptyList()) }
    var errorMsj by remember { mutableStateOf("") }

    // Estados para el Dropdown (Menú de selección)
    var expandedDropdown by remember { mutableStateOf(false) }
    val opcionesBusqueda = listOf("Número de Serie", "Marca", "Modelo", "Artículo")
    var tipoBusqueda by remember { mutableStateOf(opcionesBusqueda[0]) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val realizarBusqueda: () -> Unit = {
        if (query.isNotBlank()) {
            coroutineScope.launch {
                isLoading = true
                errorMsj = ""
                try {
                    // Petición al servidor dirigida según el tipo de búsqueda
                    val response = withContext(Dispatchers.IO) {
                        val request = BusquedaRequest(query)
                        val apiService = ApiClient.getService()

                        when (tipoBusqueda) {
                            "Marca" -> apiService.buscarPorMarca(request)
                            "Modelo" -> apiService.buscarPorModelo(request)
                            "Artículo" -> apiService.buscarPorArticulo(request)
                            else -> apiService.buscarPorSerie(request) // Por defecto: "Número de Serie"
                        }
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val equipos = response.body()!!

                        if (equipos.isNotEmpty()) {
                            // Mapear los resultados anidados de Spring Boot al formato de UI
                            resultados = equipos.map { equipo ->
                                Sugerencia(
                                    inventario = equipo.inventario,
                                    serie = equipo.serie ?: (if (tipoBusqueda == "Número de Serie") query else "N/A"),
                                    articulo = equipo.articulo?.nombreArticulo,
                                    marca = equipo.modelo?.marca?.nombreMarca,
                                    modelo = equipo.modelo?.nombreModelo
                                )
                            }
                        } else {
                            resultados = emptyList()
                            errorMsj = "No se encontraron coincidencias para $tipoBusqueda: '$query'."
                        }
                    } else {
                        errorMsj = "Error en el servidor: código ${response.code()}"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMsj = "Error de red. Verifica el servidor o los permisos."
                } finally {
                    isLoading = false
                }
            }
        } else {
            Toast.makeText(context, "Ingresa un término de búsqueda", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Pantalla Completa
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Buscar en Servidor") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // --- NUEVO: Selector de tipo de búsqueda ---
                    ExposedDropdownMenuBox(
                        expanded = expandedDropdown,
                        onExpandedChange = { expandedDropdown = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = tipoBusqueda,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Buscar por") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false }
                        ) {
                            opcionesBusqueda.forEach { opcion ->
                                DropdownMenuItem(
                                    text = { Text(opcion) },
                                    onClick = {
                                        tipoBusqueda = opcion
                                        expandedDropdown = false
                                        // Limpia resultados si cambia el tipo
                                        resultados = emptyList()
                                    }
                                )
                            }
                        }
                    }

                    // --- Campo de texto de búsqueda ---
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Término de búsqueda") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = realizarBusqueda) {
                                Icon(Icons.Default.Search, contentDescription = "Buscar")
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- Resultados ---
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (errorMsj.isNotEmpty()) {
                        Text(errorMsj, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(resultados) { sugerencia ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            onSeleccionar(
                                                sugerencia.serie ?: "",
                                                sugerencia.articulo ?: "",
                                                sugerencia.marca ?: "",
                                                sugerencia.modelo ?: ""
                                            )
                                        },
                                    elevation = CardDefaults.cardElevation(2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Inventario: ${sugerencia.inventario ?: "N/A"}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("Serie: ${sugerencia.serie ?: "N/A"}")
                                        Text("Artículo: ${sugerencia.articulo ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
                                        Text("Marca: ${sugerencia.marca ?: ""} | Mod: ${sugerencia.modelo ?: ""}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
// Variables de ancho actualizadas
private val ANCHO_NO_INVENTARIO = 110.dp
private val ANCHO_NO_SERIE = 120.dp
private val ANCHO_ARTICULO = 140.dp // NUEVO
private val ANCHO_MARCA = 100.dp
private val ANCHO_MODELO = 100.dp
private val ANCHO_UBICACION = 120.dp
private val ANCHO_OBSERVACION = 160.dp
private val ANCHO_ACCION = 160.dp

@Composable
private fun TablaInventario(items: List<ItemInventario>, onEliminar: (Int) -> Unit, onEditar: (ItemInventario) -> Unit) {
    val scrollHorizontal = rememberScrollState()

    Column(modifier = Modifier.horizontalScroll(scrollHorizontal)) {
        Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 10.dp)) {
            CeldaTabla("No. Inventario", ANCHO_NO_INVENTARIO, esEncabezado = true)
            CeldaTabla("No. Serie", ANCHO_NO_SERIE, esEncabezado = true)
            CeldaTabla("Artículo", ANCHO_ARTICULO, esEncabezado = true) // NUEVO
            CeldaTabla("Marca", ANCHO_MARCA, esEncabezado = true)
            CeldaTabla("Modelo", ANCHO_MODELO, esEncabezado = true)
            CeldaTabla("Ubicación", ANCHO_UBICACION, esEncabezado = true)
            CeldaTabla("Observaciones", ANCHO_OBSERVACION, esEncabezado = true)
            CeldaTabla("Acciones", ANCHO_ACCION, esEncabezado = true)
        }
        HorizontalDivider()

        LazyColumn {
            items(items, key = { it.id }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CeldaTabla(item.noInventario, ANCHO_NO_INVENTARIO)
                    CeldaTabla(item.noSerie, ANCHO_NO_SERIE)
                    CeldaTabla(item.articulo, ANCHO_ARTICULO) // NUEVO
                    CeldaTabla(item.marca, ANCHO_MARCA)
                    CeldaTabla(item.modelo, ANCHO_MODELO)
                    CeldaTabla(item.ubicacion, ANCHO_UBICACION)
                    CeldaTabla(item.observacion, ANCHO_OBSERVACION)
                    Row(
                        modifier = Modifier.width(ANCHO_ACCION),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onEditar(item) }) { Text("Editar") }
                        TextButton(onClick = { onEliminar(item.id) }) { Text("Borrar") }
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
        modifier = Modifier.width(ancho).padding(horizontal = 8.dp, vertical = 4.dp),
        fontWeight = if (esEncabezado) FontWeight.Bold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}