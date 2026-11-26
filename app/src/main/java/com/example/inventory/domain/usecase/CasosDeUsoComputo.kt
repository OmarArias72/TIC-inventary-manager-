package com.example.inventory.domain.usecase

import com.example.inventory.domain.model.CambioContrasena
import com.example.inventory.domain.model.CatalogosComputo
import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.ReglaNegocioException
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.EquipoComputoRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import javax.inject.Inject

/** Normaliza igual que el backend (EquipoComputoService.guardar) para comparar sin falsos conflictos. */
object NormalizadorComputo {
    fun normalizar(d: DatosComputo): DatosComputo = d.copy(
        base = NormalizadorEquipo.normalizar(d.base),
        procesador = d.procesador.trim(),
        ram = d.ram.trim(),
        almacenamiento = d.almacenamiento.trim(),
        especificacionesHardware = d.especificacionesHardware.trim(),
        sistemaOperativo = d.sistemaOperativo.trim(),
        softwareInstalado = d.softwareInstalado.trim(),
        estadoComputadora = CatalogosComputo.canonico(d.estadoComputadora, CatalogosComputo.ESTADO_COMPUTADORA),
        estadoCpu = CatalogosComputo.canonico(d.estadoCpu, CatalogosComputo.ESTADO_CPU),
        estadoMonitor = CatalogosComputo.canonico(d.estadoMonitor, CatalogosComputo.ESTADO_MONITOR),
        estadoTeclado = CatalogosComputo.canonico(d.estadoTeclado, CatalogosComputo.ESTADO_TECLADO),
        estadoMouse = CatalogosComputo.canonico(d.estadoMouse, CatalogosComputo.ESTADO_MOUSE),
        estadoCableEthernet = CatalogosComputo.canonico(d.estadoCableEthernet, CatalogosComputo.ESTADO_ETHERNET),
        observaciones = d.observaciones.trim(),
    )

    fun mismoContenido(a: DatosComputo, b: DatosComputo): Boolean = normalizar(a) == normalizar(b)
}

/** Alta o edición de un equipo de cómputo: valida, normaliza, aplica permisos y pide sincronizar. */
class GuardarEquipoComputoUseCase @Inject constructor(
    private val repositorio: EquipoComputoRepository,
    private val auth: AuthRepository,
    private val sincronizacion: SincronizacionRepository,
) {
    suspend operator fun invoke(
        inventarioOriginal: String?,
        datos: DatosComputo,
        contrasena: CambioContrasena,
        motivo: String,
    ): Result<Unit> {
        val d = NormalizadorComputo.normalizar(datos)
        validar(d, contrasena)?.let { return Result.failure(ReglaNegocioException(it)) }

        val rol = auth.usuario.value?.rol ?: Rol.OTRO
        val actual = inventarioOriginal?.let { repositorio.obtener(it) }
        if (d.base.estado !in PermisosInventario.estadosPermitidos(rol, actual?.datos?.base?.estado)) {
            return Result.failure(ReglaNegocioException("Solo el administrador puede reactivar un equipo dado de BAJA."))
        }
        val resultado = if (inventarioOriginal == null) repositorio.crear(d, contrasena, motivo.trim())
        else repositorio.actualizar(inventarioOriginal, d, contrasena, motivo.trim())
        if (resultado.isSuccess) sincronizacion.sincronizarAhora()
        return resultado
    }

    private fun validar(d: DatosComputo, contrasena: CambioContrasena): String? = when {
        d.base.inventario.isBlank() -> "El No. Inventario es obligatorio."
        d.base.inventario.length > NormalizadorEquipo.MAX_INVENTARIO -> "El No. Inventario admite máximo 100 caracteres."
        d.base.serie.length > NormalizadorEquipo.MAX_SERIE -> "El No. Serie admite máximo 100 caracteres."
        d.idSala == null -> "Selecciona la sala del equipo."
        d.sistemaOperativo.length > 100 -> "El sistema operativo admite máximo 100 caracteres."
        d.procesador.length > 100 -> "El procesador admite máximo 100 caracteres."
        d.ram.length > 50 -> "La RAM admite máximo 50 caracteres."
        d.almacenamiento.length > 100 -> "El almacenamiento admite máximo 100 caracteres."
        contrasena is CambioContrasena.Nueva && contrasena.valor.isBlank() -> "La contraseña nueva está vacía."
        contrasena is CambioContrasena.Nueva && contrasena.valor.length > 200 -> "La contraseña admite máximo 200 caracteres."
        else -> null
    }
}
