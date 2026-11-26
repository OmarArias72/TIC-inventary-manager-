package com.example.inventory.domain

import com.example.inventory.domain.model.CampoDestino
import com.example.inventory.domain.model.ObjetivoCaptura
import com.example.inventory.domain.usecase.ClasificadorEtiquetas
import com.example.inventory.domain.usecase.CodigoLeido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos tomados de etiquetas reales: Control de Patrimonio (UAEM) y etiqueta de HP ProDesk. */
class ClasificadorEtiquetasTest {

    private val todos = CampoDestino.entries.toSet()

    @Test
    fun etiquetaPatrimonioUaem_elNumeroEsInventarioYNoSerie() {
        val analisis = ClasificadorEtiquetas.clasificar(
            codigos = listOf(CodigoLeido("0449832", "CODE 39")),
            lineas = listOf("UAEM", "Seoretarýa de Administraoi n", "Control", "de Patrimonio", "Universitario", "0449832", "feb 2016"),
        )
        val numero = analisis.candidatos.first { it.valor == "0449832" }
        assertEquals(CampoDestino.INVENTARIO, numero.sugerencia)
        assertTrue(numero.esCodigoBarras && numero.esTexto)
        assertTrue(analisis.candidatos.none { it.valor == "2016" })

        val seleccion = analisis.seleccionInicial(ObjetivoCaptura.TODOS, todos)
        assertEquals("0449832", seleccion[CampoDestino.INVENTARIO])
        assertNull(seleccion[CampoDestino.SERIE]) // antes el mismo número se ponía también como serie
    }

    @Test
    fun etiquetaHp_serialEsSerieYProductoNoSeSugiere() {
        val analisis = ClasificadorEtiquetas.clasificar(
            codigos = listOf(CodigoLeido("MXL74613JT", "CODE 128"), CodigoLeido("Y5W43AV#146", "CODE 128")),
            lineas = listOf(
                "HP ProDesk 400 G4 SFF Business PC",
                "Serial No. MXL74613JT",
                "Product No. Y5W43AV#146",
                "BU ProDesk 400 G4 SFF PC",
                "OS: Win 10 Pro",
                "BID= 16WWP4DT6**#SABM#DABM",
                "FeatureByte= 3E3Q 3X47 5K6J 6S6b 727B",
                "HP Inc.",
                "MADE IN MEXICO",
                "TPC-P064-SF",
            ),
        )
        val seleccion = analisis.seleccionInicial(ObjetivoCaptura.TODOS, todos)
        assertEquals("MXL74613JT", seleccion[CampoDestino.SERIE])
        assertEquals("HP", seleccion[CampoDestino.MARCA])
        assertEquals("PRODESK 400 G4 SFF", seleccion[CampoDestino.MODELO])
        assertNull(seleccion[CampoDestino.INVENTARIO])

        val producto = analisis.candidatos.first { it.valor == "Y5W43AV#146" }
        assertNull(producto.sugerencia)
        assertTrue(producto.contexto.contains("producto"))
    }

    @Test
    fun objetivoSerie_soloPreseleccionaLaSerie() {
        val analisis = ClasificadorEtiquetas.clasificar(
            codigos = listOf(CodigoLeido("0449832", "CODE 39")),
            lineas = listOf("S/N: CN0ABC1234"),
        )
        val seleccion = analisis.seleccionInicial(ObjetivoCaptura.SERIE, todos)
        assertEquals(mapOf(CampoDestino.SERIE to "CN0ABC1234"), seleccion)
    }

    @Test
    fun serialEnLaLineaSiguiente() {
        val analisis = ClasificadorEtiquetas.clasificar(emptyList(), listOf("Serial No.", "5CG7123ABC"))
        assertEquals("5CG7123ABC", analisis.seleccionInicial(ObjetivoCaptura.SERIE, todos)[CampoDestino.SERIE])
    }

    @Test
    fun campoNoPermitido_noSePreselecciona() {
        val analisis = ClasificadorEtiquetas.clasificar(listOf(CodigoLeido("0449832", "CODE 39")), emptyList())
        val seleccion = analisis.seleccionInicial(ObjetivoCaptura.TODOS, todos - CampoDestino.INVENTARIO)
        assertFalse(CampoDestino.INVENTARIO in seleccion)
    }
}
