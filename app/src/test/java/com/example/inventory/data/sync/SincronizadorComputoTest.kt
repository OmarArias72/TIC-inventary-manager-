package com.example.inventory.data.sync

import com.example.inventory.data.local.ConteoEstado
import com.example.inventory.data.local.EquipoComputoDao
import com.example.inventory.data.local.EquipoComputoEntity
import com.example.inventory.data.local.SalaDao
import com.example.inventory.data.local.SalaEntity
import com.example.inventory.data.mapper.ComputoMapper
import com.example.inventory.data.remote.ComputoApi
import com.example.inventory.data.remote.ContrasenasDto
import com.example.inventory.data.remote.EquipoComputoRemotoDto
import com.example.inventory.data.remote.EquipoComputoRequestDto
import com.example.inventory.data.remote.SalaRemotaDto
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.fakes.FakeTransacciones
import com.example.inventory.fakes.error
import com.example.inventory.fakes.ok
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SincronizadorComputoTest {

    private lateinit var dao: FakeComputoDao
    private lateinit var salas: FakeSalaDao
    private lateinit var api: FakeComputoApi
    private lateinit var sync: SincronizadorComputoImpl
    private val mapper = ComputoMapper(Gson())

    @Before
    fun preparar() {
        dao = FakeComputoDao()
        salas = FakeSalaDao()
        api = FakeComputoApi()
        sync = SincronizadorComputoImpl(dao, salas, api, FakeTransacciones(), mapper, Gson())
    }

    private fun local(
        inventario: String,
        estado: EstadoSync,
        contrasenaPendiente: String? = null,
        ram: String = "8 GB",
        idServidor: Int? = null,
        fechaServidor: String? = null,
    ) = EquipoComputoEntity(
        inventario = inventario, idServidor = idServidor, serie = "S1", articulo = "CPU", marca = "HP", modelo = "PRODESK",
        caracteristicas = "", estado = "ALTA", idSala = 3, procesador = "i5", ram = ram, almacenamiento = "",
        especificacionesHardware = "", sistemaOperativo = "Windows 10", softwareInstalado = "", estadoComputadora = "Funcionando",
        estadoCpu = "", estadoMonitor = "", estadoTeclado = "", estadoMouse = "", estadoCableEthernet = "", observaciones = "",
        tieneContrasena = false, tieneUltimaContrasena = false, contrasenaPendiente = contrasenaPendiente,
        borrarContrasena = false, motivoCambio = null, actualizadoPor = null, fechaRegistro = 1, fechaActualizacionLocal = 10,
        fechaActualizacionServidor = fechaServidor, existeEnServidor = estado != EstadoSync.PENDIENTE_CREAR,
        estadoSync = estado, intentos = 0, ultimoError = null, versionServidor = null,
    )

    @Test
    fun alta_guardaIdDelServidorYBorraLaContrasenaPendiente() = runTest {
        dao.poner(local("PC-01", EstadoSync.PENDIENTE_CREAR, contrasenaPendiente = "secreta"))

        val conteo = sync.enviar()

        assertEquals(1, conteo.enviados)
        assertEquals("secreta", api.ultimoCuerpo?.contrasena)
        val e = dao.get("PC-01")!!
        assertEquals(EstadoSync.SINCRONIZADO, e.estadoSync)
        assertEquals(100, e.idServidor)
        assertNull(e.contrasenaPendiente)
        assertTrue(e.tieneContrasena)
    }

    @Test
    fun altaDuplicadaIgual_esIdempotente() = runTest {
        api.servidor["PC-01"] = api.dto(mapper.aRequest(local("PC-01", EstadoSync.PENDIENTE_CREAR), 7), 7)
        dao.poner(local("PC-01", EstadoSync.PENDIENTE_CREAR))

        sync.enviar()

        assertEquals(EstadoSync.SINCRONIZADO, dao.get("PC-01")!!.estadoSync)
        assertEquals(7, dao.get("PC-01")!!.idServidor)
    }

    @Test
    fun edicionConCambiosDeOtraPersona_esConflicto() = runTest {
        val enServidor = api.dto(mapper.aRequest(local("PC-01", EstadoSync.SINCRONIZADO, ram = "16 GB"), 7), 7)
            .copy(fechaActualizacion = "2026-11-03T12:00:00")
        api.servidor["PC-01"] = enServidor
        dao.poner(local("PC-01", EstadoSync.PENDIENTE_ACTUALIZAR, idServidor = 7, fechaServidor = "2026-11-01T09:00:00"))

        val conteo = sync.enviar()

        assertEquals(1, conteo.conflictos)
        assertFalse(api.llamadas.any { it.startsWith("PUT") })
        assertEquals(EstadoSync.CONFLICTO, dao.get("PC-01")!!.estadoSync)
    }

    @Test
    fun edicionSinCambiosEnServidor_seEnviaCompleta() = runTest {
        api.servidor["PC-01"] = api.dto(mapper.aRequest(local("PC-01", EstadoSync.SINCRONIZADO), 7), 7)
            .copy(fechaActualizacion = "2026-11-01T09:00:00")
        dao.poner(local("PC-01", EstadoSync.PENDIENTE_ACTUALIZAR, ram = "32 GB", idServidor = 7, fechaServidor = "2026-11-01T09:00:00"))

        sync.enviar()

        assertTrue("PUT 7" in api.llamadas)
        assertEquals("32 GB", api.servidor["PC-01"]!!.ram)
        assertEquals(EstadoSync.SINCRONIZADO, dao.get("PC-01")!!.estadoSync)
    }

    @Test
    fun salaLocal_seEmparejaPorCodigoAntesDeEnviar() = runTest {
        salas.tabla.value = listOf(SalaEntity(-1, "LAB-1", "Lab 1 (local)", ""))
        api.salasServidor = listOf(SalaRemotaDto(3, "lab-1", "Laboratorio 1", "Edificio A"))
        dao.poner(local("PC-01", EstadoSync.PENDIENTE_CREAR).copy(idSala = -1))

        val conteo = sync.enviar()

        assertEquals(1, conteo.enviados)
        assertEquals(3, api.ultimoCuerpo?.idSala)            // se envió con la sala del servidor
        assertEquals(3, dao.get("PC-01")!!.idSala)
        assertTrue(salas.tabla.value.none { it.idSala < 0 }) // la sala local se reemplazó
    }

    @Test
    fun salaLocalQueNoExisteEnServidor_quedaPendienteSinEnviarse() = runTest {
        salas.tabla.value = listOf(SalaEntity(-1, "LAB-9", "", ""))
        api.salasServidor = listOf(SalaRemotaDto(3, "LAB-1", "Laboratorio 1", ""))
        dao.poner(local("PC-01", EstadoSync.PENDIENTE_CREAR).copy(idSala = -1))

        sync.enviar()

        assertTrue(api.llamadas.none { it.startsWith("POST") })
        val e = dao.get("PC-01")!!
        assertEquals(EstadoSync.PENDIENTE_CREAR, e.estadoSync)
        assertTrue(e.ultimoError!!.contains("LAB-9"))
    }

    @Test
    fun descarga_conservaSalasLocalesSinPareja() = runTest {
        salas.tabla.value = listOf(SalaEntity(-1, "LAB-9", "", ""))
        api.salasServidor = listOf(SalaRemotaDto(3, "LAB-1", "Laboratorio 1", ""))

        sync.descargar()

        assertEquals(setOf(-1, 3), salas.tabla.value.map { it.idSala }.toSet())
    }

    @Test
    fun descarga_guardaSalasYEquipos_yConservaPendientes() = runTest {
        api.salasServidor = listOf(SalaRemotaDto(3, "LAB-1", "Laboratorio 1", "Edificio A"))
        api.servidor["PC-02"] = api.dto(mapper.aRequest(local("PC-02", EstadoSync.SINCRONIZADO), 8), 8)
        api.servidor["PC-01"] = api.dto(mapper.aRequest(local("PC-01", EstadoSync.SINCRONIZADO, ram = "4 GB"), 7), 7)
        dao.poner(local("PC-01", EstadoSync.PENDIENTE_ACTUALIZAR, ram = "32 GB", idServidor = 7))

        sync.descargar()

        assertEquals(listOf(3), salas.tabla.value.map { it.idSala })
        assertEquals(EstadoSync.SINCRONIZADO, dao.get("PC-02")!!.estadoSync)
        assertEquals("32 GB", dao.get("PC-01")!!.ram)
    }
}

