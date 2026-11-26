package com.example.inventory.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/*
 * Rutas SIN "/" inicial: así se respetan las rutas relativas a la URL base.
 * La URL base real la pone BaseUrlInterceptor en cada petición (la configura el usuario).
 * Todas devuelven Response<T> para poder revisar el código HTTP (401, 404, 409...).
 */

/** Endpoints de InventoryController (requieren rol Admin u Operador). */
interface InventarioApi {

    @GET("api/inventory/equipos")
    suspend fun listar(): Response<List<EquipoRemotoDto>>

    @GET("api/inventory/equipos/{inventario}")
    suspend fun obtener(@Path("inventario") inventario: String): Response<EquipoRemotoDto>

    /** 201 creado · 409 INVENTARIO_DUPLICADO si ya existe. */
    @POST("api/inventory/crear-equipo-movil")
    suspend fun crear(@Body cuerpo: EquipoRequestDto): Response<EquipoRemotoDto>

    /** 403 SOLO_ADMIN_BAJA si un Operador intenta reactivar un equipo en BAJA. */
    @PUT("api/inventory/{inventario}")
    suspend fun actualizar(
        @Path("inventario") inventario: String,
        @Body cuerpo: EquipoRequestDto,
    ): Response<EquipoRemotoDto>

    /** 204 · solo Admin (403 para Operador) · 404 si ya no existe. */
    @DELETE("api/inventory/{inventario}")
    suspend fun eliminar(@Path("inventario") inventario: String): Response<Unit>

    @POST("api/inventory/buscar-por-serie")
    suspend fun buscarPorSerie(@Body cuerpo: BusquedaRequestDto): Response<List<EquipoRemotoDto>>
}

/** Endpoints de AuthController. La sesión viaja en las cookies tic_access y tic_refresh. */
interface AuthApi {

    @POST("api/auth/login")
    suspend fun login(@Body cuerpo: LoginRequestDto): Response<SesionDto>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    /** 200 con sesión válida · 401 sin sesión. También sirve para saber si el backend responde. */
    @GET("api/auth/me")
    suspend fun yo(): Response<SesionDto>

    /** Perfil del usuario de la sesión (PerfilController). Solo devuelve los datos propios. */
    @GET("api/usuarios/me")
    suspend fun miPerfil(): Response<UsuarioDto>
}
