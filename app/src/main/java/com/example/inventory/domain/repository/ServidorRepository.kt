package com.example.inventory.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** URL base del servidor (por ejemplo la de cloudflared), configurable desde la app. */
interface ServidorRepository {
    val url: StateFlow<String?>

    /** Valida, normaliza y guarda la URL. Devuelve la URL tal como quedó guardada. */
    fun guardarUrl(texto: String): Result<String>

    /** Comprueba que en la URL guardada responda el backend. Devuelve un mensaje para el usuario. */
    suspend fun probarConexion(): Result<String>
}
