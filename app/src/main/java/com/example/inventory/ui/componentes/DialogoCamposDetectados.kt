package com.example.inventory.ui.componentes

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.inventory.domain.model.AnalisisImagen
import com.example.inventory.domain.model.CampoDestino
import com.example.inventory.domain.model.CandidatoOcr

/**
 * Muestra todos los valores encontrados en la foto y permite:
 * - filtrarlos (códigos de barras, texto, solo sugeridos o por texto escrito);
 * - asignar cada uno a un campo (No. Inventario, No. Serie, Marca, Modelo) o a ninguno.
 *
 * Un campo solo puede tener un valor: al asignarlo a otro candidato se quita del anterior.
 *
 * @param camposPermitidos campos que se pueden llenar (p. ej. sin Inventario si ya no es editable).
 * @param seleccionInicial propuesta automática (el mejor candidato para cada campo pedido).
 * @param textoBoton texto del botón de confirmar ("Aplicar", "Buscar"...).
 */
@Composable
fun DialogoCamposDetectados(
    analisis: AnalisisImagen,
    camposPermitidos: List<CampoDestino>,
    seleccionInicial: Map<CampoDestino, String>,
    onAplicar: (Map<CampoDestino, String>) -> Unit,
    onCancelar: () -> Unit,
    textoBoton: String = "Aplicar",
) {
    // campo -> valor elegido
    val asignacion = remember(analisis) { mutableStateMapOf<CampoDestino, String>().apply { putAll(seleccionInicial) } }
    var verCodigos by remember { mutableStateOf(true) }
    var verTexto by remember { mutableStateOf(true) }
    var soloSugeridos by remember { mutableStateOf(false) }
    var filtro by remember { mutableStateOf("") }

    val visibles = analisis.candidatos.filter { c ->
        val porOrigen = (verCodigos && c.esCodigoBarras) || (verTexto && c.esTexto)
        val porSugerencia = !soloSugeridos || c.sugerencia != null || c.valor in asignacion.values
        val porTexto = filtro.isBlank() || c.valor.contains(filtro.trim(), ignoreCase = true)
        porOrigen && porSugerencia && porTexto
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Datos detectados en la imagen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (analisis.candidatos.isEmpty()) {
                    Text("No se detectó texto ni códigos. Intenta acercarte más, con buena luz, o usa el recorte.")
                } else {
                Text(
                    "Toca el campo al que corresponde cada valor. Los que no asignes se ignoran.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // ---- Filtros ----
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(selected = verCodigos, onClick = { verCodigos = !verCodigos }, label = { Text("Códigos de barras") })
                    FilterChip(selected = verTexto, onClick = { verTexto = !verTexto }, label = { Text("Texto") })
                    FilterChip(selected = soloSugeridos, onClick = { soloSugeridos = !soloSugeridos }, label = { Text("Solo sugeridos") })
                }
                OutlinedTextField(
                    value = filtro,
                    onValueChange = { filtro = it },
                    label = { Text("Filtrar valores") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- Candidatos ----
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (visibles.isEmpty()) {
                        Text("Ningún valor coincide con los filtros.", style = MaterialTheme.typography.bodySmall)
                    }
                    visibles.forEach { candidato ->
                        FilaCandidato(
                            candidato = candidato,
                            camposPermitidos = camposPermitidos,
                            asignadoA = asignacion.entries.firstOrNull { it.value == candidato.valor }?.key,
                            onAsignar = { campo ->
                                // Quitar el valor de cualquier otro campo y asignarlo (o desasignarlo)
                                asignacion.filterValues { it == candidato.valor }.keys.toList()
                                    .forEach { asignacion.remove(it) }
                                if (campo != null) asignacion[campo] = candidato.valor
                            },
                        )
                    }
                }

                // ---- Resumen ----
                Text(
                    camposPermitidos.joinToString("\n") { campo -> "${campo.etiqueta}: ${asignacion[campo] ?: "—"}" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAplicar(asignacion.filterKeys { it in camposPermitidos }) },
                enabled = asignacion.keys.any { it in camposPermitidos },
            ) { Text(textoBoton) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun FilaCandidato(
    candidato: CandidatoOcr,
    camposPermitidos: List<CampoDestino>,
    asignadoA: CampoDestino?,
    onAsignar: (CampoDestino?) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (asignadoA != null) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(candidato.valor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            val origen = buildList {
                if (candidato.esCodigoBarras) add("código de barras")
                if (candidato.esTexto) add("texto")
            }.joinToString(" y ")
            Text(
                buildString {
                    append("Leído en: ").append(origen)
                    candidato.sugerencia?.let { append(" · parece ").append(it.etiqueta) }
                },
                style = MaterialTheme.typography.labelSmall,
            )
            if (candidato.contexto.isNotBlank()) {
                Text(candidato.contexto, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                camposPermitidos.forEach { campo ->
                    val seleccionado = asignadoA == campo
                    FilterChip(
                        selected = seleccionado,
                        onClick = { onAsignar(if (seleccionado) null else campo) },
                        label = { Text(campo.etiqueta) },
                        leadingIcon = if (seleccionado) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.padding(0.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}
