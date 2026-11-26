package com.example.inventory.data.sync

import com.example.inventory.data.local.EjecutorTransacciones
import com.example.inventory.data.local.EquipoDao
import com.example.inventory.data.local.EquipoEntity
import com.example.inventory.data.mapper.EquipoMapper
import com.example.inventory.data.remote.ApiErrores
import com.example.inventory.data.remote.AuthApi
import com.example.inventory.data.remote.InventarioApi
import com.example.inventory.data.remote.ServidorNoConfiguradoException
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ResultadoSincronizacion
import com.example.inventory.domain.usecase.NormalizadorEquipo
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de sincronización. Lo ejecuta SyncWorker (WorkManager).
 *
 * Pasos:
 * 1. Verificar que el backend responde y que hay sesión (GET /api/auth/me).
 *    Tener Internet no garantiza que el servidor esté disponible.
 * 2. ENVIAR los cambios locales pendientes, uno por uno, en el orden en que se hicieron.
 * 3. DESCARGAR la lista del servidor y actualizar los registros que no tienen cambios locales.
 *
 * Reglas de integridad:
 * - Nunca se borra un cambio local por un fallo de red: el registro sigue pendiente.
 * - Alta repetida (409): si el servidor ya tiene exactamente lo mismo se da por sincronizado
 *   (idempotencia, p. ej. un reintento tras perder la respuesta); si es distinto → CONFLICTO.
 * - Edición: si el servidor cambió desde la última sincronización → CONFLICTO (no se sobrescribe).
 * - Eliminación: 404 se toma como éxito (ya no existe).
 * - Los registros solo se marcan sincronizados si no se editaron mientras se enviaban.
 * - Un Mutex evita dos sincronizaciones a la vez.
 * Los equipos de cómputo y las salas los sincroniza [SincronizadorComputo] con las mismas reglas.
 */
