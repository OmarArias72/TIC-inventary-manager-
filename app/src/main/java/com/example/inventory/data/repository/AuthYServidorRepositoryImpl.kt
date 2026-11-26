package com.example.inventory.data.repository

import com.example.inventory.data.mapper.EquipoMapper
import com.example.inventory.data.remote.ApiErrores
import com.example.inventory.data.remote.AuthApi
import com.example.inventory.data.remote.LoginRequestDto
import com.example.inventory.data.remote.NormalizadorUrl
import com.example.inventory.data.remote.ServidorConfig
import com.example.inventory.data.remote.ServidorNoConfiguradoException
import com.example.inventory.data.remote.SesionStore
import com.example.inventory.data.remote.SinAutenticador
import com.example.inventory.domain.model.PerfilUsuario
import com.example.inventory.domain.model.ReglaNegocioException
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.model.Usuario
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.ServidorRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val sesion: SesionStore,
    private val mapper: EquipoMapper,
    private val gson: Gson,
    private val sincronizacion: SincronizacionRepository,
) : AuthRepository {

    override val usuario: StateFlow<Usuario?> = sesion.usuario
    override val sesionExpirada: StateFlow<Boolean> = sesion.sesionExpirada
    override val perfil: StateFlow<PerfilUsuario?> = sesion.perfil

    override fun entrarSinServidor(nombre: String): Usuario {
        // Rol ADMIN solo dentro del dispositivo: al conectarse, el servidor aplica los permisos reales
        // y lo que no esté permitido aparece como error en Sincronización.
        val usuario = Usuario(
            id = 0,
            nombre = nombre.trim().ifBlank { "Usuario local" },
            correo = "",
            rol = Rol.ADMIN,
            esLocal = true,
        )
        sincronizacion.detener()
        sesion.entrarModoLocal(usuario)
        return usuario
    }

    override suspend fun actualizarPerfil(): Result<PerfilUsuario> {
        if (sesion.usuario.value?.esLocal == true) {
            return Result.failure(ReglaNegocioException("Estás en modo sin servidor: inicia sesión para ver tu perfil."))
        }
        return try {
            val r = api.miPerfil()
            val dto = r.body()
            when {
                r.isSuccessful && dto != null -> {
                    val perfil = mapper.aPerfil(dto)
                    sesion.guardarPerfil(perfil)
                    Result.success(perfil)
                }
                r.code() == 401 -> Result.failure(ReglaNegocioException("Tu sesión expiró. Inicia sesión para actualizar tu perfil."))
                else -> Result.failure(ReglaNegocioException(ApiErrores.mensaje(gson, r)))
            }
        } catch (e: ServidorNoConfiguradoException) {
            Result.failure(ReglaNegocioException("Configura la URL del servidor."))
        } catch (e: IOException) {
            Result.failure(ReglaNegocioException("Sin conexión: se muestra la última copia guardada."))
        }
    }

    override suspend fun iniciarSesion(correo: String, password: String): Result<Usuario> {
        return try {
            val respuesta = api.login(LoginRequestDto(correo.trim(), password))
            if (!respuesta.isSuccessful) {
                return Result.failure(ReglaNegocioException(ApiErrores.mensaje(gson, respuesta)))
            }
            val dto = respuesta.body()?.usuario
                ?: return Result.failure(ReglaNegocioException("Respuesta inesperada del servidor."))
            val usuario = mapper.aUsuario(dto)
            if (!usuario.puedeUsarInventario) {
                try { api.logout() } catch (e: IOException) { /* sin importancia */ }
                sesion.cerrarSesion()
                return Result.failure(
                    ReglaNegocioException("Tu rol (${dto.nombreRol ?: "sin rol"}) no tiene acceso al inventario. Solo Administrador u Operador.")
                )
            }
            if (sesion.tokenRefresh() == null) {
                return Result.failure(ReglaNegocioException("El servidor no devolvió la sesión. Verifica la URL configurada."))
            }
            sesion.guardarUsuario(usuario)
            sesion.guardarPerfil(mapper.aPerfil(dto))
            sincronizacion.programarPeriodica()
            sincronizacion.sincronizarAhora()
            Result.success(usuario)
        } catch (e: ServidorNoConfiguradoException) {
            Result.failure(ReglaNegocioException("Configura la URL del servidor."))
        } catch (e: IOException) {
            Result.failure(ReglaNegocioException("No se pudo conectar con el servidor: ${e.message ?: "sin detalle"}"))
        }
    }

    override suspend fun cerrarSesion() {
        try {
            api.logout() // revoca el refresh token en el servidor
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin conexión: la sesión se cierra en el dispositivo de todos modos.
        }
        sincronizacion.detener()
        sesion.cerrarSesion()
    }
}

@Singleton
class ServidorRepositoryImpl @Inject constructor(
    private val config: ServidorConfig,
    @SinAutenticador private val cliente: OkHttpClient,
) : ServidorRepository {

    override val url: StateFlow<String?> = config.url

    override fun guardarUrl(texto: String): Result<String> =
        NormalizadorUrl.normalizar(texto).onSuccess { config.guardar(it) }

    override suspend fun probarConexion(): Result<String> = withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url("http://localhost/api/auth/me").get().build()
        try {
            cliente.newCall(peticion).execute().use { r ->
                when (r.code) {
                    200, 401 -> Result.success("Servidor disponible.")
                    404 -> Result.failure(ReglaNegocioException("En esa URL no se encontró la API (404). Verifica que el túnel apunte al frontend (con /api) o al backend."))
                    502, 503, 504, 530 -> Result.failure(ReglaNegocioException("El túnel responde, pero el servidor de destino no está disponible (${r.code})."))
                    else -> Result.failure(ReglaNegocioException("Respuesta inesperada del servidor (${r.code})."))
                }
            }
        } catch (e: ServidorNoConfiguradoException) {
            Result.failure(ReglaNegocioException("Configura la URL del servidor."))
        } catch (e: IOException) {
            Result.failure(ReglaNegocioException("No se pudo conectar: ${e.message ?: "sin detalle"}"))
        }
    }
}
