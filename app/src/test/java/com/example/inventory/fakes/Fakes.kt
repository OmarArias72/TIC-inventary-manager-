package com.example.inventory.fakes

import com.example.inventory.data.local.ConteoEstado
import com.example.inventory.data.local.EjecutorTransacciones
import com.example.inventory.data.local.EquipoDao
import com.example.inventory.data.local.EquipoEntity
import com.example.inventory.data.remote.AuthApi
import com.example.inventory.data.remote.BusquedaRequestDto
import com.example.inventory.data.remote.EquipoRemotoDto
import com.example.inventory.data.remote.EquipoRequestDto
import com.example.inventory.data.remote.InventarioApi
import com.example.inventory.data.remote.LoginRequestDto
import com.example.inventory.data.remote.SesionDto
import com.example.inventory.data.remote.UsuarioDto
import com.example.inventory.domain.model.EstadoSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.IOException

/** DAO en memoria que reproduce la semántica de las consultas SQL de EquipoDao. */
class FakeEquipoDao : EquipoDao {
    val tabla = MutableStateFlow<Map<String, EquipoEntity>>(emptyMap())

    fun poner(e: EquipoEntity) {
        tabla.value = tabla.value + (e.inventario to e)
    }

    fun get(inv: String): EquipoEntity? = tabla.value[inv]

    private fun cambiar(inv: String, f: (EquipoEntity) -> EquipoEntity) {
        val e = tabla.value[inv] ?: return
        poner(f(e))
    }

    override fun observarVisibles(): Flow<List<EquipoEntity>> =
        tabla.map { m -> m.values.filter { !it.eliminado }.sortedByDescending { it.fechaRegistro } }

    override fun observarNoSincronizados(): Flow<List<EquipoEntity>> =
        tabla.map { m -> m.values.filter { it.estadoSync != EstadoSync.SINCRONIZADO } }

    override fun observarConteos(): Flow<List<ConteoEstado>> = tabla.map { m ->
        m.values.filter { it.estadoSync != EstadoSync.SINCRONIZADO }
            .groupBy { it.estadoSync }.map { (k, v) -> ConteoEstado(k, v.size) }
    }

    override suspend fun obtenerPendientesDeEnvio(): List<EquipoEntity> =
        tabla.value.values.filter { it.estadoSync.esPendienteDeEnvio }.sortedBy { it.fechaActualizacionLocal }

    override suspend fun obtener(inventario: String): EquipoEntity? = tabla.value[inventario]

    override suspend fun buscarPorCodigo(codigo: String): EquipoEntity? = tabla.value.values.firstOrNull {
        !it.eliminado && (it.inventario.equals(codigo, true) || (it.serie.isNotEmpty() && it.serie.equals(codigo, true)))
    }

    override suspend fun inventariosSincronizados(): List<String> =
        tabla.value.values.filter { it.estadoSync == EstadoSync.SINCRONIZADO }.map { it.inventario }

    override suspend fun upsert(equipo: EquipoEntity) = poner(equipo)

    override suspend fun eliminar(inventario: String) {
        tabla.value = tabla.value - inventario
    }

    override suspend fun marcarSincronizado(inventario: String, fechaLocal: Long, fechaServidor: String?): Int {
        val e = tabla.value[inventario] ?: return 0
        if (e.fechaActualizacionLocal != fechaLocal) return 0
        poner(
            e.copy(
                estadoSync = EstadoSync.SINCRONIZADO, existeEnServidor = true, intentos = 0,
                ultimoError = null, versionServidor = null, fechaActualizacionServidor = fechaServidor,
            )
        )
        return 1
    }

    override suspend fun marcarExisteEnServidor(inventario: String) = cambiar(inventario) {
        it.copy(
            existeEnServidor = true,
            estadoSync = if (it.estadoSync == EstadoSync.PENDIENTE_CREAR) EstadoSync.PENDIENTE_ACTUALIZAR else it.estadoSync,
        )
    }

    override suspend fun marcarProblema(
        inventario: String, fechaLocal: Long, estado: EstadoSync, error: String?, versionServidor: String?,
    ): Int {
        val e = tabla.value[inventario] ?: return 0
        if (e.fechaActualizacionLocal != fechaLocal) return 0
        poner(e.copy(estadoSync = estado, ultimoError = error, versionServidor = versionServidor))
        return 1
    }

    override suspend fun registrarFalloTransitorio(inventario: String, error: String?) =
        cambiar(inventario) { it.copy(intentos = it.intentos + 1, ultimoError = error) }

    override suspend fun eliminarSiNoCambio(inventario: String, fechaLocal: Long): Int {
        val e = tabla.value[inventario] ?: return 0
        if (e.fechaActualizacionLocal != fechaLocal) return 0
        tabla.value = tabla.value - inventario
        return 1
    }

