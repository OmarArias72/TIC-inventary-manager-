package com.example.inventory.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Guarda la URL del servidor que escribe el usuario (cambia cada vez que se abre un túnel nuevo). */
@Singleton
class ServidorConfig @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("servidor", Context.MODE_PRIVATE)
    private val _url = MutableStateFlow(prefs.getString(CLAVE_URL, null))
    val url: StateFlow<String?> = _url.asStateFlow()

    /** Se lee en cada petición (hilo de OkHttp), por eso es una lectura en memoria. */
    fun urlActual(): HttpUrl? = _url.value?.toHttpUrlOrNull()

    fun guardar(urlNormalizada: String) {
        prefs.edit().putString(CLAVE_URL, urlNormalizada).apply()
        _url.value = urlNormalizada
    }

    private companion object {
        const val CLAVE_URL = "url_base"
    }
}

/** Se lanza cuando todavía no hay URL configurada. Hereda de IOException para tratarse como fallo de red. */
class ServidorNoConfiguradoException : IOException("Configura la URL del servidor.")

object NormalizadorUrl {
    /**
     * "abc.trycloudflare.com" → "https://abc.trycloudflare.com/".
     * Quita un "/api" final (las rutas ya lo incluyen), la consulta y el fragmento.
     */
    fun normalizar(texto: String): Result<String> {
        var t = texto.trim()
        if (t.isEmpty()) return Result.failure(IllegalArgumentException("Escribe la URL del servidor."))
        if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) {
            t = "https://$t"
        }
        val url = t.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("La URL no es válida."))
        var ruta = url.encodedPath.trimEnd('/')
        if (ruta.endsWith("/api")) ruta = ruta.removeSuffix("/api")
        val limpia = url.newBuilder()
            .encodedPath("$ruta/")
            .query(null)
            .fragment(null)
            .build()
        return Result.success(limpia.toString())
    }
}
