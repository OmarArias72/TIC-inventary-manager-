package com.example.inventory.domain

import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.DatosEquipo
import com.example.inventory.domain.model.EstadoEquipo
import com.example.inventory.domain.usecase.ComparadorVersiones
import com.example.inventory.domain.usecase.Segmento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparadorVersionesTest {

    private val base = DatosEquipo("INV-1", "S1", "CPU", "HP", "PRODESK", "Sin teclado", EstadoEquipo.ALTA)

    @Test
    fun soloMarcaComoDistintoLoQueRealmenteCambia() {
        val servidor = base.copy(marca = "hp ", estado = EstadoEquipo.MANTENIMIENTO)
        val filas = ComparadorVersiones.equipo(base, servidor).associateBy { it.campo }

        assertFalse(filas.getValue("Marca").distinto)      // misma marca con otra escritura
        assertTrue(filas.getValue("Estado").distinto)
        assertEquals(1, filas.values.count { it.distinto })
    }

    @Test
    fun computo_comparaCadaCampoTecnicoYLaSala() {
        val local = DatosComputo(base = base, idSala = 3, ram = "8 GB", estadoMouse = "Bueno")
        val servidor = local.copy(idSala = 4, ram = "16 GB", estadoMouse = "bueno")
        val salas = mapOf(3 to "LAB-1", 4 to "LAB-2")

        val filas = ComparadorVersiones.computo(local, servidor) { salas[it] ?: "—" }.associateBy { it.campo }

        assertTrue(filas.getValue("RAM").distinto)
        assertTrue(filas.getValue("Sala").distinto)
        assertEquals("LAB-1", filas.getValue("Sala").local)
        assertFalse(filas.getValue("Mouse").distinto) // el catálogo no distingue mayúsculas
    }

    @Test
    fun marcarPalabras_resaltaSoloLasPalabrasNuevas() {
        val segmentos = ComparadorVersiones.marcarPalabras("Office 2019 y AutoCAD", "Office 2016 y autocad")
        val resaltadas = segmentos.filter(Segmento::resaltado).map(Segmento::texto)
        assertEquals(listOf("2019"), resaltadas)
        assertEquals("Office 2019 y AutoCAD", segmentos.joinToString("") { it.texto })
    }
}