    override suspend fun restaurarConError(inventario: String, fechaLocal: Long, error: String?): Int {
        val e = tabla.value[inventario] ?: return 0
        if (e.fechaActualizacionLocal != fechaLocal) return 0
        poner(e.copy(eliminado = false, estadoSync = EstadoSync.ERROR, ultimoError = error))
        return 1
    }
}

class FakeTransacciones : EjecutorTransacciones {
    override suspend fun <T> ejecutar(bloque: suspend () -> T): T = bloque()
}

fun <T> ok(cuerpo: T): Response<T> = Response.success(cuerpo)

fun <T> error(codigo: Int, mensaje: String = "error $codigo"): Response<T> =
    Response.error(codigo, """{"status":$codigo,"message":"$mensaje"}""".toResponseBody("application/json".toMediaType()))

/** Backend simulado: un mapa de equipos y contadores de llamadas. */
class FakeInventarioApi : InventarioApi {
    val servidor = mutableMapOf<String, EquipoRemotoDto>()
    var sinRed = false
    var codigoForzadoCrear: Int? = null
    var codigoForzadoEliminar: Int? = null
    var codigoForzadoActualizar: Int? = null
    var alCrear: (() -> Unit)? = null
    val llamadas = mutableListOf<String>()
    private var reloj = 0

    private fun red() {
        if (sinRed) throw IOException("sin red")
    }

    fun nuevaFecha(): String {
        reloj++
        return "2026-11-03T10:00:%02d".format(reloj)
    }

    override suspend fun listar(): Response<List<EquipoRemotoDto>> {
        red(); llamadas += "GET lista"
        return ok(servidor.values.toList())
    }

    override suspend fun obtener(inventario: String): Response<EquipoRemotoDto> {
        red(); llamadas += "GET $inventario"
        return servidor[inventario]?.let { ok(it) } ?: error(404)
    }

    override suspend fun crear(cuerpo: EquipoRequestDto): Response<EquipoRemotoDto> {
        red(); llamadas += "POST ${cuerpo.inventario}"
        alCrear?.invoke()
        codigoForzadoCrear?.let { return error(it) }
        if (servidor.containsKey(cuerpo.inventario)) return error(409, "El equipo ya existe.")
        val dto = dtoDesde(cuerpo, nuevaFecha())
        servidor[cuerpo.inventario] = dto
        return ok(dto)
    }

    override suspend fun actualizar(inventario: String, cuerpo: EquipoRequestDto): Response<EquipoRemotoDto> {
        red(); llamadas += "PUT $inventario"
        codigoForzadoActualizar?.let { return error(it, "Solo el administrador puede dar de baja un equipo o reactivarlo.") }
        if (!servidor.containsKey(inventario)) return error(404)
        val dto = dtoDesde(cuerpo, nuevaFecha())
        servidor[inventario] = dto
        return ok(dto)
    }

    override suspend fun eliminar(inventario: String): Response<Unit> {
        red(); llamadas += "DELETE $inventario"
        codigoForzadoEliminar?.let { return error(it, "Acceso denegado.") }
        return if (servidor.remove(inventario) != null) ok(Unit) else error(404)
    }

    override suspend fun buscarPorSerie(cuerpo: BusquedaRequestDto): Response<List<EquipoRemotoDto>> {
        red()
        return ok(servidor.values.filter { it.serie?.contains(cuerpo.query, true) == true })
    }

    companion object {
        fun dto(
            inventario: String, serie: String = "S1", articulo: String = "CPU", marca: String = "HP",
            modelo: String = "PRODESK", caracteristicas: String = "", estado: String = "ALTA",
            fechaActualizacion: String = "2026-11-01T09:00:00",
        ) = EquipoRemotoDto(
            inventario = inventario, serie = serie, nombreArticulo = articulo, marca = marca, modelo = modelo,
            cantidad = 1, caracteristicas = caracteristicas, fechaRegistro = "2026-11-01T09:00:00",
            fechaActualizacion = fechaActualizacion, estado = estado, registradoPor = null, actualizadoPor = null,
        )

        fun dtoDesde(c: EquipoRequestDto, fecha: String) = dto(
            inventario = c.inventario, serie = c.serie, articulo = c.nombreArticulo, marca = c.nombreMarca,
            modelo = c.nombreModelo, caracteristicas = c.caracteristicas, estado = c.estado, fechaActualizacion = fecha,
        )
    }
}

class FakeAuthApi : AuthApi {
    var codigoMe = 200
    var sinRed = false
    override suspend fun login(cuerpo: LoginRequestDto): Response<SesionDto> = ok(SesionDto(null, 900))
    override suspend fun logout(): Response<Unit> = ok(Unit)
    override suspend fun miPerfil(): Response<UsuarioDto> = error(404)
    override suspend fun yo(): Response<SesionDto> {
        if (sinRed) throw IOException("sin red")
        return if (codigoMe == 200) ok(SesionDto(null, 900)) else error(codigoMe)
    }
}
