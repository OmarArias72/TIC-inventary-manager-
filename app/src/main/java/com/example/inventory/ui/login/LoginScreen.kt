package com.example.inventory.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventory.ui.theme.DoradoInstitucional
import com.example.inventory.ui.theme.VerdeClaroInstitucional
import com.example.inventory.ui.theme.VerdeInstitucional
import com.example.inventory.ui.theme.VerdeOscuroInstitucional

/** Para qué se muestra el login. */
enum class ModoLogin {
    /** Primera vez (no hay usuario en el dispositivo). */
    INICIAL,
    /** La sesión expiró: los datos locales se conservan. */
    REAUTENTICAR,
    /** El usuario trabaja en modo sin servidor y quiere conectarse para enviar sus datos. */
    CONECTAR,
}

/**
 * Inicio de sesión del Área de TIC con la paleta institucional.
 * Incluye la URL del servidor (plegable) y la opción de usar la app sin servidor.
 */
@Composable
fun LoginScreen(
    modo: ModoLogin = ModoLogin.INICIAL,
    onCerrar: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
    servidorViewModel: ServidorViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val servidor by servidorViewModel.ui.collectAsStateWithLifecycle()
    var verPassword by remember { mutableStateOf(false) }
    // El servidor se muestra desplegado si todavía no hay URL guardada.
    var mostrarServidor by rememberSaveable { mutableStateOf(servidorViewModel.ui.value.urlGuardada == null) }
    var pedirNombreLocal by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EncabezadoTic()

            Card(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .offset(y = (-36).dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        when (modo) {
                            ModoLogin.INICIAL -> "Inventario de equipos"
                            ModoLogin.REAUTENTICAR -> "Tu sesión expiró"
                            ModoLogin.CONECTAR -> "Conectar al servidor"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = VerdeOscuroInstitucional,
                    )
                    Text(
                        when (modo) {
                            ModoLogin.INICIAL -> "Inicia sesión con tu cuenta de Administrador u Operador del Área de TIC."
                            ModoLogin.REAUTENTICAR -> "Inicia sesión de nuevo para sincronizar. Tus cambios locales se conservan."
                            ModoLogin.CONECTAR -> "Inicia sesión para enviar al servidor lo que capturaste sin conexión."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // ---- Servidor (plegable) ----
                    ResumenServidor(
                        url = servidor.urlGuardada,
                        ok = servidor.conexionOk,
                        abierto = mostrarServidor,
                        onAlternar = { mostrarServidor = !mostrarServidor },
                    )
                    AnimatedVisibility(visible = mostrarServidor) {
                        SeccionServidor(viewModel = servidorViewModel)
                    }

                    // ---- Credenciales ----
                    OutlinedTextField(
                        value = ui.correo,
                        onValueChange = viewModel::onCorreo,
                        label = { Text("Correo institucional") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        enabled = !ui.cargando,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.password,
                        onValueChange = viewModel::onPassword,
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = if (verPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { verPassword = !verPassword }) {
                                Icon(
                                    if (verPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (verPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                )
                            }
                        },
                        enabled = !ui.cargando,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                    Button(
                        onClick = { viewModel.iniciarSesion(alTerminar = onCerrar) },
                        enabled = !ui.cargando,
                        colors = ButtonDefaults.buttonColors(containerColor = VerdeInstitucional),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (ui.cargando) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Iniciar sesión", fontWeight = FontWeight.Bold)
                    }

                    // ---- Sin servidor ----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("  o  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    when (modo) {
                        ModoLogin.INICIAL -> {
                            OutlinedButton(
                                onClick = { pedirNombreLocal = true },
                                enabled = !ui.cargando,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Default.CloudOff, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Usar sin servidor")
                            }
                            Text(
                                "Guarda todo en este dispositivo. Cuando el servidor esté listo, inicia sesión y los datos se enviarán.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ModoLogin.REAUTENTICAR, ModoLogin.CONECTAR -> {
                            TextButton(onClick = onCerrar, modifier = Modifier.fillMaxWidth()) {
                                Text("Seguir trabajando sin conexión")
                            }
                        }
                    }
                }
            }

            Text(
                "Control TIC · Inventario v2.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = (-20).dp).padding(bottom = 16.dp),
            )
        }
    }

    if (pedirNombreLocal) {
        DialogoModoLocal(
            onEntrar = { nombre ->
                pedirNombreLocal = false
                viewModel.entrarSinServidor(nombre)
            },
            onCancelar = { pedirNombreLocal = false },
        )
    }
}

/** Encabezado con degradado verde institucional e iconografía del Área de TIC. */
@Composable
private fun EncabezadoTic() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(VerdeOscuroInstitucional, VerdeInstitucional)),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
            )
            .padding(top = 48.dp, bottom = 64.dp, start = 24.dp, end = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconoCirculo(Icons.Default.Router, 44.dp)
                IconoCirculo(Icons.Default.Computer, 64.dp)
                IconoCirculo(Icons.Default.Dns, 44.dp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Control TIC",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Área de Tecnologías de la Información y Comunicación",
                style = MaterialTheme.typography.bodyMedium,
                color = VerdeClaroInstitucional,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(3.dp)
                    .background(DoradoInstitucional, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Centro Universitario UAEM Tianguistenco",
                style = MaterialTheme.typography.labelLarge,
                color = DoradoInstitucional,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun IconoCirculo(icono: ImageVector, tamano: androidx.compose.ui.unit.Dp) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.12f),
        modifier = Modifier.size(tamano),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icono, contentDescription = null, tint = DoradoInstitucional, modifier = Modifier.size(tamano * 0.55f))
        }
    }
}

/** Fila compacta con el servidor configurado y el botón para cambiarlo. */
@Composable
private fun ResumenServidor(url: String?, ok: Boolean?, abierto: Boolean, onAlternar: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (ok == true) Icons.Default.CheckCircle else Icons.Default.Dns,
                contentDescription = null,
                tint = VerdeInstitucional,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Servidor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    url?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/') ?: "Sin configurar",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onAlternar) { Text(if (abierto) "Ocultar" else "Cambiar") }
        }
    }
}

/** Campo de URL del servidor con botón para guardar y probar la conexión. */
@Composable
fun SeccionServidor(viewModel: ServidorViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = ui.url,
            onValueChange = viewModel::onUrl,
            label = { Text("URL del servidor") },
            placeholder = { Text("https://xxxx.trycloudflare.com") },
            supportingText = { Text("La URL que muestra cloudflared (o http://IP:3000 en la red local, solo en debug).") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !ui.probando,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = viewModel::guardarYProbar, enabled = !ui.probando) {
                Text("Guardar y probar")
            }
            Spacer(Modifier.width(12.dp))
            if (ui.probando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        ui.mensaje?.let {
            Text(
                it,
                color = if (ui.conexionOk == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Diálogo para cambiar la URL desde el menú, sin cerrar sesión. */
@Composable
fun DialogoServidor(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Servidor") },
        text = { SeccionServidor() },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

/** Pide un nombre (opcional) para identificar lo capturado en modo sin servidor. */
@Composable
private fun DialogoModoLocal(onEntrar: (String) -> Unit, onCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(Icons.Default.CloudOff, contentDescription = null) },
        title = { Text("Usar sin servidor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Podrás registrar y actualizar equipos; todo queda en este dispositivo. " +
                        "Al iniciar sesión más tarde, se enviará al servidor y se aplicarán los permisos de tu cuenta.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Tu nombre (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onEntrar(nombre) }, colors = ButtonDefaults.buttonColors(containerColor = VerdeInstitucional)) {
                Text("Entrar")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
    )
}