// ---------------- Dobles de prueba ----------------

private class FakeSalaDao : SalaDao {
    val tabla = MutableStateFlow<List<SalaEntity>>(emptyList())
    override fun observarTodas(): Flow<List<SalaEntity>> = tabla
    override suspend fun insertar(salas: List<SalaEntity>) {
        val ids = salas.map { it.idSala }.toSet()
        tabla.value = tabla.value.filter { it.idSala !in ids } + salas
    }
    override suspend fun borrarDelServidor() { tabla.value = tabla.value.filter { it.idSala < 0 } }
    override suspend fun obtenerLocales() = tabla.value.filter { it.idSala < 0 }
    override suspend fun buscarPorCodigo(codigo: String) = tabla.value.firstOrNull { it.codigo.trim().equals(codigo.trim(), true) }
    override suspend fun idMinimo(): Int? = tabla.value.minOfOrNull { it.idSala }
    override suspend fun eliminar(idSala: Int) { tabla.value = tabla.value.filter { it.idSala != idSala } }
}

private class FakeComputoDao : EquipoComputoDao {
    val tabla = MutableStateFlow<Map<String, EquipoComputoEntity>>(emptyMap())
    fun poner(e: EquipoComputoEntity) { tabla.value = tabla.value + (e.inventario to e) }
    fun get(inv: String) = tabla.value[inv]

