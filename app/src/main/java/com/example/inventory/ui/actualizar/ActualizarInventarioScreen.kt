package com.example.inventory.ui.actualizar

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventory.ui.componentes.BarraSuperior
import com.example.inventory.ui.componentes.CamposEquipo
import com.example.inventory.ui.componentes.CargandoLineal
import com.example.inventory.ui.componentes.DialogoCamposDetectados
import com.example.inventory.ui.componentes.EscanerOcrCard
import com.example.inventory.ui.componentes.EtiquetaSync
import com.example.inventory.ui.componentes.formatFecha

@Composable
fun ActualizarInventarioScreen(
    onAbrirMenu: () -> Unit,
    barraEstado: @Composable () -> Unit,
    viewModel: ActualizarViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.mensajes.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Scaffold(topBar = { BarraSuperior("Actualizar Inventario", onAbrirMenu) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            barraEstado()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CargandoLineal(ui.ocupado)
                EscanerOcrCard(habilitado = !ui.ocupado, onImagenLista = viewModel::procesarImagen)
                if (ui.procesandoImagen) Text("Analizando imagen…", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                ui.origen?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                ui.equipo?.let { equipo ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EtiquetaSync(equipo.estadoSync)
                        Text(
                            "Registrado: ${formatFecha(equipo.fechaRegistro)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                CamposEquipo(
                    datos = ui.datos,
                    onCambio = viewModel::onCambio,
                    estadosPermitidos = ui.estadosPermitidos,
                    habilitado = !ui.ocupado,
                    // Si el equipo ya está en el servidor, su No. de inventario no se puede cambiar.
                    inventarioEditable = ui.equipo?.existeEnServidor != true,
                    accionInventario = {
                        IconButton(onClick = { viewModel.buscar() }, enabled = !ui.ocupado) {
                            Icon(Icons.Default.Search, contentDescription = "Buscar equipo")
                        }
                    },
                )

                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(
                    onClick = viewModel::guardar,
                    enabled = !ui.ocupado,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(if (ui.esAlta) "Registrar equipo nuevo" else "Guardar Cambios")
                }
                OutlinedButton(onClick = viewModel::limpiar, enabled = !ui.ocupado, modifier = Modifier.fillMaxWidth()) {
                    Text("Limpiar")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
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
