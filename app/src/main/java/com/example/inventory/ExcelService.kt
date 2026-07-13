package com.example.inventory

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class ExcelService(private val context: Context) {

    fun exportarInventario(lista: List<ItemInventario>) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Inventario de Equipos")

        // 1. Crear Encabezados
        val encabezados = listOf(
            "ID", "No. Inventario", "No. Serie", "Marca", "Modelo", "Ubicación", "Observación"
        )
        val headerRow = sheet.createRow(0)
        encabezados.forEachIndexed { index, title ->
            headerRow.createCell(index).setCellValue(title)
        }

        // 2. Llenar Datos
        lista.forEachIndexed { rowIndex, item ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(item.id.toDouble())
            row.createCell(1).setCellValue(item.noInventario)
            row.createCell(2).setCellValue(item.noSerie)
            row.createCell(3).setCellValue(item.marca)
            row.createCell(4).setCellValue(item.modelo)
            row.createCell(5).setCellValue(item.ubicacion)
            row.createCell(6).setCellValue(item.observacion)
        }

        // 3. Ajustar ancho de columnas con un valor fijo para evitar el crash de AWT en Android
        for (col in encabezados.indices) {
            sheet.setColumnWidth(col, 15 * 256) // 15 caracteres de ancho aproximadamente
        }

        // 4. Guardar archivo temporalmente
        val file = File(context.cacheDir, "Inventario_Equipos.xlsx")
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        // 5. Compartir archivo usando Intent nativo
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Te comparto el reporte del inventario generado desde la aplicación.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartir Inventario"))
    }
}