package com.example.inventory.domain.model

/** Resultado de una ejecución del proceso de sincronización. */
sealed interface ResultadoSincronizacion {
    data class Completada(
        val enviados: Int,
        val conflictos: Int,
        val errores: Int,
        val descargados: Int,
    ) : ResultadoSincronizacion

    /** Red caída, servidor no disponible, etc. Se reintenta automáticamente. */
    data class FalloTemporal(val motivo: String) : ResultadoSincronizacion

    /** No hay sesión o expiró: hay que iniciar sesión para continuar. */
    data object SinSesion : ResultadoSincronizacion

    /** No se ha configurado la URL del servidor. */
    data object SinServidor : ResultadoSincronizacion
}

fun ResultadoSincronizacion.mensaje(): String = when (this) {
    is ResultadoSincronizacion.Completada -> buildString {
        append("Sincronización completa: $enviados enviados, $descargados equipos en el servidor")
        if (conflictos > 0) append(", $conflictos conflicto(s)")
        if (errores > 0) append(", $errores con error")
        append(".")
    }
    is ResultadoSincronizacion.FalloTemporal -> "$motivo Se reintentará automáticamente."
    ResultadoSincronizacion.SinSesion -> "Tu sesión expiró. Inicia sesión para sincronizar."
    ResultadoSincronizacion.SinServidor -> "Configura la URL del servidor para sincronizar."
}

/** Resumen de la última sincronización (se muestra en pantalla). */
data class ResumenSincronizacion(
    val fechaMs: Long,
    val exitosa: Boolean,
    val mensaje: String,
)

/** Estado que observa la interfaz. */
data class EstadoSincronizacion(
    val sincronizando: Boolean = false,
    val pendientes: Int = 0,
    val conflictos: Int = 0,
    val errores: Int = 0,
    val ultima: ResumenSincronizacion? = null,
)
