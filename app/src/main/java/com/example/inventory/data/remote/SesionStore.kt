package com.example.inventory.data.remote

import android.content.Context
import com.example.inventory.domain.model.PerfilUsuario
import com.example.inventory.domain.model.Usuario
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda la sesión en el almacenamiento privado de la app:
 * - token de acceso (JWT, dura 15 min en el backend) y token de renovación (refresh),
 *   cada uno con su fecha de expiración;
 * - el usuario, para poder abrir la app y trabajar sin conexión.
 *
 * Se usa SharedPreferences (lectura síncrona) porque OkHttp necesita los tokens en el
 * momento de enviar cada petición. Los tokens se guardan sin depender del host, así la
 * sesión sobrevive aunque cambie la URL del túnel.
 */
@Singleton
class SesionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val prefs = context.getSharedPreferences("sesion", Context.MODE_PRIVATE)

    private val _usuario = MutableStateFlow(leerUsuario())
    val usuario: StateFlow<Usuario?> = _usuario.asStateFlow()

    private val _perfil = MutableStateFlow(leerPerfil())
    val perfil: StateFlow<PerfilUsuario?> = _perfil.asStateFlow()

    private val _expirada = MutableStateFlow(prefs.getBoolean(K_EXPIRADA, false))
    val sesionExpirada: StateFlow<Boolean> = _expirada.asStateFlow()

    /** Momento del último refresh exitoso (evita refrescar dos veces con el mismo token). */
    @Volatile
    var ultimoRefreshMs: Long = 0L

    @Synchronized
    fun tokenAcceso(ahora: Long = System.currentTimeMillis()): String? =
        vigente(K_ACCESS, K_ACCESS_EXP, ahora)

    @Synchronized
    fun tokenRefresh(ahora: Long = System.currentTimeMillis()): String? =
        vigente(K_REFRESH, K_REFRESH_EXP, ahora)

    @Synchronized
    fun guardarAcceso(token: String, expiraEn: Long) {
        prefs.edit().putString(K_ACCESS, token).putLong(K_ACCESS_EXP, expiraEn).apply()
    }

    @Synchronized
    fun guardarRefresh(token: String, expiraEn: Long) {
        prefs.edit()
            .putString(K_REFRESH, token)
            .putLong(K_REFRESH_EXP, expiraEn)
            .putBoolean(K_EXPIRADA, false)
            .apply()
        _expirada.value = false
    }

    @Synchronized
    fun borrarAcceso() {
        prefs.edit().remove(K_ACCESS).remove(K_ACCESS_EXP).apply()
    }

    @Synchronized
    fun borrarRefresh() {
        prefs.edit().remove(K_REFRESH).remove(K_REFRESH_EXP).apply()
    }

    fun guardarUsuario(usuario: Usuario) {
        prefs.edit().putString(K_USUARIO, gson.toJson(usuario)).apply()
        _usuario.value = usuario
    }

    fun guardarPerfil(perfil: PerfilUsuario) {
        prefs.edit().putString(K_PERFIL, gson.toJson(perfil)).apply()
        _perfil.value = perfil
    }

    /** Modo sin servidor: usuario local, sin tokens ni perfil del servidor. */
    @Synchronized
    fun entrarModoLocal(usuario: Usuario) {
        prefs.edit().clear().putString(K_USUARIO, gson.toJson(usuario)).apply()
        _usuario.value = usuario
        _perfil.value = null
        _expirada.value = false
        ultimoRefreshMs = 0L
    }

    /** El servidor ya no acepta la sesión. Se conserva el usuario para seguir trabajando sin conexión. */
    @Synchronized
    fun marcarExpirada() {
        prefs.edit()
            .remove(K_ACCESS).remove(K_ACCESS_EXP)
            .remove(K_REFRESH).remove(K_REFRESH_EXP)
            .putBoolean(K_EXPIRADA, true)
            .apply()
        _expirada.value = true
    }

    @Synchronized
    fun cerrarSesion() {
        prefs.edit().clear().apply()
        _usuario.value = null
        _perfil.value = null
        _expirada.value = false
        ultimoRefreshMs = 0L
    }

    private fun vigente(claveToken: String, claveExpira: String, ahora: Long): String? {
        val token = prefs.getString(claveToken, null) ?: return null
        return if (prefs.getLong(claveExpira, 0L) > ahora) token else null
    }

    private fun leerUsuario(): Usuario? = try {
        prefs.getString(K_USUARIO, null)?.let { gson.fromJson(it, Usuario::class.java) }
    } catch (e: Exception) {
        null
    }

    private fun leerPerfil(): PerfilUsuario? = try {
        prefs.getString(K_PERFIL, null)?.let { gson.fromJson(it, PerfilUsuario::class.java) }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val K_PERFIL = "perfil"
        const val K_ACCESS = "access"
        const val K_ACCESS_EXP = "access_expira"
        const val K_REFRESH = "refresh"
        const val K_REFRESH_EXP = "refresh_expira"
        const val K_USUARIO = "usuario"
        const val K_EXPIRADA = "expirada"
    }
}
