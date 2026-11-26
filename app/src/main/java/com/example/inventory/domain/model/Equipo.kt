package com.example.inventory.domain.model

/** Estado del equipo en el inventario. Son los mismos valores que acepta el backend (CHECK de la BD). */
enum class EstadoEquipo {
    ALTA, MANTENIMIENTO, BAJA;

    companion object {
        fun desdeTexto(valor: String?): EstadoEquipo =
            entries.firstOrNull { it.name.equals(valor?.trim(), ignoreCase = true) } ?: ALTA
    }
}

/**
 * Estado de sincronización de un registro local respecto al servidor.
 *
 * - SINCRONIZADO: igual que en el servidor.
 * - PENDIENTE_CREAR / ACTUALIZAR / ELIMINAR: cambio local que falta enviar.
 * - CONFLICTO: el servidor cambió mientras había un cambio local; el usuario decide.
 * - ERROR: el servidor rechazó el cambio (permisos o datos); no se reintenta solo.
 */
enum class EstadoSync {
    SINCRONIZADO, PENDIENTE_CREAR, PENDIENTE_ACTUALIZAR, PENDIENTE_ELIMINAR, CONFLICTO, ERROR;

    val esPendienteDeEnvio: Boolean
        get() = this == PENDIENTE_CREAR || this == PENDIENTE_ACTUALIZAR || this == PENDIENTE_ELIMINAR
}

/** Campos editables de un equipo (lo que captura el formulario y lo que se compara en conflictos). */
data class DatosEquipo(
    val inventario: String = "",
    val serie: String = "",
    val articulo: String = "",
    val marca: String = "",
    val modelo: String = "",
    /** Se muestra como "Observaciones"; en el backend es el campo `caracteristicas`. */
    val caracteristicas: String = "",
    val estado: EstadoEquipo = EstadoEquipo.ALTA,
)

/** Equipo tal como lo conoce la app (modelo de dominio: no depende de Room ni de Retrofit). */
data class Equipo(
    val inventario: String,
    val serie: String,
    val articulo: String,
    val marca: String,
    val modelo: String,
    val caracteristicas: String,
    val estado: EstadoEquipo,
    val fechaRegistro: Long,
    val fechaActualizacionLocal: Long,
    /** true si el equipo ya existe en el servidor (decide si se envía como alta o como edición). */
    val existeEnServidor: Boolean,
    val estadoSync: EstadoSync,
    val ultimoError: String?,
    /** Versión del servidor guardada al detectar un conflicto (null si no hay conflicto o si se eliminó allá). */
    val versionServidor: DatosEquipo?,
) {
    val datos: DatosEquipo
        get() = DatosEquipo(inventario, serie, articulo, marca, modelo, caracteristicas, estado)

    /** Conflicto en el que otra persona eliminó el equipo del servidor. */
    val eliminadoEnServidor: Boolean
        get() = estadoSync == EstadoSync.CONFLICTO && versionServidor == null
}

/** Resultado de buscar un equipo por el código escaneado o escrito. */
sealed interface ResultadoBusqueda {
    data class Encontrado(val equipo: Equipo, val desdeServidor: Boolean) : ResultadoBusqueda
    data object NoEncontrado : ResultadoBusqueda
    /** No está en el dispositivo y no se pudo consultar el servidor. */
    data class SoloLocal(val motivo: String) : ResultadoBusqueda
}

/** Error de regla de negocio con un mensaje para mostrar al usuario. */
class ReglaNegocioException(mensaje: String) : Exception(mensaje)
