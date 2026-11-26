package com.example.inventory.data.sync

import com.example.inventory.data.local.EquipoEntity
import com.example.inventory.data.mapper.EquipoMapper
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ResultadoSincronizacion
import com.example.inventory.fakes.FakeAuthApi
import com.example.inventory.fakes.FakeEquipoDao
import com.example.inventory.fakes.FakeInventarioApi
import com.example.inventory.fakes.FakeTransacciones
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Para estas pruebas no hay equipos de cómputo. */
private class SincronizadorComputoVacio : SincronizadorComputo {
    override suspend fun enviar() = ConteoEnvio()
    override suspend fun descargar() = Unit
}

class SyncManagerTest {

    private lateinit var dao: FakeEquipoDao
    private lateinit var api: FakeInventarioApi
    private lateinit var authApi: FakeAuthApi
    private lateinit var sync: SyncManager

    @Before
    fun preparar() {
        dao = FakeEquipoDao()
        api = FakeInventarioApi()
        authApi = FakeAuthApi()
        val gson = Gson()
        sync = SyncManager(dao, api, authApi, FakeTransacciones(), EquipoMapper(gson), gson, SincronizadorComputoVacio())
    }

    private fun local(
        inventario: String,
        estado: EstadoSync,
        existe: Boolean = estado != EstadoSync.PENDIENTE_CREAR,
        serie: String = "S1",
        caracteristicas: String = "",
        eliminado: Boolean = false,
        fechaServidor: String? = "2026-11-01T09:00:00",
        fechaLocal: Long = 1000L,
    ) = EquipoEntity(
        inventario = inventario, serie = serie, articulo = "CPU", marca = "HP", modelo = "PRODESK",
        caracteristicas = caracteristicas, estado = "ALTA", fechaRegistro = 1000L, fechaActualizacionLocal = fechaLocal,
        fechaActualizacionServidor = if (existe) fechaServidor else null, existeEnServidor = existe,
        eliminado = eliminado, estadoSync = estado, intentos = 0, ultimoError = null, versionServidor = null,
    )

