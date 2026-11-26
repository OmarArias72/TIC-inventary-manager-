package com.example.inventory.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.example.inventory.domain.model.EstadoSync
import kotlinx.coroutines.flow.Flow

/** Copia local de las salas del servidor (solo lectura: las administra el Admin desde el web). */
@Entity(tableName = "salas")
data class SalaEntity(
    @PrimaryKey @ColumnInfo(name = "id_sala") val idSala: Int,
    @ColumnInfo(name = "codigo") val codigo: String,
    @ColumnInfo(name = "nombre") val nombre: String,
    @ColumnInfo(name = "ubicacion") val ubicacion: String,
)

/**
 * Equipos de cómputo (versión 8 de la BD). Clave = No. de inventario (único también en el servidor).
 * id_servidor = idEquipoComputo del backend, necesario para editar (PUT /api/equipos-computo/{id}).
 *
 * Contraseña: el servidor nunca la envía en la lista. Si el usuario la cambia sin conexión,
 * se guarda en contrasena_pendiente SOLO hasta enviarla (se borra al sincronizar).
 * Las columnas de sincronización funcionan igual que en [EquipoEntity].
 */
@Entity(
    tableName = "equipos_computo",
    indices = [Index(value = ["id_sala"]), Index(value = ["estado_sync"])]
)
data class EquipoComputoEntity(
    @PrimaryKey @ColumnInfo(name = "inventario") val inventario: String,
    @ColumnInfo(name = "id_servidor") val idServidor: Int?,
    @ColumnInfo(name = "serie") val serie: String,
    @ColumnInfo(name = "articulo") val articulo: String,
    @ColumnInfo(name = "marca") val marca: String,
    @ColumnInfo(name = "modelo") val modelo: String,
    @ColumnInfo(name = "caracteristicas") val caracteristicas: String,
    @ColumnInfo(name = "estado") val estado: String,
    @ColumnInfo(name = "id_sala") val idSala: Int?,
    @ColumnInfo(name = "procesador") val procesador: String,
    @ColumnInfo(name = "ram") val ram: String,
    @ColumnInfo(name = "almacenamiento") val almacenamiento: String,
    @ColumnInfo(name = "especificaciones_hardware") val especificacionesHardware: String,
    @ColumnInfo(name = "sistema_operativo") val sistemaOperativo: String,
    @ColumnInfo(name = "software_instalado") val softwareInstalado: String,
    @ColumnInfo(name = "estado_computadora") val estadoComputadora: String,
    @ColumnInfo(name = "estado_cpu") val estadoCpu: String,
    @ColumnInfo(name = "estado_monitor") val estadoMonitor: String,
    @ColumnInfo(name = "estado_teclado") val estadoTeclado: String,
    @ColumnInfo(name = "estado_mouse") val estadoMouse: String,
    @ColumnInfo(name = "estado_cable_ethernet") val estadoCableEthernet: String,
    @ColumnInfo(name = "observaciones") val observaciones: String,
    @ColumnInfo(name = "tiene_contrasena") val tieneContrasena: Boolean,
    @ColumnInfo(name = "tiene_ultima_contrasena") val tieneUltimaContrasena: Boolean,
    @ColumnInfo(name = "contrasena_pendiente") val contrasenaPendiente: String?,
    @ColumnInfo(name = "borrar_contrasena") val borrarContrasena: Boolean,
    @ColumnInfo(name = "motivo_cambio") val motivoCambio: String?,
    @ColumnInfo(name = "actualizado_por") val actualizadoPor: String?,
    @ColumnInfo(name = "fecha_registro") val fechaRegistro: Long,
    @ColumnInfo(name = "fecha_actualizacion_local") val fechaActualizacionLocal: Long,
    @ColumnInfo(name = "fecha_actualizacion_servidor") val fechaActualizacionServidor: String?,
    @ColumnInfo(name = "existe_en_servidor") val existeEnServidor: Boolean,
    @ColumnInfo(name = "estado_sync") val estadoSync: EstadoSync,
    @ColumnInfo(name = "intentos") val intentos: Int,
    @ColumnInfo(name = "ultimo_error") val ultimoError: String?,
    @ColumnInfo(name = "version_servidor") val versionServidor: String?,
)

@Dao
interface SalaDao {
    @Query("SELECT * FROM salas ORDER BY codigo")
    fun observarTodas(): Flow<List<SalaEntity>>

    /** Borra las salas que vinieron del servidor; las locales (id negativo) se conservan. */
    @Query("DELETE FROM salas WHERE id_sala >= 0")
    suspend fun borrarDelServidor()

    @Query("SELECT * FROM salas WHERE id_sala < 0")
    suspend fun obtenerLocales(): List<SalaEntity>

    @Query("SELECT * FROM salas WHERE UPPER(TRIM(codigo)) = UPPER(TRIM(:codigo)) LIMIT 1")
    suspend fun buscarPorCodigo(codigo: String): SalaEntity?

