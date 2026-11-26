package com.example.inventory.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.inventory.domain.model.EstadoSync
import kotlinx.coroutines.flow.Flow

/**
 * Consultas de la tabla equipos. Las funciones que devuelven Flow se vuelven a emitir solas
 * cuando cambia la tabla (la pantalla se actualiza sin recargar a mano).
 *
 * Las funciones "condicionales" (marcarSincronizado, marcarProblema...) solo modifican la
 * fila si fecha_actualizacion_local sigue siendo la que se leyó antes de enviarla; devuelven
 * cuántas filas cambiaron (0 = el usuario la editó mientras tanto y queda pendiente).
 */
@Dao
interface EquipoDao {

    @Query("SELECT * FROM equipos WHERE eliminado = 0 ORDER BY fecha_registro DESC")
    fun observarVisibles(): Flow<List<EquipoEntity>>

    @Query("SELECT * FROM equipos WHERE estado_sync != 'SINCRONIZADO' ORDER BY fecha_actualizacion_local DESC")
    fun observarNoSincronizados(): Flow<List<EquipoEntity>>

    @Query("SELECT estado_sync AS estado, COUNT(*) AS total FROM equipos WHERE estado_sync != 'SINCRONIZADO' GROUP BY estado_sync")
    fun observarConteos(): Flow<List<ConteoEstado>>

    @Query(
        "SELECT * FROM equipos WHERE estado_sync IN ('PENDIENTE_CREAR', 'PENDIENTE_ACTUALIZAR', 'PENDIENTE_ELIMINAR') " +
            "ORDER BY fecha_actualizacion_local ASC"
    )
    suspend fun obtenerPendientesDeEnvio(): List<EquipoEntity>

    @Query("SELECT * FROM equipos WHERE inventario = :inventario LIMIT 1")
    suspend fun obtener(inventario: String): EquipoEntity?

    @Query(
        "SELECT * FROM equipos WHERE eliminado = 0 AND " +
            "(UPPER(inventario) = UPPER(:codigo) OR (serie != '' AND UPPER(serie) = UPPER(:codigo))) LIMIT 1"
    )
    suspend fun buscarPorCodigo(codigo: String): EquipoEntity?

    @Query("SELECT inventario FROM equipos WHERE estado_sync = 'SINCRONIZADO'")
    suspend fun inventariosSincronizados(): List<String>

    /** Inserta o reemplaza por clave primaria (sin borrar y reinsertar como REPLACE). */
    @Upsert
    suspend fun upsert(equipo: EquipoEntity)

    @Query("DELETE FROM equipos WHERE inventario = :inventario")
    suspend fun eliminar(inventario: String)

    // ---------- Resultado de la sincronización ----------

    @Query(
        "UPDATE equipos SET estado_sync = 'SINCRONIZADO', existe_en_servidor = 1, intentos = 0, " +
            "ultimo_error = NULL, version_servidor = NULL, fecha_actualizacion_servidor = :fechaServidor " +
            "WHERE inventario = :inventario AND fecha_actualizacion_local = :fechaLocal"
    )
    suspend fun marcarSincronizado(inventario: String, fechaLocal: Long, fechaServidor: String?): Int

    /** El alta llegó al servidor, pero el registro se editó mientras tanto: ahora es una edición pendiente. */
    @Query(
        "UPDATE equipos SET existe_en_servidor = 1, estado_sync = CASE WHEN estado_sync = 'PENDIENTE_CREAR' " +
            "THEN 'PENDIENTE_ACTUALIZAR' ELSE estado_sync END WHERE inventario = :inventario"
    )
    suspend fun marcarExisteEnServidor(inventario: String)

    @Query(
        "UPDATE equipos SET estado_sync = :estado, ultimo_error = :error, version_servidor = :versionServidor " +
            "WHERE inventario = :inventario AND fecha_actualizacion_local = :fechaLocal"
    )
    suspend fun marcarProblema(
        inventario: String,
        fechaLocal: Long,
        estado: EstadoSync,
        error: String?,
        versionServidor: String?,
    ): Int

    /** Fallo de red o del servidor: el registro sigue pendiente y se reintenta después. */
    @Query("UPDATE equipos SET intentos = intentos + 1, ultimo_error = :error WHERE inventario = :inventario")
    suspend fun registrarFalloTransitorio(inventario: String, error: String?)

    @Query("DELETE FROM equipos WHERE inventario = :inventario AND fecha_actualizacion_local = :fechaLocal")
    suspend fun eliminarSiNoCambio(inventario: String, fechaLocal: Long): Int

    /** El servidor rechazó la eliminación (permiso): el equipo vuelve a mostrarse con el error. */
    @Query(
        "UPDATE equipos SET eliminado = 0, estado_sync = 'ERROR', ultimo_error = :error " +
            "WHERE inventario = :inventario AND fecha_actualizacion_local = :fechaLocal"
    )
    suspend fun restaurarConError(inventario: String, fechaLocal: Long, error: String?): Int
}