    override fun observarPorSala(idSala: Int) = tabla.map { m -> m.values.filter { it.idSala == idSala } }
    override fun observarSinSala() = tabla.map { m -> m.values.filter { it.idSala == null } }
    override fun observarNoSincronizados() = tabla.map { m -> m.values.filter { it.estadoSync != EstadoSync.SINCRONIZADO } }
    override fun observarConteos(): Flow<List<ConteoEstado>> = tabla.map { emptyList() }
    override suspend fun obtenerPendientesDeEnvio() = tabla.value.values.filter {
        it.estadoSync == EstadoSync.PENDIENTE_CREAR || it.estadoSync == EstadoSync.PENDIENTE_ACTUALIZAR
    }
    override suspend fun obtener(inventario: String) = tabla.value[inventario]
    override suspend fun inventariosSincronizados() = tabla.value.values.filter { it.estadoSync == EstadoSync.SINCRONIZADO }.map { it.inventario }
    override suspend fun upsert(equipo: EquipoComputoEntity) = poner(equipo)
    override suspend fun eliminar(inventario: String) { tabla.value = tabla.value - inventario }

    override suspend fun marcarSincronizado(inventario: String, fechaLocal: Long, fechaServidor: String?, idServidor: Int?): Int {
        val e = tabla.value[inventario] ?: return 0
        if (e.fechaActualizacionLocal != fechaLocal) return 0
        val cambio = e.contrasenaPendiente != null || e.borrarContrasena
        poner(
            e.copy(
                estadoSync = EstadoSync.SINCRONIZADO, existeEnServidor = true, idServidor = idServidor ?: e.idServidor,
                intentos = 0, ultimoError = null, versionServidor = null, fechaActualizacionServidor = fechaServidor,
                motivoCambio = null,
                tieneUltimaContrasena = if (cambio && e.tieneContrasena) true else e.tieneUltimaContrasena,
                tieneContrasena = when {
                    e.contrasenaPendiente != null -> true
                    e.borrarContrasena -> false
                    else -> e.tieneContrasena
                },
                contrasenaPendiente = null, borrarContrasena = false,
            )
        )
        return 1
    }

    override suspend fun marcarExisteEnServidor(inventario: String, idServidor: Int?, fechaServidor: String?) {
        val e = tabla.value[inventario] ?: return
        poner(
            e.copy(
                existeEnServidor = true, idServidor = idServidor,
                fechaActualizacionServidor = fechaServidor ?: e.fechaActualizacionServidor,
                estadoSync = if (e.estadoSync == EstadoSync.PENDIENTE_CREAR) EstadoSync.PENDIENTE_ACTUALIZAR else e.estadoSync,
            )
        )
    }

