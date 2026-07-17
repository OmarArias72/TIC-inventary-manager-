package com.example.inventory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventario")
data class ItemInventario(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var noInventario: String = "",
    var noSerie: String = "",
    var articulo: String = "",
    var marca: String = "",
    var modelo: String = "",
    var ubicacion: String = "",
    var observacion: String = ""
)