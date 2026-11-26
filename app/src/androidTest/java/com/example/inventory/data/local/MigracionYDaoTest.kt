package com.example.inventory.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.inventory.domain.model.EstadoSync
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas en dispositivo/emulador (Room necesita SQLite de Android).
 * Ejecutar: botón ▶ junto a la clase, o ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class MigracionYDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nombre = "migracion-prueba.db"
    private var db: AppDatabase? = null

    @After
    fun limpiar() {
        db?.close()
        context.deleteDatabase(nombre)
    }

    /** Crea una BD con el esquema de la versión 6 (el que tenía la app antes del refactor). */
    private fun crearBaseVersion6() {
        context.deleteDatabase(nombre)
        val v6 = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(nombre), null)
        v6.execSQL("CREATE TABLE articulos (id_articulo INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nombre_articulo TEXT NOT NULL, fecha_actualizacion INTEGER NOT NULL)")
        v6.execSQL("CREATE TABLE marcas (id_marca INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, nombre_marca TEXT NOT NULL, fecha_actualizacion INTEGER NOT NULL)")
        v6.execSQL("CREATE TABLE modelos (id_modelo INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, id_marca INTEGER NOT NULL, nombre_modelo TEXT NOT NULL, fecha_actualizacion INTEGER NOT NULL, FOREIGN KEY(id_marca) REFERENCES marcas(id_marca) ON DELETE CASCADE)")
        v6.execSQL("CREATE TABLE equipos (id_posicion INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, inventario TEXT NOT NULL, id_articulo INTEGER NOT NULL, id_modelo INTEGER NOT NULL, serie TEXT, ubicacion TEXT, caracteristicas TEXT, estado TEXT NOT NULL, fecha_registro INTEGER NOT NULL, fecha_actualizacion INTEGER NOT NULL, sync_state TEXT NOT NULL)")
        v6.execSQL("CREATE TABLE equipos_computo (id_equipo_computo INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, inventario TEXT NOT NULL, especificaciones_hardware TEXT, sistema_operativo TEXT, software_instalado TEXT, estado_teclado TEXT, estado_mouse TEXT, estado_monitor TEXT, fecha_actualizacion INTEGER NOT NULL, sync_state TEXT NOT NULL)")
        v6.execSQL("INSERT INTO articulos VALUES (1, 'SIN ARTICULO', 0)")
        v6.execSQL("INSERT INTO marcas VALUES (1, 'HP', 0)")
        v6.execSQL("INSERT INTO modelos VALUES (1, 1, 'PRODESK', 0)")
        v6.execSQL("INSERT INTO equipos VALUES (1, 'INV-001', 1, 1, 'MXL123', 'Sala 3', 'Sin teclado', 'MANTENIMIENTO', 1000, 2000, 'CREADO_OFFLINE')")
        v6.execSQL("INSERT INTO equipos VALUES (2, 'INV-002', 1, 1, NULL, '', NULL, 'ALTA', 1100, 2100, 'MODIFICADO_OFFLINE')")
        v6.version = 6
        v6.close()
    }

    @Test
    fun migracion6a7_conservaLosEquiposYLosDejaPendientes() = runTest {
        crearBaseVersion6()
        db = Room.databaseBuilder(context, AppDatabase::class.java, nombre)
            .addMigrations(Migraciones.MIGRACION_6_7, Migraciones.MIGRACION_7_8)
            .build()
        val dao = db!!.equipoDao()
        // 7 → 8 crea las tablas nuevas vacías
        assertEquals(0, db!!.equipoComputoDao().inventariosSincronizados().size)

        val uno = dao.obtener("INV-001")
        assertNotNull(uno)
        uno!!
        assertEquals("ARTÍCULO GENERAL", uno.articulo)
        assertEquals("HP", uno.marca)
        assertEquals("MXL123", uno.serie)
        assertEquals("Sin teclado\nUbicación: Sala 3", uno.caracteristicas)
        assertEquals("MANTENIMIENTO", uno.estado)
        assertEquals(EstadoSync.PENDIENTE_CREAR, uno.estadoSync)
        assertFalse(uno.existeEnServidor)

        val dos = dao.obtener("INV-002")!!
        assertEquals("", dos.serie)
        assertEquals("", dos.caracteristicas)
        assertEquals(2, dao.obtenerPendientesDeEnvio().size)
    }

    @Test
    fun marcarSincronizado_soloSiNoSeEditoMientrasTanto() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db!!.equipoDao()
        val base = EquipoEntity(
            inventario = "A", serie = "", articulo = "CPU", marca = "HP", modelo = "X", caracteristicas = "",
            estado = "ALTA", fechaRegistro = 1, fechaActualizacionLocal = 10, fechaActualizacionServidor = null,
            existeEnServidor = false, eliminado = false, estadoSync = EstadoSync.PENDIENTE_CREAR,
            intentos = 0, ultimoError = null, versionServidor = null,
        )
        dao.upsert(base)

        assertEquals(0, dao.marcarSincronizado("A", fechaLocal = 99, fechaServidor = null))
        assertEquals(EstadoSync.PENDIENTE_CREAR, dao.obtener("A")!!.estadoSync)

        assertEquals(1, dao.marcarSincronizado("A", fechaLocal = 10, fechaServidor = "2026-11-03T10:00:00"))
        val e = dao.obtener("A")!!
        assertEquals(EstadoSync.SINCRONIZADO, e.estadoSync)
        assertTrue(e.existeEnServidor)
    }

    @Test
    fun eliminadoLogico_noApareceEnLaLista() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dao = db!!.equipoDao()
        dao.upsert(
            EquipoEntity(
                inventario = "B", serie = "", articulo = "CPU", marca = "HP", modelo = "X", caracteristicas = "",
                estado = "ALTA", fechaRegistro = 1, fechaActualizacionLocal = 10, fechaActualizacionServidor = null,
                existeEnServidor = true, eliminado = true, estadoSync = EstadoSync.PENDIENTE_ELIMINAR,
                intentos = 0, ultimoError = null, versionServidor = null,
            )
        )
        assertNull(dao.buscarPorCodigo("B"))
        assertEquals(1, dao.obtenerPendientesDeEnvio().size)
    }
}
