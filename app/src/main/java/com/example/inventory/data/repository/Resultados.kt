package com.example.inventory.data.repository

import kotlinx.coroutines.CancellationException

/** Como runCatching, pero sin atrapar la cancelación de corrutinas (que debe propagarse). */
internal suspend fun <T> comoResultado(bloque: suspend () -> T): Result<T> = try {
    Result.success(bloque())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