    @Test
    fun altaExitosa_quedaSincronizada() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR))

        val r = sync.sincronizar()

        assertTrue(r is ResultadoSincronizacion.Completada)
        assertEquals(EstadoSync.SINCRONIZADO, dao.get("INV-1")!!.estadoSync)
        assertTrue(dao.get("INV-1")!!.existeEnServidor)
        assertTrue(api.servidor.containsKey("INV-1"))
    }

    @Test
    fun altaDuplicadaConMismoContenido_esIdempotente() = runTest {
        api.servidor["INV-1"] = FakeInventarioApi.dto("INV-1")
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR))

        sync.sincronizar()

        assertEquals(EstadoSync.SINCRONIZADO, dao.get("INV-1")!!.estadoSync)
    }

    @Test
    fun altaDuplicadaConContenidoDistinto_esConflicto() = runTest {
        api.servidor["INV-1"] = FakeInventarioApi.dto("INV-1", serie = "OTRA")
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR))

        val r = sync.sincronizar() as ResultadoSincronizacion.Completada

        assertEquals(1, r.conflictos)
        assertEquals(EstadoSync.CONFLICTO, dao.get("INV-1")!!.estadoSync)
        assertNotNull(dao.get("INV-1")!!.versionServidor)
        assertEquals("OTRA", api.servidor["INV-1"]!!.serie) // el servidor no se tocó
    }

    @Test
    fun sinRed_noSePierdeElCambioYSeReintenta() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR))
        authApi.sinRed = true

        val r = sync.sincronizar()

        assertTrue(r is ResultadoSincronizacion.FalloTemporal)
        assertEquals(EstadoSync.PENDIENTE_CREAR, dao.get("INV-1")!!.estadoSync)
    }

    @Test
    fun caidaDuranteElEnvio_registraIntentoYConservaPendiente() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR))
        api.sinRed = true

        val r = sync.sincronizar()

        assertTrue(r is ResultadoSincronizacion.FalloTemporal)
        assertEquals(EstadoSync.PENDIENTE_CREAR, dao.get("INV-1")!!.estadoSync)
        assertEquals(1, dao.get("INV-1")!!.intentos)
    }

    @Test
    fun errorDelServidor500_esTransitorio() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR))
        api.codigoForzadoCrear = 503

        val r = sync.sincronizar()

        assertTrue(r is ResultadoSincronizacion.FalloTemporal)
        assertEquals(EstadoSync.PENDIENTE_CREAR, dao.get("INV-1")!!.estadoSync)
    }

    @Test
    fun edicionSinCambiosEnServidor_seEnvia() = runTest {
        api.servidor["INV-1"] = FakeInventarioApi.dto("INV-1")
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_ACTUALIZAR, caracteristicas = "Pantalla rota"))

        sync.sincronizar()

        assertTrue("PUT INV-1" in api.llamadas)
        assertEquals("Pantalla rota", api.servidor["INV-1"]!!.caracteristicas)
        assertEquals(EstadoSync.SINCRONIZADO, dao.get("INV-1")!!.estadoSync)
        // La descarga posterior deja como base la fecha real del servidor
        assertEquals(api.servidor["INV-1"]!!.fechaActualizacion, dao.get("INV-1")!!.fechaActualizacionServidor)
    }

    @Test
    fun edicionCuandoOtroModificoElServidor_esConflictoYNoSobrescribe() = runTest {
        api.servidor["INV-1"] = FakeInventarioApi.dto("INV-1", caracteristicas = "Cambio de otra persona",
            fechaActualizacion = "2026-11-02T12:00:00")
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_ACTUALIZAR, caracteristicas = "Mi cambio"))

        sync.sincronizar()

        assertFalse("PUT INV-1" in api.llamadas)
        assertEquals(EstadoSync.CONFLICTO, dao.get("INV-1")!!.estadoSync)
        assertEquals("Cambio de otra persona", api.servidor["INV-1"]!!.caracteristicas)
    }

    @Test
    fun edicionDeEquipoEliminadoEnServidor_esConflicto() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_ACTUALIZAR))

        sync.sincronizar()

        val e = dao.get("INV-1")!!
        assertEquals(EstadoSync.CONFLICTO, e.estadoSync)
        assertNull(e.versionServidor)
    }

    @Test
    fun operadorDaDeBaja_403_quedaEnError() = runTest {
        api.servidor["INV-1"] = FakeInventarioApi.dto("INV-1")
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_ACTUALIZAR))
        api.codigoForzadoActualizar = 403

        val r = sync.sincronizar() as ResultadoSincronizacion.Completada

        assertEquals(1, r.errores)
        assertEquals(EstadoSync.ERROR, dao.get("INV-1")!!.estadoSync)
        assertTrue(dao.get("INV-1")!!.ultimoError!!.contains("administrador"))
    }

    @Test
    fun eliminacionConfirmada_404TambienCuentaComoExito() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_ELIMINAR, eliminado = true))

        sync.sincronizar()

        assertNull(dao.get("INV-1"))
    }

    @Test
    fun eliminacionSinPermiso_restauraElEquipoConError() = runTest {
        api.servidor["INV-1"] = FakeInventarioApi.dto("INV-1")
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_ELIMINAR, eliminado = true))
        api.codigoForzadoEliminar = 403

        sync.sincronizar()

        val e = dao.get("INV-1")!!
        assertFalse(e.eliminado)
        assertEquals(EstadoSync.ERROR, e.estadoSync)
    }

    @Test
    fun descarga_agregaNuevos_borraEliminadosYConservaPendientes() = runTest {
        api.servidor["NUEVO"] = FakeInventarioApi.dto("NUEVO")
        api.servidor["PEND"] = FakeInventarioApi.dto("PEND", caracteristicas = "servidor")
        dao.poner(local("BORRADO-EN-SERVIDOR", EstadoSync.SINCRONIZADO))
        dao.poner(local("PEND", EstadoSync.ERROR, caracteristicas = "local"))

        sync.sincronizar()

        assertNotNull(dao.get("NUEVO"))
        assertNull(dao.get("BORRADO-EN-SERVIDOR"))
        assertEquals("local", dao.get("PEND")!!.caracteristicas)
    }

    @Test
    fun edicionDuranteElEnvio_noSeMarcaComoSincronizada() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR, fechaLocal = 1000L))
        // El usuario edita el registro mientras se está enviando.
        api.alCrear = { dao.poner(dao.get("INV-1")!!.copy(caracteristicas = "editado", fechaActualizacionLocal = 2000L)) }

        sync.sincronizar()

        val e = dao.get("INV-1")!!
        assertEquals(EstadoSync.PENDIENTE_ACTUALIZAR, e.estadoSync)
        assertTrue(e.existeEnServidor)
        assertEquals("editado", e.caracteristicas)
    }

    @Test
    fun sesionExpirada_noTocaNada() = runTest {
        dao.poner(local("INV-1", EstadoSync.PENDIENTE_CREAR))
        authApi.codigoMe = 401

        val r = sync.sincronizar()

        assertEquals(ResultadoSincronizacion.SinSesion, r)
        assertTrue(api.llamadas.isEmpty())
        assertEquals(EstadoSync.PENDIENTE_CREAR, dao.get("INV-1")!!.estadoSync)
    }
}