    override suspend fun marcarProblema(inventario: String, fechaLocal: Long, estado: EstadoSync, error: String?, versionServidor: String?): Int {
        val e = tabla.value[inventario] ?: return 0
        if (e.fechaActualizacionLocal != fechaLocal) return 0
        poner(e.copy(estadoSync = estado, ultimoError = error, versionServidor = versionServidor))
        return 1
    }

    override suspend fun registrarFalloTransitorio(inventario: String, error: String?) {
        val e = tabla.value[inventario] ?: return
        poner(e.copy(intentos = e.intentos + 1, ultimoError = error))
    }

    override suspend fun reasignarSala(anterior: Int, nueva: Int) {
        tabla.value = tabla.value.mapValues { (_, e) -> if (e.idSala == anterior) e.copy(idSala = nueva) else e }
    }
}

private class FakeComputoApi : ComputoApi {
    val servidor = mutableMapOf<String, EquipoComputoRemotoDto>()
    var salasServidor: List<SalaRemotaDto> = emptyList()
    val llamadas = mutableListOf<String>()
    var ultimoCuerpo: EquipoComputoRequestDto? = null
    private var siguienteId = 100

    fun dto(c: EquipoComputoRequestDto, id: Int) = EquipoComputoRemotoDto(
        idEquipoComputo = id, inventario = c.inventario, serie = c.serie, cantidad = 1, caracteristicas = c.caracteristicas,
        estado = c.estado, nombreArticulo = c.nombreArticulo, nombreMarca = c.nombreMarca, nombreModelo = c.nombreModelo,
        idSala = c.idSala, codigoSala = null, nombreSala = null, especificacionesHardware = c.especificacionesHardware,
        sistemaOperativo = c.sistemaOperativo, softwareInstalado = c.softwareInstalado, estadoTeclado = c.estadoTeclado,
        estadoMouse = c.estadoMouse, estadoCableEthernet = c.estadoCableEthernet, estadoMonitor = c.estadoMonitor,
        procesador = c.procesador, ram = c.ram, almacenamiento = c.almacenamiento, tieneContrasena = c.contrasena != null,
        tieneUltimaContrasena = false, fechaCambioContrasena = null, estadoCpu = c.estadoCpu,
        estadoComputadora = c.estadoComputadora, observaciones = c.observaciones, ultimoUsuarioActualizacionNombre = null,
        registradoPor = null, fechaRegistro = "2026-11-01T09:00:00", fechaActualizacion = "2026-11-01T09:00:00",
    )

    override suspend fun salas(): Response<List<SalaRemotaDto>> = ok(salasServidor)
    override suspend fun listar(): Response<List<EquipoComputoRemotoDto>> = ok(servidor.values.toList())
    override suspend fun obtener(id: Int): Response<EquipoComputoRemotoDto> {
        llamadas += "GET $id"
        return servidor.values.firstOrNull { it.idEquipoComputo == id }?.let { ok(it) } ?: error(404)
    }
    override suspend fun obtenerPorInventario(inventario: String): Response<EquipoComputoRemotoDto> =
        servidor[inventario]?.let { ok(it) } ?: error(404)

    override suspend fun crear(cuerpo: EquipoComputoRequestDto): Response<EquipoComputoRemotoDto> {
        llamadas += "POST ${cuerpo.inventario}"
        ultimoCuerpo = cuerpo
        if (servidor.containsKey(cuerpo.inventario)) return error(409)
        val nuevo = dto(cuerpo, siguienteId++)
        servidor[cuerpo.inventario] = nuevo
        return ok(nuevo)
    }

    override suspend fun actualizar(id: Int, cuerpo: EquipoComputoRequestDto): Response<EquipoComputoRemotoDto> {
        llamadas += "PUT $id"
        ultimoCuerpo = cuerpo
        val nuevo = dto(cuerpo, id).copy(fechaActualizacion = "2026-11-03T18:00:00")
        servidor[cuerpo.inventario] = nuevo
        return ok(nuevo)
    }

    override suspend fun contrasenas(id: Int): Response<ContrasenasDto> = error(404)
}
