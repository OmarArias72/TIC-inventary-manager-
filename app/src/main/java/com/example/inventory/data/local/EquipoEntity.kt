package com.example.inventory.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.inventory.domain.model.EstadoSync

/**
 * Tabla local de equipos (versión 7 de la BD).
 *
 * La clave primaria es el No. de inventario, igual que en el backend, para que un registro
 * local y uno remoto se identifiquen sin ambigüedad. Artículo, marca y modelo se guardan como
 * texto: los catálogos los administra el servidor.
 *
 * Columnas de sincronización:
 * - estado_sync: qué falta hacer con el registro (ver [EstadoSync]).
 * - existe_en_servidor: decide si el cambio se envía como alta (POST) o edición (PUT).
 * - eliminado: borrado lógico; la fila se oculta y se borra de verdad cuando el servidor confirma.
 * - fecha_actualizacion_servidor: `fechaActualizacion` del servidor en la última sincronización.
 *   Si al enviar una edición el servidor tiene otra, alguien más lo modificó → conflicto.
 * - fecha_actualizacion_local: cambia con cada edición local. Sirve para no marcar como
 *   sincronizado un registro que el usuario volvió a editar mientras se enviaba.
 * - version_servidor: JSON con la versión del servidor cuando hay conflicto.
 */
@Entity(
    tableName = "equipos",
    indices = [Index(value = ["estado_sync"])]
)
data class EquipoEntity(
    @PrimaryKey @ColumnInfo(name = "inventario") val inventario: String,
    @ColumnInfo(name = "serie") val serie: String,
    @ColumnInfo(name = "articulo") val articulo: String,
    @ColumnInfo(name = "marca") val marca: String,
    @ColumnInfo(name = "modelo") val modelo: String,
    @ColumnInfo(name = "caracteristicas") val caracteristicas: String,
    @ColumnInfo(name = "estado") val estado: String,
    @ColumnInfo(name = "fecha_registro") val fechaRegistro: Long,
    @ColumnInfo(name = "fecha_actualizacion_local") val fechaActualizacionLocal: Long,
    @ColumnInfo(name = "fecha_actualizacion_servidor") val fechaActualizacionServidor: String?,
    @ColumnInfo(name = "existe_en_servidor") val existeEnServidor: Boolean,
    @ColumnInfo(name = "eliminado") val eliminado: Boolean,
    @ColumnInfo(name = "estado_sync") val estadoSync: EstadoSync,
    @ColumnInfo(name = "intentos") val intentos: Int,
    @ColumnInfo(name = "ultimo_error") val ultimoError: String?,
    @ColumnInfo(name = "version_servidor") val versionServidor: String?,
)

/** Resultado de contar registros por estado de sincronización. */
data class ConteoEstado(
    @ColumnInfo(name = "estado") val estado: EstadoSync,
    @ColumnInfo(name = "total") val total: Int,
)
