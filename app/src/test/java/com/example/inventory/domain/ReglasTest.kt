package com.example.inventory.domain

import com.example.inventory.data.mapper.FechasServidor
import com.example.inventory.data.remote.NormalizadorUrl
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.EstadoSincronizacion
import com.example.inventory.domain.model.PerfilUsuario
import com.example.inventory.domain.model.ResultadoBusqueda
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.model.Usuario
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.EquipoRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import com.example.inventory.domain.usecase.GuardarEquipoUseCase
import com.example.inventory.domain.usecase.NormalizadorEquipo
import com.example.inventory.domain.usecase.PermisosInventario
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasTest {

    // ---------- Normalización (igual que el backend) ----------

    @Test
    fun normaliza_mayusculasYValoresPorDefecto() {
        val d = NormalizadorEquipo.normalizar(DatosEquipo(inventario = " 123 ", marca = "hp", modelo = ""))
        assertEquals("123", d.inventario)
        assertEquals("HP", d.marca)
        assertEquals(NormalizadorEquipo.MODELO_POR_DEFECTO, d.modelo)
        assertEquals(NormalizadorEquipo.ARTICULO_POR_DEFECTO, d.articulo)
    }

    @Test
    fun mismoContenido_ignoraMayusculasYEspacios() {
        assertTrue(
            NormalizadorEquipo.mismoContenido(
                DatosEquipo("1", "S", "cpu", "hp", "x", "nota "),
                DatosEquipo("1", "S", "CPU", "HP", "X", "nota"),
            )
        )
    }

    // ---------- Permisos ----------

    @Test
    fun operador_puedeDarDeBajaPeroNoReactivar() {
        assertEquals(EstadoEquipo.entries, PermisosInventario.estadosPermitidos(Rol.OPERADOR, EstadoEquipo.ALTA))
        assertEquals(EstadoEquipo.entries, PermisosInventario.estadosPermitidos(Rol.OPERADOR, null))
        assertEquals(listOf(EstadoEquipo.BAJA), PermisosInventario.estadosPermitidos(Rol.OPERADOR, EstadoEquipo.BAJA))
        assertEquals(EstadoEquipo.entries, PermisosInventario.estadosPermitidos(Rol.ADMIN, EstadoEquipo.BAJA))
    }

    @Test
    fun rol_seConvierteIgualQueEnElBackend() {
        assertEquals(Rol.OPERADOR, Rol.desdeNombre("Operador-tics"))
        assertEquals(Rol.ADMIN, Rol.desdeNombre("Admin"))
        assertEquals(Rol.OTRO, Rol.desdeNombre("Profesor"))
    }

    // ---------- URL y fechas ----------

    @Test
    fun url_seNormaliza() {
        assertEquals("https://abc.trycloudflare.com/", NormalizadorUrl.normalizar("abc.trycloudflare.com").getOrThrow())
        assertEquals("https://abc.trycloudflare.com/", NormalizadorUrl.normalizar(" https://abc.trycloudflare.com/api/ ").getOrThrow())
        assertEquals("http://192.168.1.5:3000/", NormalizadorUrl.normalizar("http://192.168.1.5:3000").getOrThrow())
        assertTrue(NormalizadorUrl.normalizar("").isFailure)
    }

    @Test
    fun fechaDelServidor_seInterpretaEnHoraDelCampus() {
        // 2026-11-03 10:00 en Ciudad de México = 16:00 UTC
        assertEquals(1793721600000L, FechasServidor.aMilis("2026-11-03T10:00:00"))
        assertNotNull(FechasServidor.aMilis("2026-11-03T10:00:00.123456"))
        assertNull(FechasServidor.aMilis("no es fecha"))
    }

    // ---------- Caso de uso ----------

    @Test
    fun guardar_operadorPuedeRegistrarEnBaja() = runTest {
        val repo = RepoEspia()
        val caso = GuardarEquipoUseCase(repo, AuthFalso(Rol.OPERADOR), SyncFalso())

        val r = caso(null, DatosEquipo(inventario = "1", estado = EstadoEquipo.BAJA))

        assertTrue(r.isSuccess)
        assertTrue(repo.creado)
    }

    @Test
    fun guardar_operadorNoPuedeReactivar_sinTocarElRepositorio() = runTest {
        val repo = RepoEspia(existente = EstadoEquipo.BAJA)
        val caso = GuardarEquipoUseCase(repo, AuthFalso(Rol.OPERADOR), SyncFalso())

        val r = caso("1", DatosEquipo(inventario = "1", estado = EstadoEquipo.ALTA))

        assertTrue(r.isFailure)
        assertNull(repo.ultimo)
    }

    @Test
    fun guardar_inventarioVacio_esRechazado() = runTest {
        val caso = GuardarEquipoUseCase(RepoEspia(), AuthFalso(Rol.ADMIN), SyncFalso())
        assertTrue(caso(null, DatosEquipo(inventario = "  ")).isFailure)
    }

    @Test
    fun guardar_valido_normalizaYPideSincronizar() = runTest {
        val repo = RepoEspia()
        val sync = SyncFalso()
        val caso = GuardarEquipoUseCase(repo, AuthFalso(Rol.OPERADOR), sync)

        assertTrue(caso(null, DatosEquipo(inventario = "1", marca = "dell")).isSuccess)
        assertEquals("DELL", repo.ultimo?.marca)
        assertTrue(sync.pedido)
    }

    // ---------- Dobles de prueba ----------

    private class RepoEspia(private val existente: EstadoEquipo? = null) : EquipoRepository {
        var creado = false
        var ultimo: DatosEquipo? = null
        override fun observarEquipos(): Flow<List<Equipo>> = emptyFlow()
        override fun observarNoSincronizados(): Flow<List<Equipo>> = emptyFlow()
        override suspend fun obtener(inventario: String): Equipo? = existente?.let {
            Equipo(inventario, "", "CPU", "HP", "X", "", it, 0L, 0L, true, com.example.inventory.domain.model.EstadoSync.SINCRONIZADO, null, null)
        }
        override suspend fun crear(datos: DatosEquipo): Result<Unit> { creado = true; ultimo = datos; return Result.success(Unit) }
        override suspend fun actualizar(inventarioOriginal: String, datos: DatosEquipo): Result<Unit> { ultimo = datos; return Result.success(Unit) }
        override suspend fun eliminar(inventario: String): Result<Unit> = Result.success(Unit)
        override suspend fun buscarPorCodigo(codigo: String): ResultadoBusqueda = ResultadoBusqueda.NoEncontrado
        override suspend fun conservarVersionLocal(inventario: String): Result<Unit> = Result.success(Unit)
        override suspend fun usarVersionServidor(inventario: String): Result<Unit> = Result.success(Unit)
        override suspend fun reintentar(inventario: String): Result<Unit> = Result.success(Unit)
    }

    private class AuthFalso(rol: Rol) : AuthRepository {
        override val usuario: StateFlow<Usuario?> = MutableStateFlow(Usuario(1, "Prueba", "p@uaemex.mx", rol))
        override val sesionExpirada: StateFlow<Boolean> = MutableStateFlow(false)
        override val perfil: StateFlow<PerfilUsuario?> = MutableStateFlow(null)
        override fun entrarSinServidor(nombre: String): Usuario = usuario.value!!
        override suspend fun actualizarPerfil(): Result<PerfilUsuario> = Result.failure(Exception())
        override suspend fun iniciarSesion(correo: String, password: String): Result<Usuario> = Result.failure(Exception())
        override suspend fun cerrarSesion() = Unit
    }

    private class SyncFalso : SincronizacionRepository {
        var pedido = false
        override val estado: Flow<EstadoSincronizacion> = emptyFlow()
        override fun sincronizarAhora() { pedido = true }
        override fun programarPeriodica() = Unit
        override fun detener() = Unit
    }
}
