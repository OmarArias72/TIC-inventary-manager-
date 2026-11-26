package com.example.inventory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta institucional UAEMéx (la misma de la plataforma web Control TIC)
val VerdeInstitucional = Color(0xFF164E33)
val VerdeClaroInstitucional = Color(0xFFB7EBCB)
val VerdeOscuroInstitucional = Color(0xFF002112)
val DoradoInstitucional = Color(0xFFD4A017)

// Tonos de apoyo derivados de la paleta (familias verde, dorada y neutra)
private val DoradoClaro = Color(0xFFF6E3A6)
private val DoradoOscuro = Color(0xFF3A2C00)
private val Ambar = Color(0xFF8A5A00)          // estado MANTENIMIENTO
private val FondoNeutro = Color(0xFFF5F8F6)
private val SuperficieVariante = Color(0xFFDCE8E0)
private val TextoNeutro = Color(0xFF1A1C1A)
private val TextoVariante = Color(0xFF404943)

private val TemaInventario = lightColorScheme(
    primary = VerdeInstitucional,
    onPrimary = Color.White,
    primaryContainer = VerdeClaroInstitucional,
    onPrimaryContainer = VerdeOscuroInstitucional,
    secondary = DoradoInstitucional,
    onSecondary = VerdeOscuroInstitucional,
    secondaryContainer = DoradoClaro,
    onSecondaryContainer = DoradoOscuro,
    tertiary = Ambar,
    onTertiary = Color.White,
    background = FondoNeutro,
    onBackground = TextoNeutro,
    surface = FondoNeutro,
    onSurface = TextoNeutro,
    surfaceVariant = SuperficieVariante,
    onSurfaceVariant = TextoVariante,
    outline = Color(0xFF707971),
)

@Composable
fun InventarioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TemaInventario, content = content)
}
