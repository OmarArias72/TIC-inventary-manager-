package com.example.inventory.utils



import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun crearUriParaCamara(context: Context): Uri {
    val file = File(context.cacheDir, "captura_ocr_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}