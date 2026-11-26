package com.example.inventory.data.remote

import com.google.gson.Gson
import retrofit2.Response

object ApiErrores {

    /** Lee el campo "message" del error del backend; si no se puede, usa un texto genérico. */
    fun mensaje(gson: Gson, respuesta: Response<*>): String {
        val cuerpo = try {
            respuesta.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        val dto = try {
            cuerpo?.let { gson.fromJson(it, ApiErrorDto::class.java) }
        } catch (e: Exception) {
            null
        }
        return dto?.message?.takeIf { it.isNotBlank() } ?: "El servidor respondió con el código ${respuesta.code()}."
    }

    /**
     * Errores que pueden resolverse solos al reintentar: tiempo de espera, demasiadas
     * peticiones (límite de 300/min del backend), error del servidor o del túnel (502, 530...).
     */
    fun esTransitorio(codigo: Int): Boolean = codigo == 408 || codigo == 429 || codigo >= 500
}
