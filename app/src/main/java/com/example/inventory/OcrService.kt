package com.example.inventory

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrService {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Configuramos el escáner para leer todos los formatos estándar de códigos de barras
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    suspend fun procesarImagen(context: Context, imageUri: Uri): Map<String, String> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)

            // 1. Escanear Códigos de Barras
            val barcodes = barcodeScanner.process(image).await()
            var valorBarcode = ""
            if (barcodes.isNotEmpty()) {
                valorBarcode = barcodes.first().rawValue ?: ""
            }

            // 2. Extraer Texto con OCR
            val textResult = textRecognizer.process(image).await()
            val textoOcr = textResult.text

            // 3. Analizar y combinar resultados
            analizarDatos(textoOcr, valorBarcode)
        } catch (e: Exception) {
            e.printStackTrace()
            mapOf("codigo_barras" to "", "numero_serie" to "")
        }
    }

    private fun analizarDatos(textoOcr: String, barcodeDetectado: String): Map<String, String> {
        val lineas = textoOcr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        var numeroFinal = barcodeDetectado

        // Palabras "basura" comunes en las etiquetas que no queremos capturar
        val ignorar = listOf("UAEM", "SECRETARIA", "FINANZAS", "CONTROL", "PATRIMONIO", "UNIVERSITARIO", "SERIAL", "PRODUCT", "HP", "NO")

        // Si el código de barras falló, buscamos con OCR la cadena alfanumérica/numérica
        if (numeroFinal.isEmpty()) {
            val posiblesSeries = mutableListOf<String>()

            for (linea in lineas) {
                // Separamos por espacios o guiones
                val partes = linea.split(" ", "-", ":").map { it.trim() }
                for (parte in partes) {
                    // Expresión regular: Solo letras y números (sin límite de tamaño, mínimo 4 para evitar ruido)
                    if (parte.length >= 4 && parte.matches(Regex("^[A-Za-z0-9]+$"))) {
                        // Verificamos que no sea una palabra de la lista de ignorados
                        if (!ignorar.any { it.equals(parte, ignoreCase = true) }) {
                            posiblesSeries.add(parte.uppercase())
                        }
                    }
                }
            }

            if (posiblesSeries.isNotEmpty()) {
                // Tomamos el primer valor que cumpla el criterio (suele ser el más prominente)
                numeroFinal = posiblesSeries.first()
            }
        }

        // Asignamos el mismo valor encontrado a ambos campos, ya que en la etiqueta son el mismo
        return mapOf(
            "codigo_barras" to numeroFinal,
            "numero_serie" to numeroFinal
        )
    }
}