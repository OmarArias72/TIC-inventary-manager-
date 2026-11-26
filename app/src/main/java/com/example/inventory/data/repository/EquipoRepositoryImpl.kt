package com.example.inventory.data.repository

import com.example.inventory.data.local.EjecutorTransacciones
import com.example.inventory.data.local.EquipoDao
import com.example.inventory.data.mapper.EquipoMapper
import com.example.inventory.data.remote.BusquedaRequestDto
import com.example.inventory.data.remote.EquipoRemotoDto
import com.example.inventory.data.remote.InventarioApi
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ReglaNegocioException
import com.example.inventory.domain.model.ResultadoBusqueda
import com.example.inventory.domain.repository.EquipoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Patrón Repository con Room como fuente de verdad (offline-first):
 * - Las pantallas siempre leen de Room.
 * - Las escrituras se guardan en Room marcadas como pendientes; SyncManager las envía.
 * - Solo la búsqueda por código consulta el servidor directamente (si el equipo no está en el dispositivo).
 */
@Singleton
class EquipoRepositoryImpl @Inject constructor(
    private val dao: EquipoDao,
    private val api: InventarioApi,
    private val tx: EjecutorTransacciones,
    private val mapper: EquipoMapper,
) : EquipoRepository {

    override fun observarEquipos(): Flow<List<Equipo>> =
        dao.observarVisibles().map { lista -> lista.map(mapper::aDominio) }

    override fun observarNoSincronizados(): Flow<List<Equipo>> =
        dao.observarNoSincronizados().map { lista -> lista.map(mapper::aDominio) }

    override suspend fun obtener(inventario: String): Equipo? =
        dao.obtener(inventario)?.let(mapper::aDominio)

    override suspend fun crear(datos: DatosEquipo): Result<Unit> = comoResultado {
        tx.ejecutar {
            val existente = dao.obtener(datos.inventario)
            if (existente != null) {
                throw ReglaNegocioException(
                    if (existente.eliminado) "El equipo ${datos.inventario} está pendiente de eliminarse; espera a que se sincronice."
                    else "Ya existe un equipo con el inventario ${datos.inventario}."
                )
            }
            dao.upsert(mapper.nuevaEntidad(datos, System.currentTimeMillis()))
        }
    }

    override suspend fun actualizar(inventarioOriginal: String, datos: DatosEquipo): Result<Unit> = comoResultado {
        tx.ejecutar {
            val actual = dao.obtener(inventarioOriginal)
                ?: throw ReglaNegocioException("El equipo ya no existe en el dispositivo.")
            if (actual.eliminado) throw ReglaNegocioException("El equipo está marcado para eliminarse.")
            if (actual.estadoSync == EstadoSync.CONFLICTO) {
                throw ReglaNegocioException("Este equipo tiene un conflicto. Resuélvelo primero en Sincronización.")
            }
            if (actual.estadoSync == EstadoSync.SINCRONIZADO && mapper.datos(actual) == datos) return@ejecutar

            val cambiaInventario = datos.inventario != actual.inventario
            if (cambiaInventario) {
                if (actual.existeEnServidor) {
                    throw ReglaNegocioException("No se puede cambiar el No. de inventario de un equipo que ya está en el servidor.")
                }
                if (dao.obtener(datos.inventario) != null) {
                    throw ReglaNegocioException("Ya existe un equipo con el inventario ${datos.inventario}.")
                }
                dao.eliminar(actual.inventario)
            }
            dao.upsert(
                actual.copy(
                    inventario = datos.inventario,
                    serie = datos.serie,
                    articulo = datos.articulo,
                    marca = datos.marca,
                    modelo = datos.modelo,
                    caracteristicas = datos.caracteristicas,
                    estado = datos.estado.name,
                    fechaActualizacionLocal = System.currentTimeMillis(),
                    estadoSync = if (actual.existeEnServidor) EstadoSync.PENDIENTE_ACTUALIZAR else EstadoSync.PENDIENTE_CREAR,
                    intentos = 0,
                    ultimoError = null,
                )
            )
        }
    }

    override suspend fun eliminar(inventario: String): Result<Unit> = comoResultado {
        tx.ejecutar {
            val actual = dao.obtener(inventario) ?: return@ejecutar
            if (!actual.existeEnServidor) {
                // Nunca llegó al servidor: se borra directamente.
                dao.eliminar(inventario)
            } else {
                // Borrado lógico: se oculta y se borra cuando el servidor lo confirme.
                dao.upsert(
                    actual.copy(
                        eliminado = true,
                        estadoSync = EstadoSync.PENDIENTE_ELIMINAR,
                        fechaActualizacionLocal = System.currentTimeMillis(),
                        intentos = 0,
                        ultimoError = null,
                    )
                )
            }
        }
    }

    override suspend fun buscarPorCodigo(codigo: String): ResultadoBusqueda {
        dao.buscarPorCodigo(codigo)?.let { return ResultadoBusqueda.Encontrado(mapper.aDominio(it), desdeServidor = false) }
        return try {
            val porInventario = api.obtener(codigo)
            val encontrado: EquipoRemotoDto? = when {
                porInventario.isSuccessful -> porInventario.body()
                porInventario.code() == 404 -> {
                    // ¿Es un número de serie? El backend busca "contiene", aquí se exige coincidencia exacta.
                    val porSerie = api.buscarPorSerie(BusquedaRequestDto(codigo))
                    if (porSerie.isSuccessful) {
                        porSerie.body().orEmpty().firstOrNull { it.serie.equals(codigo, ignoreCase = true) }
                    } else {
                        null
                    }
                }
                porInventario.code() == 401 -> return ResultadoBusqueda.SoloLocal("Inicia sesión para buscar en el servidor.")
                else -> return ResultadoBusqueda.SoloLocal("El servidor respondió con el código ${porInventario.code()}.")
            }
            if (encontrado?.inventario.isNullOrBlank()) {
                ResultadoBusqueda.NoEncontrado
            } else {
                val entidad = guardarDescargado(encontrado!!)
                ResultadoBusqueda.Encontrado(mapper.aDominio(entidad), desdeServidor = true)
            }
        } catch (e: IOException) {
            ResultadoBusqueda.SoloLocal("Sin conexión con el servidor; solo se buscó en el dispositivo.")
        }
    }

    override suspend fun conservarVersionLocal(inventario: String): Result<Unit> = comoResultado {
        tx.ejecutar {
            val actual = dao.obtener(inventario) ?: throw ReglaNegocioException("El equipo ya no existe.")
            if (actual.estadoSync != EstadoSync.CONFLICTO) throw ReglaNegocioException("Este equipo no tiene conflicto.")
            val servidor = mapper.versionServidor(actual)
            val ahora = System.currentTimeMillis()
            if (servidor == null) {
                // Se eliminó en el servidor y el usuario quiere conservarlo: se vuelve a crear.
                dao.upsert(
                    actual.copy(
                        existeEnServidor = false, eliminado = false, estadoSync = EstadoSync.PENDIENTE_CREAR,
                        fechaActualizacionServidor = null, versionServidor = null, ultimoError = null,
                        intentos = 0, fechaActualizacionLocal = ahora,
                    )
                )
            } else {
                // Se toma la versión actual del servidor como base: el envío ya no será conflicto.
                dao.upsert(
                    actual.copy(
                        existeEnServidor = true,
                        estadoSync = if (actual.eliminado) EstadoSync.PENDIENTE_ELIMINAR else EstadoSync.PENDIENTE_ACTUALIZAR,
                        fechaActualizacionServidor = servidor.fechaActualizacion,
                        versionServidor = null, ultimoError = null, intentos = 0, fechaActualizacionLocal = ahora,
                    )
                )
            }
        }
    }

    override suspend fun usarVersionServidor(inventario: String): Result<Unit> = comoResultado {
        val actual = dao.obtener(inventario) ?: throw ReglaNegocioException("El equipo ya no existe.")
        val guardada = mapper.versionServidor(actual)
        when {
            guardada != null -> dao.upsert(mapper.entidadSincronizada(guardada))
            actual.estadoSync == EstadoSync.CONFLICTO -> dao.eliminar(inventario) // se eliminó en el servidor
            !actual.existeEnServidor -> dao.eliminar(inventario) // alta rechazada: se descarta
            else -> {
                // Error sin copia del servidor: hay que descargarla (requiere conexión).
                val r = try {
                    api.obtener(inventario)
                } catch (e: IOException) {
                    throw ReglaNegocioException("Se necesita conexión con el servidor para descargar su versión.")
                }
                when {
                    r.isSuccessful && r.body() != null -> dao.upsert(mapper.entidadSincronizada(r.body()!!))
                    r.code() == 404 -> dao.eliminar(inventario)
                    else -> throw ReglaNegocioException("No se pudo descargar la versión del servidor (código ${r.code()}).")
                }
            }
        }
    }

    override suspend fun reintentar(inventario: String): Result<Unit> = comoResultado {
        val actual = dao.obtener(inventario) ?: throw ReglaNegocioException("El equipo ya no existe.")
        val estado = when {
            actual.eliminado -> EstadoSync.PENDIENTE_ELIMINAR
            actual.existeEnServidor -> EstadoSync.PENDIENTE_ACTUALIZAR
            else -> EstadoSync.PENDIENTE_CREAR
        }
        dao.upsert(actual.copy(estadoSync = estado, intentos = 0, ultimoError = null))
    }

    /** Guarda en el dispositivo un equipo descargado, sin pisar cambios locales pendientes. */
    private suspend fun guardarDescargado(dto: EquipoRemotoDto) = tx.ejecutar {
        val local = dao.obtener(dto.inventario!!)
        if (local == null || local.estadoSync == EstadoSync.SINCRONIZADO) {
            val entidad = mapper.entidadSincronizada(dto)
            dao.upsert(entidad)
            entidad
        } else {
            local
        }
    }

}
