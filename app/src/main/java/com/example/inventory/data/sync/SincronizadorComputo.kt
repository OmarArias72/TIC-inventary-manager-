package com.example.inventory.data.sync

import com.example.inventory.data.local.EjecutorTransacciones
import com.example.inventory.data.local.EquipoComputoDao
import com.example.inventory.data.local.EquipoComputoEntity
import com.example.inventory.data.local.SalaDao
import com.example.inventory.data.mapper.ComputoMapper
import com.example.inventory.data.remote.ApiErrores
import com.example.inventory.data.remote.ComputoApi
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.usecase.NormalizadorComputo
import com.google.gson.Gson
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

/** Parte de la sincronización que corresponde a salas y equipos de cómputo. La usa [SyncManager]. */
interface SincronizadorComputo {
    /** Envía altas y ediciones pendientes. Lanza [SinSesionException], [TransitorioException] o IOException. */
    suspend fun enviar(): ConteoEnvio

    /** Descarga salas y equipos de cómputo y actualiza los registros sin cambios locales. */
    suspend fun descargar()
}

/**
 * Mismas reglas que los equipos generales:
 * - Alta repetida (409) con el mismo contenido = sincronizado (idempotente); distinto = CONFLICTO.
 * - Edición: si el servidor cambió desde la última sincronización = CONFLICTO.
 * - La contraseña pendiente solo se borra del dispositivo cuando el servidor confirma.
 * - Nunca se borra un cambio local por un fallo de red.
 */
