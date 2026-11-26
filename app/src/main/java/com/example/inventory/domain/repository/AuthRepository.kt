package com.example.inventory.domain.repository

import com.example.inventory.domain.model.PerfilUsuario
import com.example.inventory.domain.model.Usuario
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    /** Usuario con sesión en el dispositivo (se conserva sin conexión). null = mostrar login. */
    val usuario: StateFlow<Usuario?>

    /** true si el servidor rechazó la renovación: se puede seguir trabajando local, pero no sincronizar. */
    val sesionExpirada: StateFlow<Boolean>

    /** Última copia del perfil descargada del servidor (se puede ver sin conexión). */
    val perfil: StateFlow<PerfilUsuario?>

    suspend fun iniciarSesion(correo: String, password: String): Result<Usuario>

    /**
     * Entra sin servidor: todo se guarda en el dispositivo y no se sincroniza. Al iniciar
     * sesión más tarde, lo capturado se envía al servidor.
     */
    fun entrarSinServidor(nombre: String): Usuario

    /** Descarga el perfil del servidor (requiere conexión y sesión). */
    suspend fun actualizarPerfil(): Result<PerfilUsuario>

    /** Cierra la sesión. Los datos locales (incluidos los pendientes) se conservan. */
    suspend fun cerrarSesion()
}
