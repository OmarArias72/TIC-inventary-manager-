package com.example.inventory.domain.model

/**
 * Sala (aula/laboratorio). Las del servidor tienen id positivo. Las creadas en el dispositivo
 * (modo sin servidor) tienen id negativo y al sincronizar se emparejan por código con las del servidor.
 */
data class Sala(
    val id: Int,
    val codigo: String,
    val nombre: String,
    val ubicacion: String,
) {
    val esLocal: Boolean get() = id < 0
    val etiqueta: String
        get() = (if (nombre.isBlank()) codigo else "$codigo · $nombre") + if (esLocal) " (local)" else ""
}

/**
 * Campos editables de un equipo de cómputo: los del equipo base (inventario, serie, marca...)
 * más la sala y el detalle técnico. Un texto vacío significa "sin dato".
 */
data class DatosComputo(
    val base: DatosEquipo = DatosEquipo(articulo = "CPU"),
    val idSala: Int? = null,
    val procesador: String = "",
    val ram: String = "",
    val almacenamiento: String = "",
    val especificacionesHardware: String = "",
    val sistemaOperativo: String = "",
    val softwareInstalado: String = "",
    val estadoComputadora: String = "",
    val estadoCpu: String = "",
    val estadoMonitor: String = "",
    val estadoTeclado: String = "",
    val estadoMouse: String = "",
    val estadoCableEthernet: String = "",
    val observaciones: String = "",
)

/** Equipo de cómputo tal como lo conoce la app. */
data class EquipoComputo(
    val datos: DatosComputo,
    /** idEquipoComputo del servidor (null si todavía no se ha enviado). */
    val idServidor: Int?,
    val tieneContrasena: Boolean,
    val tieneUltimaContrasena: Boolean,
    /** Hay un cambio de contraseña guardado en el dispositivo que falta enviar. */
    val cambioContrasenaPendiente: Boolean,
    val fechaActualizacionLocal: Long,
    val existeEnServidor: Boolean,
    val estadoSync: EstadoSync,
    val ultimoError: String?,
    val actualizadoPor: String?,
    val versionServidor: DatosComputo?,
) {
    val inventario: String get() = datos.base.inventario
    val eliminadoEnServidor: Boolean get() = estadoSync == EstadoSync.CONFLICTO && versionServidor == null
}

/** Qué hacer con la contraseña del equipo al guardar. */
sealed interface CambioContrasena {
    data object SinCambio : CambioContrasena
    data class Nueva(val valor: String) : CambioContrasena
    data object Borrar : CambioContrasena
}

/** Contraseñas descifradas (solo en línea; cada consulta queda en la bitácora del servidor). */
data class ContrasenasEquipo(
    val actual: String?,
    val ultima: String?,
    val fechaCambio: String?,
)

/**
 * Valores permitidos para los estados (los mismos CHECK de la BD y de EstadosEquipo.java del
 * backend). Si el backend cambia el catálogo, hay que actualizarlos aquí.
 */
object CatalogosComputo {
    val ESTADO_COMPUTADORA = listOf("Funcionando", "No funciona", "Requiere revisión", "Requiere Mantenimiento")
    val ESTADO_CPU = listOf("Funciona", "No funciona", "Con fallas", "En revisión", "Requiere mantenimiento")
    val ESTADO_MONITOR = listOf("Bueno", "Sin imagen", "Imagen distorsionada", "Píxeles muertos", "Pantalla rayada", "Pantalla rota", "Dañado")
    val ESTADO_TECLADO = listOf("Bueno", "Teclas dañadas", "Teclas faltantes", "Algunas teclas no responden", "Sin respuesta", "Cable dañado", "Dañado")
    val ESTADO_MOUSE = listOf("Bueno", "Clic dañado", "Scroll dañado", "Sin respuesta", "Cable dañado", "Dañado")
    val ESTADO_ETHERNET = listOf("Bueno", "Conector dañado", "Cable cortado", "Sin conexión", "Dañado")

    /** Devuelve el valor con la escritura del catálogo (sin distinguir mayúsculas), o "" si no está. */
    fun canonico(valor: String, catalogo: List<String>): String =
        catalogo.firstOrNull { it.equals(valor.trim(), ignoreCase = true) } ?: ""
}
