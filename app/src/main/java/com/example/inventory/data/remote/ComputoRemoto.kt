package com.example.inventory.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** Sala tal como la devuelve GET /api/salas (entidad Sala del backend). */
data class SalaRemotaDto(
    val idSala: Int?,
    val codigoSala: String?,
    val nombre: String?,
    val ubicacion: String?,
)

/** EquipoComputoResponse del backend. No trae contraseñas, solo si existen. */
data class EquipoComputoRemotoDto(
    val idEquipoComputo: Int?,
    val inventario: String?,
    val serie: String?,
    val cantidad: Int?,
    val caracteristicas: String?,
    val estado: String?,
    val nombreArticulo: String?,
    val nombreMarca: String?,
    val nombreModelo: String?,
    val idSala: Int?,
    val codigoSala: String?,
    val nombreSala: String?,
    val especificacionesHardware: String?,
    val sistemaOperativo: String?,
    val softwareInstalado: String?,
    val estadoTeclado: String?,
    val estadoMouse: String?,
    val estadoCableEthernet: String?,
    val estadoMonitor: String?,
    val procesador: String?,
    val ram: String?,
    val almacenamiento: String?,
    val tieneContrasena: Boolean?,
    val tieneUltimaContrasena: Boolean?,
    val fechaCambioContrasena: String?,
    val estadoCpu: String?,
    val estadoComputadora: String?,
    val observaciones: String?,
    val ultimoUsuarioActualizacionNombre: String?,
    val registradoPor: String?,
    val fechaRegistro: String?,
    val fechaActualizacion: String?,
)

/**
 * EquipoComputoRequest del backend. El PUT reemplaza TODOS los campos, por eso siempre se
 * envía el registro completo. Los null no se envían (Gson los omite) y el backend los guarda como null.
 */
data class EquipoComputoRequestDto(
    val idEquipoComputo: Int?,
    val inventario: String,
    val nombreArticulo: String,
    val nombreMarca: String,
    val nombreModelo: String,
    val serie: String?,
    val cantidad: Int?,
    val caracteristicas: String?,
    val estado: String,
    val idSala: Int?,
    val especificacionesHardware: String?,
    val sistemaOperativo: String?,
    val softwareInstalado: String?,
    val estadoTeclado: String?,
    val estadoMouse: String?,
    val estadoCableEthernet: String?,
    val estadoMonitor: String?,
    val procesador: String?,
    val ram: String?,
    val almacenamiento: String?,
    /** Solo cuando cambia; null = no se modifica. */
    val contrasena: String?,
    val borrarContrasena: Boolean?,
    val estadoCpu: String?,
    val estadoComputadora: String?,
    val observaciones: String?,
    /** Nota para la bitácora del equipo. */
    val motivoCambio: String?,
)

data class ContrasenasDto(
    val inventario: String?,
    val contrasena: String?,
    val ultimaContrasena: String?,
    val fechaCambioContrasena: String?,
)

/** EquipoComputoController y SalaController (Admin y Operador). */
interface ComputoApi {

    @GET("api/salas")
    suspend fun salas(): Response<List<SalaRemotaDto>>

    @GET("api/equipos-computo")
    suspend fun listar(): Response<List<EquipoComputoRemotoDto>>

    @GET("api/equipos-computo/{id}")
    suspend fun obtener(@Path("id") id: Int): Response<EquipoComputoRemotoDto>

    /** 404 si no hay equipo de cómputo con ese inventario. */
    @GET("api/equipos-computo/inventario/{inventario}")
    suspend fun obtenerPorInventario(@Path("inventario") inventario: String): Response<EquipoComputoRemotoDto>

    /** 201 · 409 INVENTARIO_DUPLICADO si ya es equipo de cómputo. Si el inventario existe solo como equipo general, se le agrega el detalle. */
    @POST("api/equipos-computo")
    suspend fun crear(@Body cuerpo: EquipoComputoRequestDto): Response<EquipoComputoRemotoDto>

    @PUT("api/equipos-computo/{id}")
    suspend fun actualizar(@Path("id") id: Int, @Body cuerpo: EquipoComputoRequestDto): Response<EquipoComputoRemotoDto>

    /** Descifra las contraseñas; cada consulta queda en la bitácora del equipo. */
    @GET("api/equipos-computo/{id}/contrasenas")
    suspend fun contrasenas(@Path("id") id: Int): Response<ContrasenasDto>
}