class SincronizadorComputoImpl @Inject constructor(
    private val dao: EquipoComputoDao,
    private val salaDao: SalaDao,
    private val api: ComputoApi,
    private val tx: EjecutorTransacciones,
    private val mapper: ComputoMapper,
    private val gson: Gson,
) : SincronizadorComputo {

    override suspend fun enviar(): ConteoEnvio {
        // Salas creadas sin servidor: primero se emparejan con las del servidor por código.
        if (salaDao.obtenerLocales().isNotEmpty()) {
            val salas = api.salas()
            if (salas.code() == 401) throw SinSesionException()
            if (!salas.isSuccessful) throw TransitorioException("No se pudieron descargar las salas (código ${salas.code()}).")
            tx.ejecutar { emparejarSalasLocales(salas.body().orEmpty().mapNotNull(mapper::salaEntidad)) }
        }

        var conteo = ConteoEnvio()
        for (p in dao.obtenerPendientesDeEnvio()) {
            val salaLocal = p.idSala?.takeIf { it < 0 }
            if (salaLocal != null) {
                // Sigue pendiente: se enviará en cuanto exista en el servidor una sala con el mismo código.
                val codigo = salaDao.obtenerLocales().firstOrNull { it.idSala == salaLocal }?.codigo ?: "local"
                dao.registrarFalloTransitorio(
                    p.inventario,
                    "La sala $codigo solo existe en el dispositivo. Pide al administrador que la dé de alta " +
                        "con el mismo código o elige otra sala.",
                )
                conteo = conteo.copy(errores = conteo.errores + 1)
                continue
            }
            val resultado = try {
                if (p.estadoSync == EstadoSync.PENDIENTE_CREAR) enviarAlta(p) else enviarEdicion(p)
            } catch (e: IOException) {
                dao.registrarFalloTransitorio(p.inventario, "Sin conexión con el servidor.")
                throw e
            }
            conteo = when (resultado) {
                Envio.OK -> conteo.copy(enviados = conteo.enviados + 1)
                Envio.CONFLICTO -> conteo.copy(conflictos = conteo.conflictos + 1)
                Envio.ERROR -> conteo.copy(errores = conteo.errores + 1)
            }
        }
        return conteo
    }

    private suspend fun enviarAlta(p: EquipoComputoEntity): Envio {
        val r = api.crear(mapper.aRequest(p, idServidor = null))
        return when {
            r.isSuccessful -> {
                val id = r.body()?.idEquipoComputo
                if (dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, null, id) == 0) {
                    dao.marcarExisteEnServidor(p.inventario, id, null)
                }
                Envio.OK
            }
            r.code() == 409 -> resolverAltaDuplicada(p)
            else -> fallo(p, r)
        }
    }

    private suspend fun resolverAltaDuplicada(p: EquipoComputoEntity): Envio {
        val r = api.obtenerPorInventario(p.inventario)
        val servidor = r.body()
        if (!r.isSuccessful || servidor == null) return fallo(p, r)
        val mismo = NormalizadorComputo.mismoContenido(mapper.datos(p), mapper.datos(servidor))
        return when {
            mismo && p.contrasenaPendiente == null && !p.borrarContrasena -> {
                if (dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, servidor.fechaActualizacion, servidor.idEquipoComputo) == 0) {
                    dao.marcarExisteEnServidor(p.inventario, servidor.idEquipoComputo, servidor.fechaActualizacion)
                }
                Envio.OK
            }
            mismo -> {
                // Mismos datos, pero falta enviar el cambio de contraseña: se envía como edición.
                dao.marcarExisteEnServidor(p.inventario, servidor.idEquipoComputo, servidor.fechaActualizacion)
                val actualizado = dao.obtener(p.inventario) ?: return Envio.OK
                enviarEdicion(actualizado)
            }
            else -> {
                dao.marcarProblema(
                    p.inventario, p.fechaActualizacionLocal, EstadoSync.CONFLICTO,
                    "Ya existía un equipo de cómputo con este inventario en el servidor, con datos distintos.",
                    mapper.aJson(servidor),
                )
                Envio.CONFLICTO
            }
        }
    }

    private suspend fun enviarEdicion(p: EquipoComputoEntity): Envio {
        val actual = if (p.idServidor != null) api.obtener(p.idServidor) else api.obtenerPorInventario(p.inventario)
        if (actual.code() == 404) {
            dao.marcarProblema(
                p.inventario, p.fechaActualizacionLocal, EstadoSync.CONFLICTO,
                "Otra persona eliminó este equipo de cómputo del servidor.", null,
            )
            return Envio.CONFLICTO
        }
        val servidor = actual.body()
        if (!actual.isSuccessful || servidor == null) return fallo(p, actual)
        val id = servidor.idEquipoComputo ?: p.idServidor

        val base = p.fechaActualizacionServidor
        if (base != null && servidor.fechaActualizacion != base) {
            val mismo = NormalizadorComputo.mismoContenido(mapper.datos(p), mapper.datos(servidor))
            if (!mismo) {
                dao.marcarProblema(
                    p.inventario, p.fechaActualizacionLocal, EstadoSync.CONFLICTO,
                    "Otra persona modificó este equipo de cómputo en el servidor.", mapper.aJson(servidor),
                )
                return Envio.CONFLICTO
            }
            if (p.contrasenaPendiente == null && !p.borrarContrasena) {
                dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, servidor.fechaActualizacion, id)
                return Envio.OK
            }
        }
        if (id == null) return fallo(p, actual)

        val r = api.actualizar(id, mapper.aRequest(p, id))
        if (!r.isSuccessful) return fallo(p, r)
        dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, null, id)
        return Envio.OK
    }

    private suspend fun fallo(p: EquipoComputoEntity, r: Response<*>): Envio {
        val codigo = r.code()
        if (codigo == 401) throw SinSesionException()
        val mensaje = ApiErrores.mensaje(gson, r)
        if (ApiErrores.esTransitorio(codigo)) {
            dao.registrarFalloTransitorio(p.inventario, mensaje)
            throw TransitorioException(mensaje)
        }
        dao.marcarProblema(p.inventario, p.fechaActualizacionLocal, EstadoSync.ERROR, mensaje, null)
        return Envio.ERROR
    }

    /**
     * Cada sala local cuyo código exista en el servidor se reemplaza por la del servidor
     * (y se actualizan los equipos que la usaban). Debe llamarse dentro de una transacción.
     */
    private suspend fun emparejarSalasLocales(servidor: List<com.example.inventory.data.local.SalaEntity>) {
        val porCodigo = servidor.associateBy { it.codigo.trim().uppercase() }
        for (local in salaDao.obtenerLocales()) {
            val enServidor = porCodigo[local.codigo.trim().uppercase()] ?: continue
            salaDao.insertar(listOf(enServidor))
            dao.reasignarSala(local.idSala, enServidor.idSala)
            salaDao.eliminar(local.idSala)
        }
    }

    override suspend fun descargar() {
        val salas = api.salas()
        if (salas.code() == 401) throw SinSesionException()
        if (!salas.isSuccessful) throw TransitorioException("No se pudieron descargar las salas (código ${salas.code()}).")
        val equipos = api.listar()
        if (equipos.code() == 401) throw SinSesionException()
        if (!equipos.isSuccessful) throw TransitorioException("No se pudieron descargar los equipos de cómputo (código ${equipos.code()}).")

        val listaSalas = salas.body().orEmpty().mapNotNull(mapper::salaEntidad)
        val lista = equipos.body().orEmpty().filter { !it.inventario.isNullOrBlank() }
        val enServidor = lista.mapTo(HashSet()) { it.inventario!! }
        tx.ejecutar {
            salaDao.borrarDelServidor()
            salaDao.insertar(listaSalas)
            emparejarSalasLocales(listaSalas)
            for (dto in lista) {
                val local = dao.obtener(dto.inventario!!)
                if (local == null || local.estadoSync == EstadoSync.SINCRONIZADO) {
                    dao.upsert(mapper.entidadSincronizada(dto))
                }
            }
            dao.inventariosSincronizados()
                .filter { it !in enServidor }
                .forEach { dao.eliminar(it) }
        }
    }
}
