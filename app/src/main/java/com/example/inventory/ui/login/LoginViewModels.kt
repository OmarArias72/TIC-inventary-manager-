package com.example.inventory.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.ServidorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------- Servidor (campo de URL) ----------------

data class ServidorUiState(
    val url: String = "",
    val urlGuardada: String? = null,
    val probando: Boolean = false,
    val mensaje: String? = null,
    val conexionOk: Boolean? = null,
)

@HiltViewModel
class ServidorViewModel @Inject constructor(
    private val servidor: ServidorRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        ServidorUiState(url = servidor.url.value.orEmpty(), urlGuardada = servidor.url.value)
    )
    val ui: StateFlow<ServidorUiState> = _ui.asStateFlow()

    fun onUrl(texto: String) = _ui.update { it.copy(url = texto, mensaje = null, conexionOk = null) }

    /** Guarda la URL y comprueba que el backend responda. */
    fun guardarYProbar() {
        val guardada = servidor.guardarUrl(_ui.value.url)
        guardada.onFailure { e ->
            _ui.update { it.copy(mensaje = e.message, conexionOk = false) }
            return
        }
        val url = guardada.getOrThrow()
        _ui.update { it.copy(url = url, urlGuardada = url, probando = true, mensaje = null, conexionOk = null) }
        viewModelScope.launch {
            val resultado = servidor.probarConexion()
            _ui.update {
                it.copy(
                    probando = false,
                    conexionOk = resultado.isSuccess,
                    mensaje = resultado.fold({ m -> m }, { e -> e.message }),
                )
            }
        }
    }
}

// ---------------- Inicio de sesión ----------------

data class LoginUiState(
    val correo: String = "",
    val password: String = "",
    val cargando: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val servidor: ServidorRepository,
) : ViewModel() {

    // Si la sesión expiró, el correo ya viene escrito.
    private val _ui = MutableStateFlow(LoginUiState(correo = auth.usuario.value?.correo.orEmpty()))
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun onCorreo(texto: String) = _ui.update { it.copy(correo = texto, error = null) }
    fun onPassword(texto: String) = _ui.update { it.copy(password = texto, error = null) }

    /** Modo sin servidor: la app funciona solo con los datos del dispositivo. */
    fun entrarSinServidor(nombre: String) {
        auth.entrarSinServidor(nombre)
    }

    fun iniciarSesion(alTerminar: () -> Unit = {}) {
        val estado = _ui.value
        when {
            servidor.url.value.isNullOrBlank() -> {
                _ui.update { it.copy(error = "Primero guarda la URL del servidor.") }
                return
            }
            estado.correo.isBlank() || estado.password.isBlank() -> {
                _ui.update { it.copy(error = "Escribe tu correo y tu contraseña.") }
                return
            }
        }
        _ui.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            auth.iniciarSesion(estado.correo, estado.password)
                .onSuccess {
                    _ui.update { it.copy(cargando = false, password = "") }
                    alTerminar()
                }
                .onFailure { e -> _ui.update { it.copy(cargando = false, error = e.message) } }
        }
    }
}
