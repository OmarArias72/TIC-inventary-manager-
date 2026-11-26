package com.example.inventory.data.mapper

import com.example.inventory.data.local.EquipoComputoEntity
import com.example.inventory.data.local.SalaEntity
import com.example.inventory.data.remote.EquipoComputoRemotoDto
import com.example.inventory.data.remote.EquipoComputoRequestDto
import com.example.inventory.data.remote.SalaRemotaDto
import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.EquipoComputo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.Sala
import com.google.gson.Gson
import javax.inject.Inject

/** Conversiones de salas y equipos de cómputo entre Room, dominio y backend. */
class ComputoMapper @Inject constructor(
    private val gson: Gson,
) {
    fun sala(e: SalaEntity) = Sala(e.idSala, e.codigo, e.nombre, e.ubicacion)

    fun salaEntidad(dto: SalaRemotaDto): SalaEntity? {
        val id = dto.idSala ?: return null
        return SalaEntity(id, dto.codigoSala.orEmpty(), dto.nombre.orEmpty(), dto.ubicacion.orEmpty())
    }

    fun datos(e: EquipoComputoEntity) = DatosComputo(
        base = DatosEquipo(
            inventario = e.inventario,
            serie = e.serie,
            articulo = e.articulo,
            marca = e.marca,
            modelo = e.modelo,
            caracteristicas = e.caracteristicas,
            estado = EstadoEquipo.desdeTexto(e.estado),
        ),
        idSala = e.idSala,
        procesador = e.procesador,
        ram = e.ram,
        almacenamiento = e.almacenamiento,
        especificacionesHardware = e.especificacionesHardware,
        sistemaOperativo = e.sistemaOperativo,
        softwareInstalado = e.softwareInstalado,
        estadoComputadora = e.estadoComputadora,
        estadoCpu = e.estadoCpu,
        estadoMonitor = e.estadoMonitor,
        estadoTeclado = e.estadoTeclado,
        estadoMouse = e.estadoMouse,
        estadoCableEthernet = e.estadoCableEthernet,
        observaciones = e.observaciones,
    )

    fun datos(dto: EquipoComputoRemotoDto) = DatosComputo(
        base = DatosEquipo(
            inventario = dto.inventario.orEmpty(),
            serie = dto.serie.orEmpty(),
            articulo = dto.nombreArticulo.orEmpty(),
            marca = dto.nombreMarca.orEmpty(),
            modelo = dto.nombreModelo.orEmpty(),
            caracteristicas = dto.caracteristicas.orEmpty(),
            estado = EstadoEquipo.desdeTexto(dto.estado),
        ),
        idSala = dto.idSala,
        procesador = dto.procesador.orEmpty(),
        ram = dto.ram.orEmpty(),
        almacenamiento = dto.almacenamiento.orEmpty(),
        especificacionesHardware = dto.especificacionesHardware.orEmpty(),
        sistemaOperativo = dto.sistemaOperativo.orEmpty(),
        softwareInstalado = dto.softwareInstalado.orEmpty(),
        estadoComputadora = dto.estadoComputadora.orEmpty(),
        estadoCpu = dto.estadoCpu.orEmpty(),
        estadoMonitor = dto.estadoMonitor.orEmpty(),
        estadoTeclado = dto.estadoTeclado.orEmpty(),
        estadoMouse = dto.estadoMouse.orEmpty(),
        estadoCableEthernet = dto.estadoCableEthernet.orEmpty(),
        observaciones = dto.observaciones.orEmpty(),
    )

    fun aDominio(e: EquipoComputoEntity) = EquipoComputo(
        datos = datos(e),
        idServidor = e.idServidor,
        tieneContrasena = e.tieneContrasena,
        tieneUltimaContrasena = e.tieneUltimaContrasena,
        cambioContrasenaPendiente = e.contrasenaPendiente != null || e.borrarContrasena,
        fechaActualizacionLocal = e.fechaActualizacionLocal,
        existeEnServidor = e.existeEnServidor,
        estadoSync = e.estadoSync,
        ultimoError = e.ultimoError,
        actualizadoPor = e.actualizadoPor,
        versionServidor = versionServidor(e)?.let { datos(it) },
    )

    /** Copia los campos editables de [d] sobre [e] (o sobre una fila nueva si [e] es null). */
    fun conDatos(e: EquipoComputoEntity?, d: DatosComputo, ahora: Long): EquipoComputoEntity {
        val b = d.base
        val base = e ?: EquipoComputoEntity(
            inventario = b.inventario, idServidor = null, serie = "", articulo = "", marca = "", modelo = "",
            caracteristicas = "", estado = EstadoEquipo.ALTA.name, idSala = null, procesador = "", ram = "",
            almacenamiento = "", especificacionesHardware = "", sistemaOperativo = "", softwareInstalado = "",
            estadoComputadora = "", estadoCpu = "", estadoMonitor = "", estadoTeclado = "", estadoMouse = "",
            estadoCableEthernet = "", observaciones = "", tieneContrasena = false, tieneUltimaContrasena = false,
            contrasenaPendiente = null, borrarContrasena = false, motivoCambio = null, actualizadoPor = null,
            fechaRegistro = ahora, fechaActualizacionLocal = ahora, fechaActualizacionServidor = null,
            existeEnServidor = false, estadoSync = EstadoSync.PENDIENTE_CREAR, intentos = 0, ultimoError = null,
            versionServidor = null,
        )
        return base.copy(
            inventario = b.inventario,
            serie = b.serie,
            articulo = b.articulo,
            marca = b.marca,
            modelo = b.modelo,
            caracteristicas = b.caracteristicas,
            estado = b.estado.name,
            idSala = d.idSala,
            procesador = d.procesador,
            ram = d.ram,
            almacenamiento = d.almacenamiento,
            especificacionesHardware = d.especificacionesHardware,
            sistemaOperativo = d.sistemaOperativo,
            softwareInstalado = d.softwareInstalado,
            estadoComputadora = d.estadoComputadora,
            estadoCpu = d.estadoCpu,
            estadoMonitor = d.estadoMonitor,
            estadoTeclado = d.estadoTeclado,
            estadoMouse = d.estadoMouse,
            estadoCableEthernet = d.estadoCableEthernet,
            observaciones = d.observaciones,
            fechaActualizacionLocal = ahora,
        )
    }

    fun entidadSincronizada(dto: EquipoComputoRemotoDto, ahora: Long = System.currentTimeMillis()): EquipoComputoEntity =
        conDatos(null, datos(dto), ahora).copy(
            idServidor = dto.idEquipoComputo,
            tieneContrasena = dto.tieneContrasena == true,
            tieneUltimaContrasena = dto.tieneUltimaContrasena == true,
            actualizadoPor = dto.ultimoUsuarioActualizacionNombre,
            fechaRegistro = FechasServidor.aMilis(dto.fechaRegistro) ?: ahora,
            fechaActualizacionLocal = FechasServidor.aMilis(dto.fechaActualizacion) ?: ahora,
            fechaActualizacionServidor = dto.fechaActualizacion,
            existeEnServidor = true,
            estadoSync = EstadoSync.SINCRONIZADO,
        )

    fun aRequest(e: EquipoComputoEntity, idServidor: Int?): EquipoComputoRequestDto = EquipoComputoRequestDto(
        idEquipoComputo = idServidor,
        inventario = e.inventario,
        nombreArticulo = e.articulo,
        nombreMarca = e.marca,
        nombreModelo = e.modelo,
        serie = e.serie.ifBlank { null },
        cantidad = null,
        caracteristicas = e.caracteristicas.ifBlank { null },
        estado = e.estado,
        idSala = e.idSala,
        especificacionesHardware = e.especificacionesHardware.ifBlank { null },
        sistemaOperativo = e.sistemaOperativo.ifBlank { null },
        softwareInstalado = e.softwareInstalado.ifBlank { null },
        estadoTeclado = e.estadoTeclado.ifBlank { null },
        estadoMouse = e.estadoMouse.ifBlank { null },
        estadoCableEthernet = e.estadoCableEthernet.ifBlank { null },
        estadoMonitor = e.estadoMonitor.ifBlank { null },
        procesador = e.procesador.ifBlank { null },
        ram = e.ram.ifBlank { null },
        almacenamiento = e.almacenamiento.ifBlank { null },
        contrasena = e.contrasenaPendiente,
        borrarContrasena = if (e.borrarContrasena) true else null,
        estadoCpu = e.estadoCpu.ifBlank { null },
        estadoComputadora = e.estadoComputadora.ifBlank { null },
        observaciones = e.observaciones.ifBlank { null },
        motivoCambio = e.motivoCambio?.ifBlank { null } ?: "Sincronizado desde la app móvil",
    )

    fun aJson(dto: EquipoComputoRemotoDto): String = gson.toJson(dto)

    fun versionServidor(e: EquipoComputoEntity): EquipoComputoRemotoDto? = try {
        e.versionServidor?.let { gson.fromJson(it, EquipoComputoRemotoDto::class.java) }
    } catch (ex: Exception) {
        null
    }
}
