package com.example.inventory.domain.repository

import com.example.inventory.domain.model.CambioContrasena
import com.example.inventory.domain.model.ContrasenasEquipo
import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.EquipoComputo
import com.example.inventory.domain.model.Sala
import kotlinx.coroutines.flow.Flow

/** Salas y equipos de cómputo. Mismo modelo offline-first que [EquipoRepository]. */
interface EquipoComputoRepository {
    fun observarSalas(): Flow<List<Sala>>

    /**
     * Crea una sala solo en el dispositivo (para trabajar sin servidor). Al sincronizar se
     * empareja con la sala del servidor que tenga el mismo código.
     */
    suspend fun crearSalaLocal(codigo: String, nombre: String, ubicacion: String): Result<Sala>

    /** @param idSala null = equipos sin sala asignada. */
    fun observarPorSala(idSala: Int?): Flow<List<EquipoComputo>>

    fun observarNoSincronizados(): Flow<List<EquipoComputo>>

    suspend fun obtener(inventario: String): EquipoComputo?

    suspend fun crear(datos: DatosComputo, contrasena: CambioContrasena, motivo: String): Result<Unit>

    suspend fun actualizar(
        inventarioOriginal: String,
        datos: DatosComputo,
        contrasena: CambioContrasena,
        motivo: String,
    ): Result<Unit>

    /** Requiere conexión: el servidor descifra y registra la consulta en la bitácora. */
    suspend fun verContrasenas(inventario: String): Result<ContrasenasEquipo>

    suspend fun conservarVersionLocal(inventario: String): Result<Unit>
    suspend fun usarVersionServidor(inventario: String): Result<Unit>
    suspend fun reintentar(inventario: String): Result<Unit>
}
