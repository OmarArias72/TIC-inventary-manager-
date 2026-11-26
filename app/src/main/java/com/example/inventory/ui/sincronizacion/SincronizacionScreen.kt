package com.example.inventory.ui.sincronizacion

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import com.example.inventory.domain.usecase.ComparadorVersiones
import com.example.inventory.domain.usecase.DiferenciaCampo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EquipoComputo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.usecase.Resolucion
import com.example.inventory.ui.componentes.BarraSuperior
import com.example.inventory.ui.componentes.CargandoLineal
import com.example.inventory.ui.componentes.EtiquetaSync
import com.example.inventory.ui.componentes.formatFecha

/** Estado de la sincronización, cambios pendientes y resolución de conflictos y errores. */
@Composable
fun SincronizacionScreen(
    onAbrirMenu: () -> Unit,
    viewModel: SincronizacionViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.mensajes.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    Scaffold(
        topBar = {
            BarraSuperior("Sincronización", onAbrirMenu) {
                IconButton(onClick = viewModel::sincronizarAhora) {
                    Icon(Icons.Default.Sync, contentDescription = "Sincronizar ahora")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Resumen(ui) { viewModel.sincronizarAhora() } }
            if (ui.equipos.isEmpty() && ui.equiposComputo.isEmpty()) {
                item {
                    Text(
                        "No hay cambios pendientes. Todo está sincronizado.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(ui.equipos, key = { "general-" + it.inventario }) { equipo ->
                TarjetaEquipo(
                    equipo = equipo,
                    ocupado = ui.procesando == equipo.inventario,
                    onResolver = { resolucion -> viewModel.resolver(equipo.inventario, resolucion) },
                )
            }
            if (ui.equiposComputo.isNotEmpty()) {
                item { Text("Equipos de cómputo", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            }
            items(ui.equiposComputo, key = { "computo-" + it.inventario }) { equipo ->
                TarjetaComputo(
                    equipo = equipo,
                    nombresSala = ui.nombresSala,
                    ocupado = ui.procesando == equipo.inventario,
                    onResolver = { resolucion -> viewModel.resolver(equipo.inventario, resolucion, esComputo = true) },
                )
            }
        }
    }
}

@Composable
private fun Resumen(ui: SincronizacionUiState, onSincronizar: () -> Unit) {
    val e = ui.estado
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Estado", fontWeight = FontWeight.Bold)
            CargandoLineal(e.sincronizando)
            Text("Pendientes de enviar: ${e.pendientes}")
            Text("Conflictos: ${e.conflictos}   ·   Con error: ${e.errores}")
            e.ultima?.let {
                Text(
                    "Última sincronización (${formatFecha(it.fechaMs, conHora = true)}): ${it.mensaje}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.exitosa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = onSincronizar, enabled = !e.sincronizando) { Text("Sincronizar ahora") }
        }
    }
}

@Composable
private fun TarjetaEquipo(equipo: Equipo, ocupado: Boolean, onResolver: (Resolucion) -> Unit) {
    TarjetaPendiente(
        inventario = equipo.inventario,
        descripcion = "${equipo.articulo} · ${equipo.marca} ${equipo.modelo}",
        estadoSync = equipo.estadoSync,
        ultimoError = equipo.ultimoError,
        diferencias = equipo.versionServidor?.let { ComparadorVersiones.equipo(equipo.datos, it) },
        eliminadoEnServidor = equipo.eliminadoEnServidor,
        ocupado = ocupado,
        onResolver = onResolver,
    )
}

@Composable
private fun TarjetaComputo(
    equipo: EquipoComputo,
    nombresSala: Map<Int, String>,
    ocupado: Boolean,
    onResolver: (Resolucion) -> Unit,
) {
    val b = equipo.datos.base
    TarjetaPendiente(
        inventario = equipo.inventario,
        descripcion = "Equipo de cómputo · ${b.marca} ${b.modelo}",
        estadoSync = equipo.estadoSync,
        ultimoError = equipo.ultimoError,
        diferencias = equipo.versionServidor?.let { servidor ->
            ComparadorVersiones.computo(equipo.datos, servidor) { id ->
                if (id == null) "Sin sala" else nombresSala[id] ?: "Sala $id"
            }
        },
        eliminadoEnServidor = equipo.eliminadoEnServidor,
        ocupado = ocupado,
        onResolver = onResolver,
    )
}

/** Tarjeta de un registro sin sincronizar. En conflictos muestra la comparación con el servidor. */
@Composable
private fun TarjetaPendiente(
    inventario: String,
    descripcion: String,
    estadoSync: EstadoSync,
    ultimoError: String?,
    diferencias: List<DiferenciaCampo>?,
    eliminadoEnServidor: Boolean,
    ocupado: Boolean,
    onResolver: (Resolucion) -> Unit,
) {
    val esConflicto = estadoSync == EstadoSync.CONFLICTO
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (estadoSync) {
                EstadoSync.CONFLICTO -> MaterialTheme.colorScheme.surface
                EstadoSync.ERROR -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (esConflicto) BorderStroke(2.dp, MaterialTheme.colorScheme.error) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(inventario, fontWeight = FontWeight.Bold)
                EtiquetaSync(estadoSync)
            }
            Text(descripcion, style = MaterialTheme.typography.bodySmall)
            ultimoError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (esConflicto) MaterialTheme.colorScheme.error else Color.Unspecified)
            }

            when (estadoSync) {
                EstadoSync.CONFLICTO -> {
                    if (diferencias != null) ComparacionCampos(diferencias)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onResolver(Resolucion.CONSERVAR_LOCAL) }, enabled = !ocupado) {
                            Text(if (eliminadoEnServidor) "Volver a crearlo" else "Conservar la mía")
                        }
                        OutlinedButton(onClick = { onResolver(Resolucion.USAR_SERVIDOR) }, enabled = !ocupado) {
                            Text(if (eliminadoEnServidor) "Eliminarlo aquí" else "Usar la del servidor")
                        }
                    }
                    Text(
                        if (eliminadoEnServidor) "Otra persona lo eliminó del servidor. \"Volver a crearlo\" lo envía de nuevo con tus datos."
                        else "\"Conservar la mía\": tus valores (resaltados en dorado) reemplazan los del servidor. " +
                            "\"Usar la del servidor\": se descartan tus cambios y quedan los valores en verde.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EstadoSync.ERROR -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onResolver(Resolucion.REINTENTAR) }, enabled = !ocupado) { Text("Reintentar") }
                    OutlinedButton(onClick = { onResolver(Resolucion.USAR_SERVIDOR) }, enabled = !ocupado) {
                        Text("Descartar mi cambio")
                    }
                }
                else -> Text(
                    "Se enviará automáticamente cuando haya conexión.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Comparación campo por campo. Los campos diferentes se resaltan y, dentro de cada valor,
 * las palabras que cambian se marcan: dorado = este dispositivo, verde = servidor.
 * Por defecto solo se muestran las diferencias; se puede ver la comparación completa.
 */
@Composable
private fun ComparacionCampos(filas: List<DiferenciaCampo>) {
    var soloDiferencias by remember { mutableStateOf(true) }
    val distintas = filas.count { it.distinto }
    val visibles = if (soloDiferencias) filas.filter { it.distinto } else filas
    val colorLocal = MaterialTheme.colorScheme.secondaryContainer
    val colorServidor = MaterialTheme.colorScheme.primaryContainer

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (distintas == 0) "Mismos datos; cambió la fecha en el servidor"
                else "$distintas de ${filas.size} campos son diferentes",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Leyenda("Este dispositivo", colorLocal)
            Leyenda("Servidor", colorServidor)
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = !soloDiferencias,
                onClick = { soloDiferencias = !soloDiferencias },
                label = { Text("Ver todo", style = MaterialTheme.typography.labelSmall) },
            )
        }
        visibles.forEach { fila ->
            if (fila.distinto) {
                FilaDiferente(fila, colorLocal, colorServidor)
            } else {
                Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fila.campo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
                    Text(fila.local.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.Check, contentDescription = "Igual", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/** Un campo diferente: nombre del campo y los dos valores, cada uno con sus palabras cambiadas resaltadas. */
@Composable
private fun FilaDiferente(fila: DiferenciaCampo, colorLocal: Color, colorServidor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("≠ ${fila.campo}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            ValorResaltado("Dispositivo", fila.local, fila.servidor, colorLocal)
            ValorResaltado("Servidor", fila.servidor, fila.local, colorServidor)
        }
    }
}

@Composable
private fun ValorResaltado(etiqueta: String, valor: String, otro: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(color = color, shape = RoundedCornerShape(4.dp), modifier = Modifier.width(84.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Spacer(Modifier.width(8.dp))
        if (valor.isBlank()) {
            Text("(vacío)", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // Si el texto cambió por completo se resalta todo; si no, solo las palabras distintas.
            val texto = buildAnnotatedString {
                ComparadorVersiones.marcarPalabras(valor, otro).forEach { segmento ->
                    if (segmento.resaltado) {
                        withStyle(SpanStyle(background = color, fontWeight = FontWeight.Bold)) { append(segmento.texto) }
                    } else {
                        append(segmento.texto)
                    }
                }
            }
            Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Leyenda(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = RoundedCornerShape(3.dp), modifier = Modifier.size(12.dp)) {}
        Spacer(Modifier.width(4.dp))
        Text(texto, style = MaterialTheme.typography.labelSmall)
    }
}
