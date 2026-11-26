package com.example.inventory.ui.componentes

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ObjetivoCaptura
import com.example.inventory.utils.crearUriParaCamara
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFecha(ms: Long, conHora: Boolean = false): String {
    if (ms <= 0) return ""
    val patron = if (conHora) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy"
    return SimpleDateFormat(patron, Locale.getDefault()).format(Date(ms))
}

/** Funciones para abrir la cámara o la galería; se obtienen con [rememberCapturadorImagen]. */
class CapturadorImagen(
    val abrirCamara: () -> Unit,
    val abrirGaleria: () -> Unit,
)

/**
 * Prepara los lanzadores de cámara, galería, recorte y permiso de cámara.
 *
 * @param recortar true = abre el recortador (permite girar y encuadrar solo la etiqueta);
 *                 false = analiza la foto completa directamente.
 * @param onImagen recibe la Uri de la imagen lista para analizar.
 */
@Composable
fun rememberCapturadorImagen(recortar: Boolean, onImagen: (Uri) -> Unit): CapturadorImagen {
    val context = LocalContext.current
    var uriCamara by remember { mutableStateOf<Uri?>(null) }

    val recorte = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let(onImagen)
        } else if (result.error != null) {
            result.error?.printStackTrace()
            Toast.makeText(context, "Error al recortar la imagen", Toast.LENGTH_SHORT).show()
        }
    }
    val camaraDirecta = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        val uri = uriCamara
        if (exito && uri != null) onImagen(uri)
    }
    val galeriaDirecta = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) onImagen(uri)
    }
    val opcionesRecorte = { camara: Boolean ->
        CropImageContractOptions(
            uri = null,
            cropImageOptions = CropImageOptions(
                imageSourceIncludeGallery = !camara,
                imageSourceIncludeCamera = camara,
                allowRotation = true,
                allowFlipping = false,
                activityTitle = "Encuadra la etiqueta",
            ),
        )
    }
    val abrirCamaraConPermiso = {
        if (recortar) {
            recorte.launch(opcionesRecorte(true))
        } else {
            val uri = crearUriParaCamara(context)
            uriCamara = uri
            camaraDirecta.launch(uri)
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) abrirCamaraConPermiso()
        else Toast.makeText(context, "Se requiere permiso de cámara", Toast.LENGTH_SHORT).show()
    }

    return CapturadorImagen(
        abrirCamara = {
            val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            if (concedido) abrirCamaraConPermiso() else permiso.launch(Manifest.permission.CAMERA)
        },
        abrirGaleria = {
            if (recortar) recorte.launch(opcionesRecorte(false)) else galeriaDirecta.launch("image/*")
        },
    )
}

/**
 * Tarjeta del escáner. El usuario elige QUÉ quiere capturar (inventario, serie o todo),
 * toma la foto y después elige en un diálogo qué valor detectado va en cada campo.
 */
@Composable
fun EscanerOcrCard(
    habilitado: Boolean,
    onImagenLista: (Uri, ObjetivoCaptura) -> Unit,
    modifier: Modifier = Modifier,
    objetivoInicial: ObjetivoCaptura = ObjetivoCaptura.INVENTARIO,
) {
    var objetivo by rememberSaveable { mutableStateOf(objetivoInicial) }
    var recortar by rememberSaveable { mutableStateOf(true) }
    val capturador = rememberCapturadorImagen(recortar = recortar) { uri -> onImagenLista(uri, objetivo) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Escáner de etiquetas", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text("¿Qué quieres capturar?", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ObjetivoCaptura.entries.forEach { opcion ->
                    FilterChip(
                        selected = objetivo == opcion,
                        onClick = { objetivo = opcion },
                        label = { Text(opcion.etiqueta) },
                        enabled = habilitado,
                    )
                }
            }
            Text(
                when (objetivo) {
                    ObjetivoCaptura.INVENTARIO -> "Enfoca la etiqueta de Control de Patrimonio (código de barras y número)."
                    ObjetivoCaptura.SERIE -> "Enfoca la etiqueta del fabricante (\"Serial No.\" o \"S/N\")."
                    ObjetivoCaptura.TODOS -> "Se detectan inventario, serie, marca y modelo; tú eliges cuáles usar."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = recortar, onCheckedChange = { recortar = it }, enabled = habilitado)
                Text("Recortar y girar antes de analizar (más preciso)", style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = capturador.abrirCamara, enabled = habilitado) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cámara")
                }
                Button(
                    onClick = capturador.abrirGaleria,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    enabled = habilitado,
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Galería")
                }
            }
        }
    }
}

