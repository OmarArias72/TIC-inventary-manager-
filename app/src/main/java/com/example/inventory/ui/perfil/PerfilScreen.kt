package com.example.inventory.ui.perfil

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.inventory.domain.model.PerfilUsuario
import com.example.inventory.domain.model.Usuario
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.ServidorRepository
import com.example.inventory.ui.componentes.BarraSuperior
import com.example.inventory.ui.componentes.formatFecha
import com.example.inventory.ui.theme.DoradoInstitucional
import com.example.inventory.ui.theme.VerdeInstitucional
import com.example.inventory.ui.theme.VerdeOscuroInstitucional
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PerfilUiState(
    val usuario: Usuario? = null,
    val perfil: PerfilUsuario? = null,
    val urlServidor: String? = null,
    val actualizando: Boolean = false,
)

/** Perfil del usuario: solo lectura. Los datos vienen de GET /api/usuarios/me (solo los propios). */
@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val auth: AuthRepository,
    servidor: ServidorRepository,
) : ViewModel() {

    private val actualizando = MutableStateFlow(false)

    val ui: StateFlow<PerfilUiState> = combine(auth.usuario, auth.perfil, servidor.url, actualizando) { u, p, url, cargando ->
        PerfilUiState(u, p, url, cargando)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerfilUiState())

    private val _mensajes = Channel<String>(Channel.BUFFERED)
    val mensajes: Flow<String> = _mensajes.receiveAsFlow()

    init {
        // Al abrir: intentar traer la versión más reciente (sin conexión se queda la copia guardada).
        if (auth.usuario.value?.esLocal == false) actualizar(silencioso = true)
    }

    fun actualizar(silencioso: Boolean = false) {
        actualizando.value = true
        viewModelScope.launch {
            auth.actualizarPerfil()
                .onSuccess { if (!silencioso) _mensajes.send("Perfil actualizado.") }
                .onFailure { e -> if (!silencioso) _mensajes.send(e.message ?: "No se pudo actualizar.") }
            actualizando.value = false
        }
    }
}

@Composable
fun PerfilScreen(
    onAbrirMenu: () -> Unit,
    onConectar: () -> Unit,
    viewModel: PerfilViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.mensajes.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    val usuario = ui.usuario ?: return
    val perfil = ui.perfil

    Scaffold(
        topBar = {
            BarraSuperior("Mi perfil", onAbrirMenu) {
                if (!usuario.esLocal) {
                    IconButton(onClick = { viewModel.actualizar() }, enabled = !ui.actualizando) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar desde el servidor")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Encabezado(
                nombre = perfil?.nombreCompleto?.ifBlank { null } ?: usuario.nombre,
                rol = when {
                    usuario.esLocal -> "Modo sin servidor"
                    perfil != null && perfil.nombreRol.isNotBlank() -> perfil.nombreRol
                    else -> usuario.rol.name
                },
            )
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (ui.actualizando) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  Actualizando…", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (usuario.esLocal) {
                    TarjetaModoLocal(onConectar)
                } else {
                    Tarjeta("Cuenta") {
                        Dato(Icons.Default.Email, "Correo", perfil?.correo ?: usuario.correo)
                        Dato(Icons.Default.Badge, "Rol", perfil?.nombreRol?.ifBlank { null } ?: usuario.rol.name)
                        if (perfil != null) {
                            Dato(Icons.Default.VerifiedUser, "Estado", buildString {
                                append(if (perfil.activo) "Activa" else "Inactiva")
                                append(if (perfil.correoVerificado) " · correo verificado" else " · correo sin verificar")
                            })
                        }
                    }
                    if (perfil != null) {
                        Tarjeta("Actividad") {
                            Dato(Icons.Default.Schedule, "Último acceso", perfil.ultimoAccesoMs?.let { formatFecha(it, conHora = true) } ?: "—")
                            Dato(Icons.Default.CalendarToday, "Miembro desde", perfil.fechaCreacionMs?.let { formatFecha(it) } ?: "—")
                        }
                        Text(
                            "Datos descargados el ${formatFecha(perfil.descargadoMs, conHora = true)}. " +
                                "Para modificarlos usa la plataforma web.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Aún no se ha descargado tu perfil completo. Conéctate y toca actualizar.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Tarjeta("Aplicación") {
                    Dato(Icons.Default.Dns, "Servidor", ui.urlServidor ?: "Sin configurar")
                }
            }
        }
    }
}

@Composable
private fun Encabezado(nombre: String, rol: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(VerdeOscuroInstitucional, VerdeInstitucional)))
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = DoradoInstitucional, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        iniciales(nombre),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = VerdeOscuroInstitucional,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(nombre, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)) {
                Text(
                    rol,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TarjetaModoLocal(onConectar: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Estás usando la app sin servidor", fontWeight = FontWeight.Bold)
            }
            Text(
                "Todo lo que registres se guarda en este dispositivo. Cuando el servidor esté disponible, " +
                    "inicia sesión con tu cuenta: los datos se enviarán y aquí aparecerá tu perfil.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onConectar) { Text("Conectar al servidor") }
        }
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider()
            contenido()
        }
    }
}

@Composable
private fun Dato(icono: ImageVector, etiqueta: String, valor: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(valor.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun iniciales(nombre: String): String =
    nombre.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
