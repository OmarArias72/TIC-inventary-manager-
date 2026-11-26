package com.example.inventory.domain.repository

import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.ResultadoBusqueda
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a los equipos. La implementación decide de dónde vienen los datos (Room y el backend);
 * la capa de presentación solo conoce esta interfaz.
 *
 * Todas las escrituras son locales: se guardan en Room marcadas como pendientes y el
 * proceso de sincronización las envía al servidor cuando hay conexión.
 */
interface EquipoRepository {
    /** Equipos visibles (no incluye los marcados para eliminarse). Se actualiza solo. */
    fun observarEquipos(): Flow<List<Equipo>>

    /** Equipos con cambios sin sincronizar, conflictos o errores. */
    fun observarNoSincronizados(): Flow<List<Equipo>>

    suspend fun obtener(inventario: String): Equipo?

    suspend fun crear(datos: DatosEquipo): Result<Unit>

    suspend fun actualizar(inventarioOriginal: String, datos: DatosEquipo): Result<Unit>

    suspend fun eliminar(inventario: String): Result<Unit>

    /** Busca por No. de inventario o No. de serie: primero en el dispositivo y luego en el servidor. */
    suspend fun buscarPorCodigo(codigo: String): ResultadoBusqueda

    /** Conflicto: conservar la versión del dispositivo (se enviará y reemplazará la del servidor). */
    suspend fun conservarVersionLocal(inventario: String): Result<Unit>

    /** Conflicto o error: descartar el cambio local y quedarse con la versión del servidor. */
    suspend fun usarVersionServidor(inventario: String): Result<Unit>

    /** Error: volver a intentar enviar el cambio local. */
    suspend fun reintentar(inventario: String): Result<Unit>
}
