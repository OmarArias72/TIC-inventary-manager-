package com.example.inventory

import androidx.room.*

@Dao
interface InventarioDao {
    @Query("SELECT * FROM inventario")
    suspend fun obtenerTodos(): List<ItemInventario>

    @Query("SELECT COUNT(*) FROM inventario WHERE LOWER(noSerie) = LOWER(:noSerie)")
    suspend fun existeNumeroSerie(noSerie: String): Int

    @Query("SELECT COUNT(*) FROM inventario WHERE LOWER(noSerie) = LOWER(:noSerie) AND id != :idAExcluir")
    suspend fun existeNumeroSerie(noSerie: String, idAExcluir: Int): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(item: ItemInventario): Long

    @Update
    suspend fun actualizar(item: ItemInventario): Int

    @Query("DELETE FROM inventario WHERE id = :id")
    suspend fun eliminar(id: Int)
}

// version = 2 porque marcaModelo se dividió en marca + modelo (cambio de esquema)
// exportSchema = false: no necesitamos guardar el historial de esquemas en JSON para este proyecto
@Database(entities = [ItemInventario::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun inventarioDao(): InventarioDao
}

// Repositorio equivalente al DatabaseService original
class DatabaseRepository(private val dao: InventarioDao) {
    suspend fun obtenerTodos() = dao.obtenerTodos()

    suspend fun insertarItem(item: ItemInventario): Long {
        if (dao.existeNumeroSerie(item.noSerie.trim()) > 0) return -1
        return dao.insertar(item.apply { noSerie = noSerie.trim() })
    }

    suspend fun actualizarItem(item: ItemInventario): Int {
        if (dao.existeNumeroSerie(item.noSerie.trim(), item.id) > 0) return -1
        return dao.actualizar(item.apply { noSerie = noSerie.trim() })
    }

    suspend fun eliminarItem(id: Int) = dao.eliminar(id)
}