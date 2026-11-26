package com.example.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.inventory.ui.AppRaiz
import com.example.inventory.ui.theme.InventarioTheme
import dagger.hilt.android.AndroidEntryPoint

/** @AndroidEntryPoint permite que los ViewModels de esta Activity reciban dependencias de Hilt. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InventarioTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRaiz()
                }
            }
        }
    }
}
