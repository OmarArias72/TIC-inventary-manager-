package com.example.inventory.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.inventory.domain.model.Usuario
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import com.example.inventory.ui.login.LoginScreen
import com.example.inventory.ui.login.ModoLogin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SesionViewModel @Inject constructor(
    auth: AuthRepository,
    sincronizacion: SincronizacionRepository,
) : ViewModel() {
    val usuario: StateFlow<Usuario?> = auth.usuario

    init {
        // Al abrir la app con sesión: enviar lo pendiente y dejar programada la sincronización periódica.
        if (auth.usuario.value != null) {
            sincronizacion.programarPeriodica()
            sincronizacion.sincronizarAhora()
        }
    }
}

/** Decide qué mostrar: inicio de sesión o la app. */
@Composable
fun AppRaiz(viewModel: SesionViewModel = hiltViewModel()) {
    val usuario by viewModel.usuario.collectAsStateWithLifecycle()
    val actual = usuario
    if (actual == null) {
        LoginScreen(modo = ModoLogin.INICIAL)
    } else {
        MainAppScreen(usuario = actual)
    }
}
