package com.example.inventory.data.repository

import com.example.inventory.data.local.EjecutorTransacciones
import com.example.inventory.data.local.EquipoComputoDao
import com.example.inventory.data.local.SalaDao
import com.example.inventory.data.mapper.ComputoMapper
import com.example.inventory.data.remote.ApiErrores
import com.example.inventory.data.remote.ComputoApi
import com.example.inventory.domain.model.CambioContrasena
import com.example.inventory.domain.model.ContrasenasEquipo
import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.EquipoComputo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ReglaNegocioException
import com.example.inventory.domain.model.Sala
import com.example.inventory.domain.repository.EquipoComputoRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Room es la fuente de verdad; SincronizadorComputo envía los cambios. */
@Singleton
class EquipoComputoRepositoryImpl @Inject constructor(
    private val dao: EquipoComputoDao,
    private val salaDao: SalaDao,
    private val api: ComputoApi,
    private val tx: EjecutorTransacciones,
    private val mapper: ComputoMapper,
    private val gson: Gson,
) : EquipoComputoRepository {

    override fun observarSalas(): Flow<List<Sala>> =
        salaDao.observarTodas().map { lista -> lista.map(mapper::sala) }

    override suspend fun crearSalaLocal(codigo: String, nombre: String, ubicacion: String): Result<Sala> = comoResultado {
        val c = codigo.trim().uppercase()
        if (c.isEmpty()) throw ReglaNegocioException("El código de la sala es obligatorio.")
        if (c.length > 50) throw ReglaNegocioException("El código admite máximo 50 caracteres.")
        tx.ejecutar {
            salaDao.buscarPorCodigo(c)?.let { throw ReglaNegocioException("Ya existe la sala ${it.codigo}.") }
            val id = minOf(salaDao.idMinimo() ?: 0, 0) - 1
            val entidad = com.example.inventory.data.local.SalaEntity(id, c, nombre.trim(), ubicacion.trim())
            salaDao.insertar(listOf(entidad))
            mapper.sala(entidad)
        }
    }

    override fun observarPorSala(idSala: Int?): Flow<List<EquipoComputo>> =
        (if (idSala == null) dao.observarSinSala() else dao.observarPorSala(idSala))
            .map { lista -> lista.map(mapper::aDominio) }

    override fun observarNoSincronizados(): Flow<List<EquipoComputo>> =
        dao.observarNoSincronizados().map { lista -> lista.map(mapper::aDominio) }

    override suspend fun obtener(inventario: String): EquipoComputo? =
        dao.obtener(inventario)?.let(mapper::aDominio)

    override suspend fun crear(datos: DatosComputo, contrasena: CambioContrasena, motivo: String): Result<Unit> = comoResultado {
        tx.ejecutar {
            val existente = dao.obtener(datos.base.inventario)
            if (existente != null) {
                throw ReglaNegocioException("Ya hay un equipo de cómputo con el inventario ${datos.base.inventario}.")
            }
            val nuevo = mapper.conDatos(null, datos, System.currentTimeMillis())
            dao.upsert(conContrasena(nuevo, contrasena).copy(motivoCambio = motivo.ifBlank { null }))
        }
    }

    override suspend fun actualizar(
        inventarioOriginal: String,
        datos: DatosComputo,
        contrasena: CambioContrasena,
        motivo: String,
    ): Result<Unit> = comoResultado {
        tx.ejecutar {
            val actual = dao.obtener(inventarioOriginal)
                ?: throw ReglaNegocioException("El equipo ya no existe en el dispositivo.")
            if (actual.estadoSync == EstadoSync.CONFLICTO) {
                throw ReglaNegocioException("Este equipo tiene un conflicto. Resuélvelo primero en Sincronización.")
            }
            val cambiaInventario = datos.base.inventario != actual.inventario
            if (cambiaInventario) {
                // El backend no permite cambiar el inventario de un equipo existente.
                if (actual.existeEnServidor) {
                    throw ReglaNegocioException("No se puede cambiar el No. de inventario de un equipo que ya está en el servidor.")
                }
                if (dao.obtener(datos.base.inventario) != null) {
                    throw ReglaNegocioException("Ya hay un equipo de cómputo con el inventario ${datos.base.inventario}.")
                }
                dao.eliminar(actual.inventario)
            }
            val sinCambios = actual.estadoSync == EstadoSync.SINCRONIZADO &&
                mapper.datos(actual) == datos && contrasena == CambioContrasena.SinCambio
            if (sinCambios) return@ejecutar

            val editado = conContrasena(mapper.conDatos(actual, datos, System.currentTimeMillis()), contrasena)
            dao.upsert(
                editado.copy(
                    estadoSync = if (actual.existeEnServidor) EstadoSync.PENDIENTE_ACTUALIZAR else EstadoSync.PENDIENTE_CREAR,
                    // Si hay varias ediciones sin enviar se conservan las notas de todas.
                    motivoCambio = listOfNotNull(actual.motivoCambio?.takeIf { actual.estadoSync.esPendienteDeEnvio }, motivo.ifBlank { null })
                        .joinToString(" | ").ifBlank { null },
                    intentos = 0,
                    ultimoError = null,
                )
            )
        }
    }

    private fun conContrasena(e: com.example.inventory.data.local.EquipoComputoEntity, cambio: CambioContrasena) =
        when (cambio) {
            CambioContrasena.SinCambio -> e
            is CambioContrasena.Nueva -> e.copy(contrasenaPendiente = cambio.valor, borrarContrasena = false)
            CambioContrasena.Borrar -> e.copy(contrasenaPendiente = null, borrarContrasena = true)
        }

    override suspend fun verContrasenas(inventario: String): Result<ContrasenasEquipo> = comoResultado {
        val equipo = dao.obtener(inventario) ?: throw ReglaNegocioException("El equipo ya no existe.")
        val id = equipo.idServidor
            ?: throw ReglaNegocioException("El equipo aún no se ha enviado al servidor; sincroniza primero.")
        val r = try {
            api.contrasenas(id)
        } catch (e: IOException) {
            throw ReglaNegocioException("Se necesita conexión con el servidor para ver las contraseñas.")
        }
        val dto = r.body()
        if (!r.isSuccessful || dto == null) throw ReglaNegocioException(ApiErrores.mensaje(gson, r))
        ContrasenasEquipo(dto.contrasena, dto.ultimaContrasena, dto.fechaCambioContrasena)
    }

    override suspend fun conservarVersionLocal(inventario: String): Result<Unit> = comoResultado {
        tx.ejecutar {
            val actual = dao.obtener(inventario) ?: throw ReglaNegocioException("El equipo ya no existe.")
            if (actual.estadoSync != EstadoSync.CONFLICTO) throw ReglaNegocioException("Este equipo no tiene conflicto.")
            val servidor = mapper.versionServidor(actual)
            val ahora = System.currentTimeMillis()
            dao.upsert(
                if (servidor == null) {
                    // Se eliminó en el servidor: se vuelve a crear.
                    actual.copy(
                        existeEnServidor = false, idServidor = null, estadoSync = EstadoSync.PENDIENTE_CREAR,
                        fechaActualizacionServidor = null, versionServidor = null, ultimoError = null,
                        intentos = 0, fechaActualizacionLocal = ahora,
                    )
                } else {
                    actual.copy(
                        existeEnServidor = true, idServidor = servidor.idEquipoComputo ?: actual.idServidor,
                        estadoSync = EstadoSync.PENDIENTE_ACTUALIZAR,
                        fechaActualizacionServidor = servidor.fechaActualizacion,
                        versionServidor = null, ultimoError = null, intentos = 0, fechaActualizacionLocal = ahora,
                    )
                }
            )
        }
    }

    override suspend fun usarVersionServidor(inventario: String): Result<Unit> = comoResultado {
        val actual = dao.obtener(inventario) ?: throw ReglaNegocioException("El equipo ya no existe.")
        val guardada = mapper.versionServidor(actual)
        when {
            guardada != null -> dao.upsert(mapper.entidadSincronizada(guardada))
            actual.estadoSync == EstadoSync.CONFLICTO -> dao.eliminar(inventario)
            !actual.existeEnServidor -> dao.eliminar(inventario)
            else -> {
                val r = try {
                    if (actual.idServidor != null) api.obtener(actual.idServidor) else api.obtenerPorInventario(inventario)
                } catch (e: IOException) {
                    throw ReglaNegocioException("Se necesita conexión con el servidor para descargar su versión.")
                }
                val dto = r.body()
                when {
                    r.isSuccessful && dto != null -> dao.upsert(mapper.entidadSincronizada(dto))
                    r.code() == 404 -> dao.eliminar(inventario)
                    else -> throw ReglaNegocioException("No se pudo descargar la versión del servidor (código ${r.code()}).")
                }
            }
        }
    }

    override suspend fun reintentar(inventario: String): Result<Unit> = comoResultado {
        val actual = dao.obtener(inventario) ?: throw ReglaNegocioException("El equipo ya no existe.")
        dao.upsert(
            actual.copy(
                estadoSync = if (actual.existeEnServidor) EstadoSync.PENDIENTE_ACTUALIZAR else EstadoSync.PENDIENTE_CREAR,
                intentos = 0,
                ultimoError = null,
            )
        )
    }
}
