package com.example.inventory.domain.usecase

import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.ReglaNegocioException
import com.example.inventory.domain.model.ResultadoBusqueda
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.EquipoComputoRepository
import com.example.inventory.domain.repository.EquipoRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import javax.inject.Inject

/**
 * Alta o edición de un equipo: valida, normaliza, aplica los permisos del rol,
 * guarda localmente y pide sincronizar.
 */
class GuardarEquipoUseCase @Inject constructor(
    private val repositorio: EquipoRepository,
    private val auth: AuthRepository,
    private val sincronizacion: SincronizacionRepository,
) {
    /** @param inventarioOriginal null = alta; con valor = edición de ese equipo. */
    suspend operator fun invoke(inventarioOriginal: String?, datos: DatosEquipo): Result<Unit> {
        val d = NormalizadorEquipo.normalizar(datos)
        validar(d)?.let { return Result.failure(ReglaNegocioException(it)) }

        val rol = auth.usuario.value?.rol ?: Rol.OTRO
        val actual = inventarioOriginal?.let { repositorio.obtener(it) }
        if (d.estado !in PermisosInventario.estadosPermitidos(rol, actual?.estado)) {
            return Result.failure(
                ReglaNegocioException("Solo el administrador puede reactivar un equipo dado de BAJA.")
            )
        }

        val resultado = if (inventarioOriginal == null) repositorio.crear(d)
        else repositorio.actualizar(inventarioOriginal, d)
        if (resultado.isSuccess) sincronizacion.sincronizarAhora()
        return resultado
    }

    private fun validar(d: DatosEquipo): String? = when {
        d.inventario.isBlank() -> "El No. Inventario es obligatorio."
        d.inventario.length > NormalizadorEquipo.MAX_INVENTARIO -> "El No. Inventario admite máximo ${NormalizadorEquipo.MAX_INVENTARIO} caracteres."
        d.serie.length > NormalizadorEquipo.MAX_SERIE -> "El No. Serie admite máximo ${NormalizadorEquipo.MAX_SERIE} caracteres."
        listOf(d.articulo, d.marca, d.modelo).any { it.length > NormalizadorEquipo.MAX_NOMBRE } ->
            "Artículo, marca y modelo admiten máximo ${NormalizadorEquipo.MAX_NOMBRE} caracteres."
        else -> null
    }
}

class EliminarEquipoUseCase @Inject constructor(
    private val repositorio: EquipoRepository,
    private val auth: AuthRepository,
    private val sincronizacion: SincronizacionRepository,
) {
    suspend operator fun invoke(inventario: String): Result<Unit> {
        val equipo = repositorio.obtener(inventario)
            ?: return Result.failure(ReglaNegocioException("El equipo ya no existe en el dispositivo."))
        val rol = auth.usuario.value?.rol ?: Rol.OTRO
        if (!PermisosInventario.puedeEliminar(rol, equipo)) {
            return Result.failure(ReglaNegocioException("Solo el administrador puede eliminar equipos del inventario."))
        }
        val resultado = repositorio.eliminar(inventario)
        if (resultado.isSuccess) sincronizacion.sincronizarAhora()
        return resultado
    }
}

class BuscarEquipoPorCodigoUseCase @Inject constructor(
    private val repositorio: EquipoRepository,
) {
    suspend operator fun invoke(codigo: String): ResultadoBusqueda {
        val limpio = codigo.trim()
        if (limpio.isEmpty()) return ResultadoBusqueda.NoEncontrado
        return repositorio.buscarPorCodigo(limpio)
    }
}

enum class Resolucion { CONSERVAR_LOCAL, USAR_SERVIDOR, REINTENTAR }

/** Resuelve un conflicto o un error de sincronización según la decisión del usuario. */
class ResolverSincronizacionUseCase @Inject constructor(
    private val repositorio: EquipoRepository,
    private val computo: EquipoComputoRepository,
    private val sincronizacion: SincronizacionRepository,
) {
    /** @param esComputo true si el registro es un equipo de cómputo (tabla distinta). */
    suspend operator fun invoke(inventario: String, resolucion: Resolucion, esComputo: Boolean = false): Result<Unit> {
        val resultado = if (esComputo) {
            when (resolucion) {
                Resolucion.CONSERVAR_LOCAL -> computo.conservarVersionLocal(inventario)
                Resolucion.USAR_SERVIDOR -> computo.usarVersionServidor(inventario)
                Resolucion.REINTENTAR -> computo.reintentar(inventario)
            }
        } else {
            when (resolucion) {
                Resolucion.CONSERVAR_LOCAL -> repositorio.conservarVersionLocal(inventario)
                Resolucion.USAR_SERVIDOR -> repositorio.usarVersionServidor(inventario)
                Resolucion.REINTENTAR -> repositorio.reintentar(inventario)
            }
        }
        if (resultado.isSuccess) sincronizacion.sincronizarAhora()
        return resultado
    }
}
