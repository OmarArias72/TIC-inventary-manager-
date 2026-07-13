package com.example.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Construir la base de datos Room
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "inventario.db"
        )
            // Recrea la tabla si cambia el esquema (aceptable mientras se desarrolla;
            // en producción se reemplazaría por una Migration real que preserve los datos).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        val repository = DatabaseRepository(db.inventarioDao())

        // 2. Factory manual para inyectar el repositorio en el ViewModel
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return InventarioViewModel(repository) as T
            }
        }

        setContent {
            MaterialTheme {
                Surface {
                    val viewModel: InventarioViewModel = viewModel(factory = viewModelFactory)
                    InventarioScreen(viewModel)
                }
            }
        }
    }
}