package com.example.inventory.data.remote

/*
 * DTO = objetos que viajan en JSON. Copian EXACTAMENTE los nombres de campo del backend
 * (TICS-BACKEND, paquete inventario.dto y seguridad.dto). Todos los campos son nullables
 * porque Gson no respeta la nulabilidad de Kotlin: si el servidor omite un campo llega null.
 */

/** Respuesta de /api/inventory/... (EquipoVistaDTO del backend). Las fechas son LocalDateTime en texto ISO. */
data class EquipoRemotoDto(
    val inventario: String?,
    val serie: String?,
    val nombreArticulo: String?,
    val marca: String?,
    val modelo: String?,
    val cantidad: Int?,
    val caracteristicas: String?,
    val fechaRegistro: String?,
    val fechaActualizacion: String?,
    val estado: String?,
    val registradoPor: String?,
    val actualizadoPor: String?,
)

/** Cuerpo de POST /crear-equipo-movil (EquipoMovilCreate) y PUT /{inventario} (EquipoCreateRequest). */
data class EquipoRequestDto(
    val inventario: String,
    val nombreArticulo: String,
    val nombreMarca: String,
    val nombreModelo: String,
    val serie: String,
    val cantidad: Int?,
    val caracteristicas: String,
    val estado: String,
    /** Nota para la bitácora del equipo en el servidor. */
    val observacion: String?,
)

/** BusquedaRequest del backend: el campo se llama "query" (la app enviaba "termino"). */
data class BusquedaRequestDto(val query: String)

/** LoginRequest: el backend acepta "correo" como alias de "email". */
data class LoginRequestDto(val correo: String, val password: String)

data class SesionDto(
    val usuario: UsuarioDto?,
    val accesoExpiraEnSegundos: Long?,
)

data class UsuarioDto(
    val idUsuario: Int?,
    val nombre: String?,
    val apellidos: String?,
    val correo: String?,
    val nombreRol: String?,
    val activo: Boolean?,
    val correoVerificado: Boolean?,
    val ultimoAcceso: String?,
    val fechaCreacion: String?,
)

/** Formato único de error del backend (ApiError). */
data class ApiErrorDto(
    val status: Int?,
    val codigo: String?,
    val message: String?,
)
