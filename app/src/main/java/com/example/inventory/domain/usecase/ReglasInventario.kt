package com.example.inventory.domain.usecase

import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.Rol

/**
 * Reglas de permisos del backend (InventoryService.exigirPermisoEstado y SecurityConfig),
 * aplicadas también en la app para que un cambio hecho sin conexión no sea rechazado después.
 * Desde 2026-11-06: Admin y Operador dan de BAJA; solo el Admin reactiva un equipo en BAJA.
 */
object PermisosInventario {

    /** Solo Admin elimina en el servidor. Un registro que nunca se envió se puede borrar localmente. */
    fun puedeEliminar(rol: Rol, equipo: Equipo): Boolean =
        rol == Rol.ADMIN || !equipo.existeEnServidor

    /** Cualquiera puede dar de BAJA; solo el Admin saca un equipo de BAJA (reactivarlo). */
    fun estadosPermitidos(rol: Rol, estadoActual: EstadoEquipo?): List<EstadoEquipo> = when {
        rol == Rol.ADMIN -> EstadoEquipo.entries
        estadoActual == EstadoEquipo.BAJA -> listOf(EstadoEquipo.BAJA)
        else -> EstadoEquipo.entries
    }
}

/**
 * Normaliza los datos igual que el backend (InventoryService.resolverArticulo/Marca/Modelo):
 * nombres en mayúsculas y valores por defecto cuando vienen vacíos. Así lo que se guarda en
 * el dispositivo coincide con lo que guardará el servidor y la comparación de conflictos es fiable.
 */
object NormalizadorEquipo {
    const val ARTICULO_POR_DEFECTO = "ARTÍCULO GENERAL"
    const val MARCA_POR_DEFECTO = "GENÉRICA"
    const val MODELO_POR_DEFECTO = "GENÉRICO"

    const val MAX_INVENTARIO = 100
    const val MAX_SERIE = 100
    const val MAX_NOMBRE = 255

    fun normalizar(d: DatosEquipo): DatosEquipo = d.copy(
        inventario = d.inventario.trim(),
        serie = d.serie.trim(),
        articulo = d.articulo.trim().uppercase().ifBlank { ARTICULO_POR_DEFECTO },
        marca = d.marca.trim().uppercase().ifBlank { MARCA_POR_DEFECTO },
        modelo = d.modelo.trim().uppercase().ifBlank { MODELO_POR_DEFECTO },
        caracteristicas = d.caracteristicas.trim(),
    )

    fun mismoContenido(a: DatosEquipo, b: DatosEquipo): Boolean = normalizar(a) == normalizar(b)
}