/** Campos del equipo, compartidos por el diálogo de registro y la pantalla de actualizar. */
@Composable
fun CamposEquipo(
    datos: DatosEquipo,
    onCambio: (DatosEquipo) -> Unit,
    estadosPermitidos: List<EstadoEquipo>,
    habilitado: Boolean,
    inventarioEditable: Boolean = true,
    accionInventario: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = datos.inventario,
            onValueChange = { onCambio(datos.copy(inventario = it)) },
            label = { Text("No. Inventario *") },
            singleLine = true,
            enabled = habilitado && inventarioEditable,
            trailingIcon = accionInventario,
            supportingText = if (!inventarioEditable) {
                { Text("No se puede cambiar: el equipo ya está en el servidor") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = datos.serie,
            onValueChange = { onCambio(datos.copy(serie = it)) },
            label = { Text("No. Serie") },
            singleLine = true,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = datos.articulo,
            onValueChange = { onCambio(datos.copy(articulo = it)) },
            label = { Text("Artículo (ej. CPU, MONITOR)") },
            singleLine = true,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = datos.marca,
            onValueChange = { onCambio(datos.copy(marca = it)) },
            label = { Text("Marca") },
            singleLine = true,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = datos.modelo,
            onValueChange = { onCambio(datos.copy(modelo = it)) },
            label = { Text("Modelo") },
            singleLine = true,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        )
        SelectorEstado(
            estado = datos.estado,
            opciones = estadosPermitidos,
            onSeleccion = { onCambio(datos.copy(estado = it)) },
            habilitado = habilitado,
        )
        OutlinedTextField(
            value = datos.caracteristicas,
            onValueChange = { onCambio(datos.copy(caracteristicas = it)) },
            label = { Text("Observaciones") },
            minLines = 2,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorEstado(
    estado: EstadoEquipo,
    opciones: List<EstadoEquipo>,
    onSeleccion: (EstadoEquipo) -> Unit,
    habilitado: Boolean,
) {
    var expandido by remember { mutableStateOf(false) }
    val puedeCambiar = habilitado && opciones.size > 1
    ExposedDropdownMenuBox(
        expanded = expandido && puedeCambiar,
        onExpandedChange = { if (puedeCambiar) expandido = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = estado.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Estado del Equipo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) },
            supportingText = if (opciones == listOf(EstadoEquipo.BAJA)) {
                { Text("Solo el administrador puede reactivar un equipo dado de BAJA") }
            } else null,
            enabled = habilitado,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expandido && puedeCambiar, onDismissRequest = { expandido = false }) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(opcion.name) },
                    onClick = {
                        onSeleccion(opcion)
                        expandido = false
                    },
                )
            }
        }
    }
}

fun colorEstadoEquipo(estado: EstadoEquipo, esquema: androidx.compose.material3.ColorScheme): Color = when (estado) {
    EstadoEquipo.ALTA -> esquema.primary
    EstadoEquipo.BAJA -> esquema.error
    EstadoEquipo.MANTENIMIENTO -> esquema.tertiary
}

/** Etiqueta pequeña con el estado de sincronización de un registro. */
@Composable
fun EtiquetaSync(estado: EstadoSync) {
    val (texto, fondo) = when (estado) {
        EstadoSync.SINCRONIZADO -> "Sincronizado" to MaterialTheme.colorScheme.primaryContainer
        EstadoSync.PENDIENTE_CREAR -> "Alta pendiente" to MaterialTheme.colorScheme.secondary
        EstadoSync.PENDIENTE_ACTUALIZAR -> "Cambio pendiente" to MaterialTheme.colorScheme.secondary
        EstadoSync.PENDIENTE_ELIMINAR -> "Baja pendiente" to MaterialTheme.colorScheme.secondary
        EstadoSync.CONFLICTO -> "Conflicto" to MaterialTheme.colorScheme.errorContainer
        EstadoSync.ERROR -> "Error" to MaterialTheme.colorScheme.errorContainer
    }
    Surface(color = fondo, shape = MaterialTheme.shapes.small) {
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    onAbrirMenu: () -> Unit,
    acciones: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(titulo) },
        navigationIcon = {
            IconButton(onClick = onAbrirMenu) {
                Icon(Icons.Default.Menu, contentDescription = "Abrir menú")
            }
        },
        actions = { acciones() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
fun PantallaEnConstruccion(titulo: String, onAbrirMenu: () -> Unit) {
    Scaffold(topBar = { BarraSuperior(titulo, onAbrirMenu) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text("Pantalla en construcción", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun CargandoLineal(visible: Boolean) {
    if (visible) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}
