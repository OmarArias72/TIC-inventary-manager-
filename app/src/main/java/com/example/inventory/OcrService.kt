package com.example.inventory

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrService {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extraerTexto(context: Context, imageUri: Uri): String {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val result = textRecognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun extraerDatosDeSerie(texto: String): Map<String, String> {
        val lineas = texto.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

        var codigoBarras = ""
        var numeroSerie = ""

        val barcodeRegex = Regex("^\\d{8,14}$")
        val serialRegex = Regex("(?:S/N|SN|SERIAL|SERIE)[:\\s\\-]*([A-Z0-9]{5,20})", RegexOption.IGNORE_CASE)
        val alfanumericoPuroRegex = Regex("^[A-Z0-9]{6,20}$", RegexOption.IGNORE_CASE)

        for (linea in lineas) {
            val lineaLimpia = linea.replace(" ", "")

            if (codigoBarras.isEmpty() && barcodeRegex.matches(lineaLimpia)) {
                codigoBarras = lineaLimpia
                continue
            }

            val matchSerial = serialRegex.find(linea)
            if (matchSerial != null) {
                numeroSerie = matchSerial.groupValues[1]
                continue
            }

            if (numeroSerie.isEmpty() && alfanumericoPuroRegex.matches(lineaLimpia)) {
                if (lineaLimpia != codigoBarras) {
                    numeroSerie = lineaLimpia
                }
            }
        }

        return mapOf(
            "codigo_barras" to codigoBarras,
            "numero_serie" to numeroSerie.uppercase()
        )
    }
}