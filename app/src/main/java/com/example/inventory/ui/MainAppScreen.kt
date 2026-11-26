package com.example.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.inventory.domain.model.EstadoSincronizacion
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.model.Usuario
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.ConectividadMonitor
import com.example.inventory.domain.repository.SincronizacionRepository
import com.example.inventory.ui.actualizar.ActualizarInventarioScreen
import com.example.inventory.ui.computo.EquiposComputoScreen
import com.example.inventory.ui.inventario.InventarioScreen
import com.example.inventory.ui.login.DialogoServidor
import com.example.inventory.ui.login.LoginScreen
import com.example.inventory.ui.login.ModoLogin
import com.example.inventory.ui.perfil.PerfilScreen
import com.example.inventory.ui.sincronizacion.SincronizacionScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val hayRed: Boolean = true,
    val sincronizacion: EstadoSincronizacion = EstadoSincronizacion(),
    val sesionExpirada: Boolean = false,
    /** Modo sin servidor: nada se sincroniza hasta iniciar sesión. */
    val modoLocal: Boolean = false,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val sincronizacion: SincronizacionRepository,
    conectividad: ConectividadMonitor,
) : ViewModel() {

    val ui: StateFlow<MainUiState> = combine(
        conectividad.hayRed,
        sincronizacion.estado,
        auth.sesionExpirada,
        auth.usuario,
    ) { red, estado, expirada, usuario -> MainUiState(red, estado, expirada, usuario?.esLocal == true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun sincronizarAhora() = sincronizacion.sincronizarAhora()

    fun cerrarSesion() {
        viewModelScope.launch { auth.cerrarSesion() }
    }
}

enum class Destino(val titulo: String) {
    REGISTRAR("Registrar inventario"),
    ACTUALIZAR("Actualizar inventario"),
    EQUIPOS_COMPUTO("Actualizar equipos de cómputo"),
    SINCRONIZACION("Sincronización"),
    PERFIL("Mi perfil"),
}

@Composable
fun MainAppScreen(
    usuario: Usuario,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var destino by rememberSaveable { mutableStateOf(Destino.REGISTRAR) }
    var mostrarServidor by remember { mutableStateOf(false) }
    var mostrarCerrarSesion by remember { mutableStateOf(false) }
    var mostrarLogin by remember { mutableStateOf(false) }

    if (mostrarLogin) {
        // Sesión expirada o modo sin servidor: se inicia sesión sin perder lo que hay en el dispositivo.
        LoginScreen(
            modo = if (usuario.esLocal) ModoLogin.CONECTAR else ModoLogin.REAUTENTICAR,
            onCerrar = { mostrarLogin = false },
        )
        return
    }

    val abrirMenu: () -> Unit = { scope.launch { drawerState.open() } }
    val barraEstado: @Composable () -> Unit = {
        BarraEstadoSync(
            estado = ui,
            onSincronizar = viewModel::sincronizarAhora,
            onIniciarSesion = { mostrarLogin = true },
            onVerPendientes = { destino = Destino.SINCRONIZACION },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    usuario.nombre,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    when {
                        usuario.esLocal -> "Modo sin servidor"
                        usuario.rol == Rol.ADMIN -> "Administrador"
                        else -> "Operador"
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Destino.entries.forEach { d ->
                    NavigationDrawerItem(
                        label = { Text(d.titulo) },
                        badge = if (d == Destino.SINCRONIZACION) {
                            {
                                val total = ui.sincronizacion.pendientes + ui.sincronizacion.conflictos + ui.sincronizacion.errores
                                if (total > 0) Text(total.toString())
                            }
                        } else null,
                        selected = destino == d,
                        onClick = {
                            destino = d
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Servidor") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = false,
                    onClick = {
                        mostrarServidor = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
                NavigationDrawerItem(
                    label = { Text("Cerrar sesión") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    selected = false,
                    onClick = {
                        mostrarCerrarSesion = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
        },
    ) {
        when (destino) {
            Destino.REGISTRAR -> InventarioScreen(onAbrirMenu = abrirMenu, barraEstado = barraEstado)
            Destino.ACTUALIZAR -> ActualizarInventarioScreen(onAbrirMenu = abrirMenu, barraEstado = barraEstado)
            Destino.EQUIPOS_COMPUTO -> EquiposComputoScreen(onAbrirMenu = abrirMenu, barraEstado = barraEstado)
            Destino.SINCRONIZACION -> SincronizacionScreen(onAbrirMenu = abrirMenu)
            Destino.PERFIL -> PerfilScreen(onAbrirMenu = abrirMenu, onConectar = { mostrarLogin = true })
        }
    }

    if (mostrarServidor) {
        DialogoServidor(onCerrar = { mostrarServidor = false })
    }

    if (mostrarCerrarSesion) {
        val pendientes = ui.sincronizacion.pendientes + ui.sincronizacion.conflictos + ui.sincronizacion.errores
        AlertDialog(
            onDismissRequest = { mostrarCerrarSesion = false },
            title = { Text("Cerrar sesión") },
            text = {
                Text(
                    if (pendientes > 0) "Hay $pendientes cambio(s) sin sincronizar. Se conservarán en este dispositivo " +
                        "y se enviarán cuando vuelvas a iniciar sesión."
                    else "¿Quieres cerrar la sesión?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    mostrarCerrarSesion = false
                    viewModel.cerrarSesion()
                }) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { mostrarCerrarSesion = false }) { Text("Cancelar") } },
        )
    }
}

/** Franja con el estado de conexión y sincronización. Se muestra arriba del contenido de cada pantalla. */
@Composable
fun BarraEstadoSync(
    estado: MainUiState,
    onSincronizar: () -> Unit,
    onIniciarSesion: () -> Unit,
    onVerPendientes: () -> Unit,
) {
    val s = estado.sincronizacion
    val problemas = s.conflictos + s.errores
    when {
        estado.modoLocal -> Franja(
            texto = "Modo sin servidor: todo se guarda en este dispositivo" +
                if (s.pendientes > 0) " (${s.pendientes} por enviar)." else ".",
            esAviso = false,
            accion = "Conectar",
            onAccion = onIniciarSesion,
            icono = { Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        estado.sesionExpirada -> Franja(
            texto = "Sesión expirada: los cambios se guardan aquí, pero no se sincronizan.",
            esAviso = true,
            accion = "Iniciar sesión",
            onAccion = onIniciarSesion,
        )
        !estado.hayRed -> Franja(
            texto = "Sin conexión. Los cambios se guardan en el dispositivo" +
                if (s.pendientes > 0) " (${s.pendientes} pendiente(s))." else ".",
            esAviso = false,
            icono = { Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        problemas > 0 -> Franja(
            texto = "$problemas equipo(s) necesitan revisión (conflicto o error).",
            esAviso = true,
            accion = "Revisar",
            onAccion = onVerPendientes,
            icono = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        s.sincronizando -> Franja(
            texto = "Sincronizando…",
            esAviso = false,
            icono = { CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) },
        )
        s.pendientes > 0 -> Franja(
            texto = "${s.pendientes} cambio(s) pendiente(s) de sincronizar.",
            esAviso = false,
            accion = "Sincronizar",
            onAccion = onSincronizar,
            icono = { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        else -> Unit
    }
}

@Composable
private fun Franja(
    texto: String,
    esAviso: Boolean,
    accion: String? = null,
    onAccion: () -> Unit = {},
    icono: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = if (esAviso) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icono?.invoke()
            Text(texto, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (accion != null) {
                TextButton(onClick = onAccion, colors = ButtonDefaults.textButtonColors()) {
                    Text(accion, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