    @Query("SELECT MIN(id_sala) FROM salas")
    suspend fun idMinimo(): Int?

    @Query("DELETE FROM salas WHERE id_sala = :idSala")
    suspend fun eliminar(idSala: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(salas: List<SalaEntity>)
}

@Dao
interface EquipoComputoDao {

    @Query("SELECT * FROM equipos_computo WHERE id_sala = :idSala ORDER BY inventario")
    fun observarPorSala(idSala: Int): Flow<List<EquipoComputoEntity>>

    @Query("SELECT * FROM equipos_computo WHERE id_sala IS NULL ORDER BY inventario")
    fun observarSinSala(): Flow<List<EquipoComputoEntity>>

    @Query("SELECT * FROM equipos_computo WHERE estado_sync != 'SINCRONIZADO' ORDER BY fecha_actualizacion_local DESC")
    fun observarNoSincronizados(): Flow<List<EquipoComputoEntity>>

    @Query("SELECT estado_sync AS estado, COUNT(*) AS total FROM equipos_computo WHERE estado_sync != 'SINCRONIZADO' GROUP BY estado_sync")
    fun observarConteos(): Flow<List<ConteoEstado>>

    @Query(
        "SELECT * FROM equipos_computo WHERE estado_sync IN ('PENDIENTE_CREAR', 'PENDIENTE_ACTUALIZAR') " +
            "ORDER BY fecha_actualizacion_local ASC"
    )
    suspend fun obtenerPendientesDeEnvio(): List<EquipoComputoEntity>

    @Query("SELECT * FROM equipos_computo WHERE inventario = :inventario LIMIT 1")
    suspend fun obtener(inventario: String): EquipoComputoEntity?

    @Query("SELECT inventario FROM equipos_computo WHERE estado_sync = 'SINCRONIZADO'")
    suspend fun inventariosSincronizados(): List<String>

    @Upsert
    suspend fun upsert(equipo: EquipoComputoEntity)

    @Query("DELETE FROM equipos_computo WHERE inventario = :inventario")
    suspend fun eliminar(inventario: String)

    /**
     * Envío confirmado. En SQLite todas las expresiones del SET usan los valores ANTERIORES de
     * la fila, por eso tiene_contrasena se calcula con la contrasena_pendiente que se envió.
     */
    @Query(
        "UPDATE equipos_computo SET estado_sync = 'SINCRONIZADO', existe_en_servidor = 1, " +
            "id_servidor = COALESCE(:idServidor, id_servidor), intentos = 0, ultimo_error = NULL, " +
            "version_servidor = NULL, fecha_actualizacion_servidor = :fechaServidor, motivo_cambio = NULL, " +
            "tiene_ultima_contrasena = CASE WHEN (contrasena_pendiente IS NOT NULL OR borrar_contrasena = 1) " +
            "AND tiene_contrasena = 1 THEN 1 ELSE tiene_ultima_contrasena END, " +
            "tiene_contrasena = CASE WHEN contrasena_pendiente IS NOT NULL THEN 1 " +
            "WHEN borrar_contrasena = 1 THEN 0 ELSE tiene_contrasena END, " +
            "contrasena_pendiente = NULL, borrar_contrasena = 0 " +
            "WHERE inventario = :inventario AND fecha_actualizacion_local = :fechaLocal"
    )
    suspend fun marcarSincronizado(inventario: String, fechaLocal: Long, fechaServidor: String?, idServidor: Int?): Int

    /** El registro ya existe en el servidor (con ese id); queda como edición pendiente. */
    @Query(
        "UPDATE equipos_computo SET existe_en_servidor = 1, id_servidor = :idServidor, " +
            "fecha_actualizacion_servidor = COALESCE(:fechaServidor, fecha_actualizacion_servidor), " +
            "estado_sync = CASE WHEN estado_sync = 'PENDIENTE_CREAR' THEN 'PENDIENTE_ACTUALIZAR' ELSE estado_sync END " +
            "WHERE inventario = :inventario"
    )
    suspend fun marcarExisteEnServidor(inventario: String, idServidor: Int?, fechaServidor: String?)

    @Query(
        "UPDATE equipos_computo SET estado_sync = :estado, ultimo_error = :error, version_servidor = :versionServidor " +
            "WHERE inventario = :inventario AND fecha_actualizacion_local = :fechaLocal"
    )
    suspend fun marcarProblema(
        inventario: String,
        fechaLocal: Long,
        estado: EstadoSync,
        error: String?,
        versionServidor: String?,
    ): Int

    @Query("UPDATE equipos_computo SET intentos = intentos + 1, ultimo_error = :error WHERE inventario = :inventario")
    suspend fun registrarFalloTransitorio(inventario: String, error: String?)

    /** Cambia una sala local por la del servidor en todos los equipos que la usan. */
    @Query("UPDATE equipos_computo SET id_sala = :nueva WHERE id_sala = :anterior")
    suspend fun reasignarSala(anterior: Int, nueva: Int)
}
