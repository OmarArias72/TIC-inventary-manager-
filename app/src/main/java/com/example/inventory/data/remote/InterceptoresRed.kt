package com.example.inventory.data.remote

import okhttp3.Authenticator
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** Cliente OkHttp sin renovación automática de sesión (para el refresh y la prueba de conexión). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SinAutenticador

/**
 * Cambia "http://localhost/" (URL fija de Retrofit) por la URL que configuró el usuario.
 * Así se puede cambiar el servidor sin reiniciar la app.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val config: ServidorConfig,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val base = config.urlActual() ?: throw ServidorNoConfiguradoException()
        val original = chain.request()
        val nuevaUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(base.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()
        val peticion = original.newBuilder()
            .url(nuevaUrl)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(peticion)
    }
}

/**
 * Maneja las cookies de sesión del backend como si fueran tokens:
 * - Al recibir Set-Cookie (login/refresh/logout) guarda o borra tic_access y tic_refresh.
 * - En cada petición envía tic_access, y tic_refresh solo a /api/auth/ (igual que el navegador).
 * No dependen del host, por eso la sesión sigue valiendo si cambia la URL del túnel.
 */
@Singleton
class SesionCookieJar @Inject constructor(
    private val sesion: SesionStore,
) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val ahora = System.currentTimeMillis()
        cookies.forEach { cookie ->
            val vigente = cookie.value.isNotBlank() && cookie.expiresAt > ahora
            when (cookie.name) {
                ACCESS -> if (vigente) sesion.guardarAcceso(cookie.value, cookie.expiresAt) else sesion.borrarAcceso()
                REFRESH -> if (vigente) sesion.guardarRefresh(cookie.value, cookie.expiresAt) else sesion.borrarRefresh()
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val lista = mutableListOf<Cookie>()
        sesion.tokenAcceso()?.let { lista += crear(ACCESS, it, url) }
        if (url.encodedPath.contains("/api/auth/")) {
            sesion.tokenRefresh()?.let { lista += crear(REFRESH, it, url) }
        }
        return lista
    }

    private fun crear(nombre: String, valor: String, url: HttpUrl): Cookie =
        Cookie.Builder().name(nombre).value(valor).hostOnlyDomain(url.host).path("/").build()

    companion object {
        const val ACCESS = "tic_access"
        const val REFRESH = "tic_refresh"
    }
}

/**
 * Cuando el backend responde 401 (el token de acceso de 15 min expiró), OkHttp llama a este
 * Authenticator: renueva la sesión con POST /api/auth/refresh y repite la petición original.
 *
 * Es `synchronized` porque el backend ROTA el refresh token y, si recibe dos veces el mismo,
 * lo trata como robo y cierra TODAS las sesiones del usuario. Si dos peticiones fallan a la
 * vez, solo la primera renueva; la segunda ve que ya hubo un refresh y solo reintenta.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sesion: SesionStore,
    @SinAutenticador private val cliente: OkHttpClient,
) : Authenticator {

    private val candado = Any()

    @Throws(IOException::class)
    override fun authenticate(route: Route?, response: Response): Request? {
        val ruta = response.request.url.encodedPath
        if (RUTAS_SIN_REFRESH.any { ruta.contains(it) }) return null
        if (contarIntentos(response) >= 2) return null

        return synchronized(candado) { renovarYReintentar(response) }
    }

    private fun renovarYReintentar(response: Response): Request? {
        // Otra petición ya renovó la sesión después de que se envió esta: solo reintentar.
        if (sesion.ultimoRefreshMs > response.sentRequestAtMillis) {
            return response.request.newBuilder().build()
        }
        if (sesion.tokenRefresh() == null) {
            sesion.marcarExpirada()
            return null
        }
        // Un IOException aquí (sin red) se propaga: es un fallo de red, no una sesión expirada.
        val renovada = cliente.newCall(peticionRefresh()).execute().use { it.isSuccessful }
        return if (renovada) {
            sesion.ultimoRefreshMs = System.currentTimeMillis()
            response.request.newBuilder().build()
        } else {
            sesion.marcarExpirada()
            null
        }
    }

    private fun peticionRefresh(): Request = Request.Builder()
        .url("http://localhost/api/auth/refresh") // BaseUrlInterceptor pone la URL real
        .post("".toRequestBody("application/json".toMediaType()))
        .build()

    private fun contarIntentos(response: Response): Int {
        var r: Response? = response
        var n = 0
        while (r != null) {
            n++
            r = r.priorResponse
        }
        return n
    }

    private companion object {
        val RUTAS_SIN_REFRESH = listOf("/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
    }
}
