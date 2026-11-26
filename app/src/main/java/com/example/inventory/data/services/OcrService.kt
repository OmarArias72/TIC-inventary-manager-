package com.example.inventory.data.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.example.inventory.domain.model.AnalisisImagen
import com.example.inventory.domain.usecase.ClasificadorEtiquetas
import com.example.inventory.domain.usecase.CodigoLeido
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analiza la foto de una etiqueta con ML Kit y devuelve TODOS los valores encontrados,
 * clasificados por [ClasificadorEtiquetas].
 *
 * Mejoras respecto a la versión anterior:
 * - Lee todos los códigos de barras (antes solo el primero, y lo usaba como inventario Y como serie).
 * - Lee el texto también girado 90° y 270°: las etiquetas de HP/Dell suelen estar en vertical.
 * - Corrige la orientación de la foto (EXIF) y la reduce a un tamaño razonable antes de analizarla.
 */
@Singleton
class OcrService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    )

    suspend fun analizarImagen(uri: Uri): AnalisisImagen {
        val bitmap = try {
            withContext(Dispatchers.IO) { cargarBitmap(uri) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return AnalisisImagen(emptyList())

        val codigos = mutableListOf<CodigoLeido>()
        val lineasPorGiro = mutableListOf<List<String>>()

        for (giro in GIROS) {
            val imagen = InputImage.fromBitmap(bitmap, giro)
            if (giro != 270) { // los códigos de barras se leen bien con 0° y 90°
                try {
                    barcodeScanner.process(imagen).await().forEach { b ->
                        val valor = b.rawValue?.trim().orEmpty()
                        if (valor.isNotEmpty()) codigos += CodigoLeido(valor, nombreFormato(b.format))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            try {
                val texto = textRecognizer.process(imagen).await()
                lineasPorGiro += texto.textBlocks.flatMap { bloque -> bloque.lines.map { it.text } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                lineasPorGiro += emptyList<String>()
            }
        }

        // Un giro equivocado produce poco texto (y ruido). Se usan solo los giros que
        // leyeron al menos el 40% del texto del mejor giro.
        val maximo = lineasPorGiro.maxOfOrNull { lineas -> lineas.sumOf { it.length } } ?: 0
        val lineas = lineasPorGiro
            .filter { lineas -> maximo > 0 && lineas.sumOf { it.length } >= maximo * 0.4 }
            .flatten()

        return ClasificadorEtiquetas.clasificar(codigos, lineas)
    }

    /** Carga la imagen reducida (lado mayor ≤ 2560 px) y girada según su EXIF. */
    private fun cargarBitmap(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        val ladoMayor = maxOf(limites.outWidth, limites.outHeight)
        if (ladoMayor <= 0) return null
        var muestreo = 1
        while (ladoMayor / muestreo > LADO_MAXIMO) muestreo *= 2

        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestreo })
        } ?: return null

        val orientacion = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val grados = when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (grados == 0f) return bitmap
        val matriz = Matrix().apply { postRotate(grados) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
    }

    private fun nombreFormato(formato: Int): String = when (formato) {
        Barcode.FORMAT_CODE_128 -> "CODE 128"
        Barcode.FORMAT_CODE_39 -> "CODE 39"
        Barcode.FORMAT_CODE_93 -> "CODE 93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_QR_CODE -> "QR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA MATRIX"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> ""
    }

    private companion object {
        val GIROS = listOf(0, 90, 270)
        const val LADO_MAXIMO = 2560
    }
}
