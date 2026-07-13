package com.example.inventory

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import kotlinx.coroutines.launch

class InventarioViewModel(private val repository: DatabaseRepository) : ViewModel() {
    var filas by mutableStateOf<List<ItemInventario>>(emptyList())
        private set

    init { cargarDatos() }

    fun cargarDatos() {
        viewModelScope.launch {
            filas = repository.obtenerTodos()
        }
    }

    fun guardarFila(item: ItemInventario) {
        viewModelScope.launch {
            if (item.id == 0) {
                repository.insertarItem(item)
            } else {
                repository.actualizarItem(item)
            }
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

    // Estados para el Dialog de Formulario
    var mostrarDialogo by remember { mutableStateOf(false) }
    var itemSeleccionado by remember { mutableStateOf<ItemInventario?>(null) }

    // Estado para el Dialog de Confirmación de Borrado
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        itemSeleccionado = ItemInventario()
                        mostrarDialogo = true
                    }) {
                        Text("Agregar Fila")
                    }
                    Button(onClick = { excelService.exportarInventario(viewModel.filas) }) {
                        Text("Exportar a XLSX")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (viewModel.filas.isEmpty()) {
                Text(
                    text = "No hay registros en la tabla.",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                TablaInventario(
                    items = viewModel.filas,
                    // En lugar de borrar directo, guardamos el ID para mostrar la advertencia
                    onEliminar = { id -> idAEliminar = id },
                    onEditar = { item ->
                        itemSeleccionado = item.copy()
                        mostrarDialogo = true
                    }
                )
            }
        }

        // Cuadro de diálogo para agregar/editar
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

        // Cuadro de diálogo para confirmar eliminación
        if (idAEliminar != null) {
            AlertDialog(
                onDismissRequest = { idAEliminar = null },
                title = { Text("Confirmar eliminación") },
                text = { Text("¿Estás seguro de que deseas eliminar este registro? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.eliminarFila(idAEliminar!!)
                            idAEliminar = null // Cerramos el diálogo
                        }
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { idAEliminar = null }) {
                        Text("Cancelar")
                    }
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
    var marca by remember { mutableStateOf(itemInicial.marca) }
    var modelo by remember { mutableStateOf(itemInicial.modelo) }
    var ubicacion by remember { mutableStateOf(itemInicial.ubicacion) }
    var observacion by remember { mutableStateOf(itemInicial.observacion) }

    // Lanzador unificado de CanHub para Recortar Imagen (sirve para Cámara y Galería)
    val cropImageLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // Obtenemos la URI de la imagen ya recortada
            val uriContent = result.uriContent
            if (uriContent != null) {
                coroutineScope.launch {
                    val textoExtraido = ocrService.extraerTexto(context, uriContent)
                    val datos = ocrService.extraerDatosDeSerie(textoExtraido)

                    if (!datos["codigo_barras"].isNullOrEmpty()) {
                        noInventario = datos["codigo_barras"]!!
                    }
                    if (!datos["numero_serie"].isNullOrEmpty()) {
                        noSerie = datos["numero_serie"]!!
                    }
                }
            }
        } else {
            val exception = result.error
            exception?.printStackTrace()
            Toast.makeText(context, "Error al procesar la imagen", Toast.LENGTH_SHORT).show()
        }
    }

    // Lanzador para pedir el permiso de la cámara (por seguridad)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Si dan permiso, abrimos la cámara con el Cropper
            cropImageLauncher.launch(
                CropImageContractOptions(
                    uri = null,
                    cropImageOptions = CropImageOptions(
                        imageSourceIncludeGallery = false,
                        imageSourceIncludeCamera = true
                    )
                )
            )
        } else {
            Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (itemInicial.id == 0) "Nuevo Registro" else "Editar Registro") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = noInventario,
                        onValueChange = { noInventario = it },
                        label = { Text("No. Inventario") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    // Botón de Cámara
                    IconButton(
                        onClick = {
                            val permissionCheckResult = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            )
                            if (permissionCheckResult == PackageManager.PERMISSION_GRANTED) {
                                cropImageLauncher.launch(
                                    CropImageContractOptions(
                                        uri = null,
                                        cropImageOptions = CropImageOptions(
                                            imageSourceIncludeGallery = false,
                                            imageSourceIncludeCamera = true
                                        )
                                    )
                                )
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Tomar foto")
                    }

                    // Botón de Galería
                    IconButton(
                        onClick = {
                            cropImageLauncher.launch(
                                CropImageContractOptions(
                                    uri = null,
                                    cropImageOptions = CropImageOptions(
                                        imageSourceIncludeGallery = true,
                                        imageSourceIncludeCamera = false
                                    )
                                )
                            )
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = "Elegir de galería")
                    }
                }

                OutlinedTextField(value = noSerie, onValueChange = { noSerie = it }, label = { Text("No. Serie") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = marca, onValueChange = { marca = it }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = modelo, onValueChange = { modelo = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ubicacion, onValueChange = { ubicacion = it }, label = { Text("Ubicación") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = observacion, onValueChange = { observacion = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onGuardar(
                        itemInicial.copy(
                            noInventario = noInventario,
                            noSerie = noSerie,
                            marca = marca,
                            modelo = modelo,
                            ubicacion = ubicacion,
                            observacion = observacion
                        )
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

// Variables de ancho y TablaInventario
private val ANCHO_NO_INVENTARIO = 110.dp
private val ANCHO_NO_SERIE = 120.dp
private val ANCHO_MARCA = 100.dp
private val ANCHO_MODELO = 100.dp
private val ANCHO_UBICACION = 120.dp
private val ANCHO_OBSERVACION = 160.dp
private val ANCHO_ACCION = 160.dp

@Composable
private fun TablaInventario(
    items: List<ItemInventario>,
    onEliminar: (Int) -> Unit,
    onEditar: (ItemInventario) -> Unit
) {
    val scrollHorizontal = rememberScrollState()

    Column(modifier = Modifier.horizontalScroll(scrollHorizontal)) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 10.dp)
        ) {
            CeldaTabla("No. Inventario", ANCHO_NO_INVENTARIO, esEncabezado = true)
            CeldaTabla("No. Serie", ANCHO_NO_SERIE, esEncabezado = true)
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
                    CeldaTabla(item.marca, ANCHO_MARCA)
                    CeldaTabla(item.modelo, ANCHO_MODELO)
                    CeldaTabla(item.ubicacion, ANCHO_UBICACION)
                    CeldaTabla(item.observacion, ANCHO_OBSERVACION)
                    Row(
                        modifier = Modifier.width(ANCHO_ACCION),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onEditar(item) }) {
                            Text("Editar")
                        }
                        TextButton(onClick = { onEliminar(item.id) }) {
                            Text("Borrar")
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
        overflow = TextOverflow.Ellipsis
    )
}