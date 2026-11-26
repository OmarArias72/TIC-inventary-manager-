package com.example.inventory.data.mapper

import com.example.inventory.data.local.EquipoEntity
import com.example.inventory.data.remote.EquipoRemotoDto
import com.example.inventory.data.remote.EquipoRequestDto
import com.example.inventory.data.remote.UsuarioDto
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.PerfilUsuario
import com.example.inventory.domain.model.Rol
import com.example.inventory.domain.model.Usuario
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** El backend envía LocalDateTime sin zona, en la hora del campus (America/Mexico_City). */
object FechasServidor {
    private val ZONA: ZoneId = ZoneId.of("America/Mexico_City")

    fun aMilis(texto: String?): Long? {
        if (texto.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(texto).atZone(ZONA).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

/**
 * Conversiones entre capas: Entity (Room) ↔ dominio ↔ DTO (backend).
 * Ninguna otra clase conoce los detalles de cada formato.
 */
class EquipoMapper @Inject constructor(
    private val gson: Gson,
) {
    fun aDominio(e: EquipoEntity): Equipo = Equipo(
        inventario = e.inventario,
        serie = e.serie,
        articulo = e.articulo,
        marca = e.marca,
        modelo = e.modelo,
        caracteristicas = e.caracteristicas,
        estado = EstadoEquipo.desdeTexto(e.estado),
        fechaRegistro = e.fechaRegistro,
        fechaActualizacionLocal = e.fechaActualizacionLocal,
        existeEnServidor = e.existeEnServidor,
        estadoSync = e.estadoSync,
        ultimoError = e.ultimoError,
        versionServidor = versionServidor(e)?.let { aDatos(it) },
    )

    fun datos(e: EquipoEntity): DatosEquipo = DatosEquipo(
        inventario = e.inventario,
        serie = e.serie,
        articulo = e.articulo,
        marca = e.marca,
        modelo = e.modelo,
        caracteristicas = e.caracteristicas,
        estado = EstadoEquipo.desdeTexto(e.estado),
    )

    fun aDatos(dto: EquipoRemotoDto): DatosEquipo = DatosEquipo(
        inventario = dto.inventario.orEmpty(),
        serie = dto.serie.orEmpty(),
        articulo = dto.nombreArticulo.orEmpty(),
        marca = dto.marca.orEmpty(),
        modelo = dto.modelo.orEmpty(),
        caracteristicas = dto.caracteristicas.orEmpty(),
        estado = EstadoEquipo.desdeTexto(dto.estado),
    )

    /** Registro local nuevo (alta hecha en el dispositivo). */
    fun nuevaEntidad(d: DatosEquipo, ahora: Long): EquipoEntity = EquipoEntity(
        inventario = d.inventario,
        serie = d.serie,
        articulo = d.articulo,
        marca = d.marca,
        modelo = d.modelo,
        caracteristicas = d.caracteristicas,
        estado = d.estado.name,
        fechaRegistro = ahora,
        fechaActualizacionLocal = ahora,
        fechaActualizacionServidor = null,
        existeEnServidor = false,
        eliminado = false,
        estadoSync = EstadoSync.PENDIENTE_CREAR,
        intentos = 0,
        ultimoError = null,
        versionServidor = null,
    )

    /** Copia local de un equipo tal como está en el servidor. */
    fun entidadSincronizada(dto: EquipoRemotoDto, ahora: Long = System.currentTimeMillis()): EquipoEntity {
        val d = aDatos(dto)
        return EquipoEntity(
            inventario = d.inventario,
            serie = d.serie,
            articulo = d.articulo,
            marca = d.marca,
            modelo = d.modelo,
            caracteristicas = d.caracteristicas,
            estado = d.estado.name,
            fechaRegistro = FechasServidor.aMilis(dto.fechaRegistro) ?: ahora,
            fechaActualizacionLocal = FechasServidor.aMilis(dto.fechaActualizacion) ?: ahora,
            fechaActualizacionServidor = dto.fechaActualizacion,
            existeEnServidor = true,
            eliminado = false,
            estadoSync = EstadoSync.SINCRONIZADO,
            intentos = 0,
            ultimoError = null,
            versionServidor = null,
        )
    }

    fun aRequest(e: EquipoEntity): EquipoRequestDto = EquipoRequestDto(
        inventario = e.inventario,
        nombreArticulo = e.articulo,
        nombreMarca = e.marca,
        nombreModelo = e.modelo,
        serie = e.serie,
        cantidad = null, // el backend usa 1 en altas y conserva el valor en ediciones
        caracteristicas = e.caracteristicas,
        estado = e.estado,
        observacion = "Sincronizado desde la app móvil",
    )

    fun aJson(dto: EquipoRemotoDto): String = gson.toJson(dto)

    fun versionServidor(e: EquipoEntity): EquipoRemotoDto? = try {
        e.versionServidor?.let { gson.fromJson(it, EquipoRemotoDto::class.java) }
    } catch (ex: Exception) {
        null
    }

    fun aPerfil(dto: UsuarioDto, ahora: Long = System.currentTimeMillis()): PerfilUsuario = PerfilUsuario(
        id = dto.idUsuario ?: 0,
        nombre = dto.nombre.orEmpty(),
        apellidos = dto.apellidos.orEmpty(),
        correo = dto.correo.orEmpty(),
        nombreRol = dto.nombreRol.orEmpty(),
        activo = dto.activo ?: true,
        correoVerificado = dto.correoVerificado ?: false,
        ultimoAccesoMs = FechasServidor.aMilis(dto.ultimoAcceso),
        fechaCreacionMs = FechasServidor.aMilis(dto.fechaCreacion),
        descargadoMs = ahora,
    )

    fun aUsuario(dto: UsuarioDto): Usuario = Usuario(
        id = dto.idUsuario ?: 0,
        nombre = listOfNotNull(dto.nombre, dto.apellidos).joinToString(" ").trim().ifBlank { dto.correo.orEmpty() },
        correo = dto.correo.orEmpty(),
        rol = Rol.desdeNombre(dto.nombreRol),
    )
}