@Singleton
class SyncManager @Inject constructor(
    private val dao: EquipoDao,
    private val api: InventarioApi,
    private val authApi: AuthApi,
    private val tx: EjecutorTransacciones,
    private val mapper: EquipoMapper,
    private val gson: Gson,
    private val computo: SincronizadorComputo,
) {
    private val mutex = Mutex()

    suspend fun sincronizar(): ResultadoSincronizacion = mutex.withLock {
        try {
            verificarServidor()
            var enviados = 0
            var conflictos = 0
            var errores = 0
            for (pendiente in dao.obtenerPendientesDeEnvio()) {
                when (enviar(pendiente)) {
                    Envio.OK -> enviados++
                    Envio.CONFLICTO -> conflictos++
                    Envio.ERROR -> errores++
                }
            }
            // Equipos de cómputo: después de los generales (un alta de cómputo puede usar un equipo recién creado).
            val enviadosComputo = computo.enviar()
            enviados += enviadosComputo.enviados
            conflictos += enviadosComputo.conflictos
            errores += enviadosComputo.errores

            val descargados = descargar()
            computo.descargar()
            ResultadoSincronizacion.Completada(enviados, conflictos, errores, descargados)
        } catch (e: ServidorNoConfiguradoException) {
            ResultadoSincronizacion.SinServidor
        } catch (e: SinSesionException) {
            ResultadoSincronizacion.SinSesion
        } catch (e: TransitorioException) {
            ResultadoSincronizacion.FalloTemporal(e.message ?: "El servidor no está disponible.")
        } catch (e: IOException) {
            ResultadoSincronizacion.FalloTemporal("Sin conexión con el servidor.")
        }
    }

    private suspend fun verificarServidor() {
        val r = authApi.yo()
        when {
            r.isSuccessful -> Unit
            r.code() == 401 -> throw SinSesionException()
            else -> throw TransitorioException("El servidor respondió con el código ${r.code()}.")
        }
    }

    private suspend fun enviar(p: EquipoEntity): Envio = try {
        when (p.estadoSync) {
            EstadoSync.PENDIENTE_CREAR -> enviarAlta(p)
            EstadoSync.PENDIENTE_ACTUALIZAR -> enviarEdicion(p)
            EstadoSync.PENDIENTE_ELIMINAR -> enviarEliminacion(p)
            else -> Envio.OK
        }
    } catch (e: IOException) {
        dao.registrarFalloTransitorio(p.inventario, "Sin conexión con el servidor.")
        throw e
    }

    // ---------- Alta ----------

    private suspend fun enviarAlta(p: EquipoEntity): Envio {
        val r = api.crear(mapper.aRequest(p))
        return when {
            r.isSuccessful -> {
                // La fecha de la respuesta no es fiable (la pone un trigger); la descarga la actualiza.
                if (dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, null) == 0) {
                    dao.marcarExisteEnServidor(p.inventario)
                }
                Envio.OK
            }
            r.code() == 409 -> resolverAltaDuplicada(p)
            else -> fallo(p, r)
        }
    }

    private suspend fun resolverAltaDuplicada(p: EquipoEntity): Envio {
        val r = api.obtener(p.inventario)
        if (!r.isSuccessful || r.body() == null) return fallo(p, r)
        val servidor = r.body()!!
        return if (NormalizadorEquipo.mismoContenido(mapper.datos(p), mapper.aDatos(servidor))) {
            if (dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, servidor.fechaActualizacion) == 0) {
                dao.marcarExisteEnServidor(p.inventario)
            }
            Envio.OK
        } else {
            dao.marcarProblema(
                p.inventario, p.fechaActualizacionLocal, EstadoSync.CONFLICTO,
                "Ya existía un equipo con este inventario en el servidor, con datos distintos.",
                mapper.aJson(servidor),
            )
            Envio.CONFLICTO
        }
    }

    // ---------- Edición ----------

    private suspend fun enviarEdicion(p: EquipoEntity): Envio {
        val actual = api.obtener(p.inventario)
        if (actual.code() == 404) {
            dao.marcarProblema(
                p.inventario, p.fechaActualizacionLocal, EstadoSync.CONFLICTO,
                "Otra persona eliminó este equipo del servidor.", null,
            )
            return Envio.CONFLICTO
        }
        if (!actual.isSuccessful || actual.body() == null) return fallo(p, actual)
        val servidor = actual.body()!!

        val base = p.fechaActualizacionServidor
        if (base != null && servidor.fechaActualizacion != base) {
            // El servidor cambió desde nuestra última sincronización.
            return if (NormalizadorEquipo.mismoContenido(mapper.datos(p), mapper.aDatos(servidor))) {
                dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, servidor.fechaActualizacion)
                Envio.OK
            } else {
                dao.marcarProblema(
                    p.inventario, p.fechaActualizacionLocal, EstadoSync.CONFLICTO,
                    "Otra persona modificó este equipo en el servidor.", mapper.aJson(servidor),
                )
                Envio.CONFLICTO
            }
        }

        val r = api.actualizar(p.inventario, mapper.aRequest(p))
        if (!r.isSuccessful) return fallo(p, r)
        dao.marcarSincronizado(p.inventario, p.fechaActualizacionLocal, null)
        return Envio.OK
    }

    // ---------- Eliminación ----------

    private suspend fun enviarEliminacion(p: EquipoEntity): Envio {
        val r = api.eliminar(p.inventario)
        return when {
            r.isSuccessful || r.code() == 404 -> {
                dao.eliminarSiNoCambio(p.inventario, p.fechaActualizacionLocal)
                Envio.OK
            }
            r.code() == 403 -> {
                dao.restaurarConError(p.inventario, p.fechaActualizacionLocal, ApiErrores.mensaje(gson, r))
                Envio.ERROR
            }
            else -> fallo(p, r)
        }
    }

    /** 401 → sesión; 5xx/429/408 → reintentar más tarde; otro 4xx → error que revisa el usuario. */
    private suspend fun fallo(p: EquipoEntity, r: Response<*>): Envio {
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

    // ---------- Descarga ----------

    /** Devuelve cuántos equipos hay en el servidor. */
    private suspend fun descargar(): Int {
        val r = api.listar()
        if (r.code() == 401) throw SinSesionException()
        if (!r.isSuccessful) throw TransitorioException("No se pudo descargar el inventario (código ${r.code()}).")
        val lista = r.body().orEmpty().filter { !it.inventario.isNullOrBlank() }
        val enServidor = lista.mapTo(HashSet()) { it.inventario!! }
        tx.ejecutar {
            for (dto in lista) {
                val local = dao.obtener(dto.inventario!!)
                // Solo se reemplazan registros sin cambios locales.
                if (local == null || local.estadoSync == EstadoSync.SINCRONIZADO) {
                    dao.upsert(mapper.entidadSincronizada(dto))
                }
            }
            // Sincronizados que ya no existen en el servidor: alguien los eliminó.
            dao.inventariosSincronizados()
                .filter { it !in enServidor }
                .forEach { dao.eliminar(it) }
        }
        return lista.size
    }
}
