package com.example.inventory.domain.model

/** Roles que importan a la app. El backend usa los nombres "Admin", "Operador-tics", etc. */
enum class Rol {
    ADMIN, OPERADOR, OTRO;

    companion object {
        /** Misma conversión que Roles.codigo() del backend. */
        fun desdeNombre(nombreRol: String?): Rol = when (nombreRol?.trim()?.lowercase()) {
            "admin" -> ADMIN
            "operador-tics", "operador" -> OPERADOR
            else -> OTRO
        }
    }
}

data class Usuario(
    val id: Int,
    val nombre: String,
    val correo: String,
    val rol: Rol,
    /**
     * true = "modo sin servidor": la app se usa solo en el dispositivo (p. ej. antes de desplegar
     * el backend). No se sincroniza nada hasta que se inicie sesión con una cuenta real.
     */
    val esLocal: Boolean = false,
) {
    /** El backend solo permite /api/inventory a Admin y Operador. */
    val puedeUsarInventario: Boolean get() = rol == Rol.ADMIN || rol == Rol.OPERADOR
}

/** Datos de la cuenta tal como los tiene el servidor (GET /api/usuarios/me). Solo lectura. */
data class PerfilUsuario(
    val id: Int,
    val nombre: String,
    val apellidos: String,
    val correo: String,
    val nombreRol: String,
    val activo: Boolean,
    val correoVerificado: Boolean,
    val ultimoAccesoMs: Long?,
    val fechaCreacionMs: Long?,
    /** Cuándo se descargó esta copia (para mostrar "actualizado hace..."). */
    val descargadoMs: Long,
) {
    val nombreCompleto: String get() = listOf(nombre, apellidos).filter { it.isNotBlank() }.joinToString(" ")
}
