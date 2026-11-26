package com.example.inventory.data.services

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.inventory.domain.model.Equipo
import com.example.inventory.domain.model.EstadoSync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Genera el .xlsx del inventario. Escribir el archivo es lento, por eso todo se hace en
 * Dispatchers.IO (antes se hacía en el hilo principal y podía congelar la pantalla).
 */
@Singleton
class ExcelService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun crearWorkbook(lista: List<Equipo>): XSSFWorkbook {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Inventario de Equipos")

        val encabezados = listOf(
            "No. Inventario", "No. Serie", "Artículo", "Marca", "Modelo",
            "Observaciones", "Estado", "Fecha de Registro", "Sincronizado"
        )
        val headerRow = sheet.createRow(0)
        encabezados.forEachIndexed { index, title -> headerRow.createCell(index).setCellValue(title) }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        lista.forEachIndexed { rowIndex, item ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(item.inventario)
            row.createCell(1).setCellValue(item.serie)
            row.createCell(2).setCellValue(item.articulo)
            row.createCell(3).setCellValue(item.marca)
            row.createCell(4).setCellValue(item.modelo)
            row.createCell(5).setCellValue(item.caracteristicas)
            row.createCell(6).setCellValue(item.estado.name)
            row.createCell(7).setCellValue(if (item.fechaRegistro > 0) dateFormat.format(Date(item.fechaRegistro)) else "")
            row.createCell(8).setCellValue(if (item.estadoSync == EstadoSync.SINCRONIZADO) "Sí" else "No")
        }

        for (col in encabezados.indices) {
            sheet.setColumnWidth(col, 15 * 256)
        }
        return workbook
    }

    /** Crea el archivo en caché y devuelve su Uri para compartirlo con otra app. */
    suspend fun crearArchivoParaCompartir(lista: List<Equipo>): Uri = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "Inventario_Equipos.xlsx")
        crearWorkbook(lista).use { workbook ->
            FileOutputStream(file).use { workbook.write(it) }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    /** Guarda el archivo en la ubicación que eligió el usuario. */
    suspend fun guardarEnUri(lista: List<Equipo>, uri: Uri) = withContext(Dispatchers.IO) {
        crearWorkbook(lista).use { workbook ->
            context.contentResolver.openOutputStream(uri)?.use { workbook.write(it) }
                ?: throw IllegalStateException("No se pudo abrir el archivo de destino.")
        }
    }
}
