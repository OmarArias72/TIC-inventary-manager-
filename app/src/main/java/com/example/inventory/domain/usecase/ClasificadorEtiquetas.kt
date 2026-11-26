package com.example.inventory.domain.usecase

import com.example.inventory.domain.model.AnalisisImagen
import com.example.inventory.domain.model.CampoDestino
import com.example.inventory.domain.model.CandidatoOcr

/** Código de barras leído por ML Kit. */
data class CodigoLeido(val valor: String, val formato: String)

/**
 * Clasifica lo que se leyó de una etiqueta (códigos de barras + líneas de texto) y decide qué
 * valor parece No. de inventario, No. de serie, marca o modelo.
 *
 * Reglas (por puntaje, se suman):
 * - Texto con etiqueta: "Serial No. MXL7…" → serie; "Inventario: 123" → inventario;
 *   "Model: …" → modelo; "Product No. …" se muestra, pero no se sugiere.
 * - Etiqueta de Control de Patrimonio (UAEM): código de barras numérico y el número impreso
 *   (p. ej. 0449832) → inventario.
 * - Alfanumérico de 8 a 15 caracteres con letras y números → posible serie.
 * - Línea que empieza con una marca conocida ("HP ProDesk 400 G4 SFF") → marca y modelo.
 * Se ignoran años, meses y palabras comunes de las etiquetas.
 *
 * Es código Kotlin puro (sin Android) para poder probarlo con pruebas unitarias.
 */
object ClasificadorEtiquetas {

    private const val MAX_CANDIDATOS = 40

    private val MARCAS = setOf(
        "HP", "DELL", "LENOVO", "ACER", "ASUS", "APPLE", "SAMSUNG", "LG", "EPSON", "BROTHER", "CANON",
        "BENQ", "TOSHIBA", "MICROSOFT", "CISCO", "AOC", "VIEWSONIC", "LOGITECH", "XEROX", "SONY",
        "HUAWEI", "COMPAQ", "GHIA", "VORAGO", "STARTECH", "TP-LINK", "HIKVISION", "LEXMARK", "RICOH",
        "KYOCERA", "OPTOMA", "PANASONIC", "PHILIPS", "INTEL", "GIGABYTE", "MSI", "ZEBRA", "APC",
    )

    private val PALABRAS_GENERICAS_MODELO = setOf("BUSINESS", "PC", "COMPUTER", "DESKTOP", "SERIES")

    private val IGNORAR = setOf(
        "UAEM", "VUAEM", "UAEMEX", "SECRETARIA", "FINANZAS", "ADMINISTRATIVA", "ADMINISTRACION", "CONTROL",
        "PATRIMONIO", "UNIVERSITARIO", "SERIAL", "PRODUCT", "MADE", "MEXICO", "CHINA", "WINDOWS",
        "WARRANTY", "LIMITED", "REGULATORY", "MODEL", "INPUT", "OUTPUT",
        "ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC", "JAN", "APR", "AUG", "DEC",
    )

    // (?i) = sin distinguir mayúsculas. El grupo 1 es el valor.
    private val RE_SERIE = Regex("""(?i)\b(?:serial|serie|s/n|sn)\b\.?(?:\s*(?:no|number|num|n°|#)\b\.?)?\s*[:#=]?\s*([A-Z0-9][A-Z0-9\-]{3,})""")
    private val RE_SERIE_SOLO_ETIQUETA = Regex("""(?i)^\s*(?:serial|serie|s/n|sn|n[uú]mero de serie|no\.? de serie)\b(?:\s*(?:no|number|num|n°|#)\b\.?)?\s*[:#=]?\s*$""")
    private val RE_PRODUCTO = Regex("""(?i)\b(?:product|p/n|pn|part)\b\.?(?:\s*(?:no|number|num|#)\b\.?)?\s*[:#=]?\s*([A-Z0-9][A-Z0-9#\-]{3,})""")
    private val RE_INVENTARIO = Regex("""(?i)\b(?:inventario|inv|activo fijo|no\.? de inventario)\b\.?(?:\s*(?:no|num|n°|#)\b\.?)?\s*[:#=]?\s*([A-Z0-9][A-Z0-9\-]{2,})""")
    private val RE_MODELO = Regex("""(?i)\b(?:model|modelo|mod)\b\.?(?:\s*(?:no|number|#)\b\.?)?\s*[:#=]\s*(.{2,40})""")
    private val RE_TOKEN = Regex("""^[A-Za-z0-9][A-Za-z0-9\-]*$""")
    private val RE_ANIO = Regex("""^(19|20)\d{2}$""")

    /** Acumula puntajes por valor (un mismo valor puede aparecer en código y en texto). */
    private class Acumulado(val valor: String) {
        var codigo = false
        var texto = false
        var contexto: String? = null
        var prioridadContexto = 0
        val puntajes = mutableMapOf<CampoDestino, Int>()

        fun sumar(campo: CampoDestino?, puntos: Int) {
            if (campo != null) puntajes[campo] = (puntajes[campo] ?: 0) + puntos
        }

        fun contexto(texto: String, prioridad: Int) {
            if (prioridad > prioridadContexto) {
                contexto = texto
                prioridadContexto = prioridad
            }
        }
    }

    fun clasificar(codigos: List<CodigoLeido>, lineas: List<String>): AnalisisImagen {
        val mapa = linkedMapOf<String, Acumulado>()
        fun cand(valor: String): Acumulado {
            val clave = valor.trim().uppercase()
            return mapa.getOrPut(clave) { Acumulado(clave) }
        }

        // ---------- Códigos de barras ----------
        codigos.distinctBy { it.valor.trim().uppercase() }.forEach { codigo ->
            val v = codigo.valor.trim()
            if (v.isEmpty() || v.length > 60) return@forEach
            val c = cand(v)
            c.codigo = true
            c.contexto("Código de barras ${codigo.formato}", 1)
            when {
                v.all { it.isDigit() } && v.length in 5..12 -> c.sumar(CampoDestino.INVENTARIO, 70)
                v.contains('#') -> c.sumar(CampoDestino.SERIE, 5) // suele ser No. de producto
                v.any { it.isLetter() } && v.any { it.isDigit() } && v.length in 6..20 -> c.sumar(CampoDestino.SERIE, 45)
            }
        }

        // ---------- Texto ----------
        val limpias = lineas.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        limpias.forEachIndexed { i, linea ->
            RE_SERIE.find(linea)?.groupValues?.get(1)?.takeIf { tieneDigito(it) }?.let {
                cand(it).apply { texto = true; sumar(CampoDestino.SERIE, 100); contexto("Texto: \"$linea\"", 3) }
            }
            if (RE_SERIE_SOLO_ETIQUETA.matches(linea)) {
                // "Serial No." y el valor en la línea siguiente
                limpias.getOrNull(i + 1)?.split(Regex("""\s+"""))?.firstOrNull()
                    ?.trim('.', ',', ';', ':')?.takeIf { RE_TOKEN.matches(it) && tieneDigito(it) && it.length >= 4 }
                    ?.let { cand(it).apply { texto = true; sumar(CampoDestino.SERIE, 90); contexto("Texto después de \"$linea\"", 3) } }
            }
            RE_PRODUCTO.find(linea)?.groupValues?.get(1)?.takeIf { tieneDigito(it) }?.let {
                cand(it).apply { texto = true; sumar(CampoDestino.SERIE, -40); contexto("No. de producto: \"$linea\"", 3) }
            }
            RE_INVENTARIO.find(linea)?.groupValues?.get(1)?.takeIf { tieneDigito(it) }?.let {
                cand(it).apply { texto = true; sumar(CampoDestino.INVENTARIO, 100); contexto("Texto: \"$linea\"", 3) }
            }
            RE_MODELO.find(linea)?.groupValues?.get(1)?.trim()?.takeIf { tieneDigito(it) }?.let {
                cand(it).apply { texto = true; sumar(CampoDestino.MODELO, 90); contexto("Texto: \"$linea\"", 3) }
            }
            marcaYModelo(linea)?.let { (marca, modelo) ->
                cand(marca).apply { texto = true; sumar(CampoDestino.MARCA, 60); contexto("Texto: \"$linea\"", 2) }
                if (modelo != null) {
                    cand(modelo).apply { texto = true; sumar(CampoDestino.MODELO, 55); contexto("Texto: \"$linea\"", 2) }
                }
            }

            // Palabras sueltas que parecen números de inventario o de serie
            val tokens = linea.split(Regex("""[\s,;:=]+""")).map { it.trim('.', '(', ')', '[', ']', '"', '\'') }
            tokens.forEach { t ->
                if (t.length !in 5..20 || !RE_TOKEN.matches(t)) return@forEach
                val mayus = t.uppercase()
                if (mayus in IGNORAR || RE_ANIO.matches(t)) return@forEach
                val soloNumeros = t.all { it.isDigit() }
                val mixto = t.any { it.isLetter() } && t.any { it.isDigit() }
                when {
                    soloNumeros && t.length in 6..8 ->
                        cand(t).apply {
                            texto = true
                            sumar(CampoDestino.INVENTARIO, if (linea == t) 60 else 50)
                            contexto("Texto: \"$linea\"", 1)
                        }
                    soloNumeros -> cand(t).apply { texto = true; sumar(CampoDestino.INVENTARIO, 20); contexto("Texto: \"$linea\"", 1) }
                    mixto && t.length in 8..15 -> cand(t).apply { texto = true; sumar(CampoDestino.SERIE, 25); contexto("Texto: \"$linea\"", 1) }
                    mixto -> cand(t).apply { texto = true; sumar(CampoDestino.SERIE, 10); contexto("Texto: \"$linea\"", 1) }
                }
            }
        }

        val candidatos = mapa.values
            .map { a ->
                CandidatoOcr(
                    valor = a.valor,
                    esCodigoBarras = a.codigo,
                    esTexto = a.texto,
                    contexto = a.contexto ?: "",
                    puntajes = a.puntajes.filterValues { it > 0 },
                )
            }
            .sortedWith(compareByDescending<CandidatoOcr> { it.puntajeMaximo }.thenByDescending { it.esCodigoBarras })
            .take(MAX_CANDIDATOS)
        return AnalisisImagen(candidatos)
    }

    /** "HP ProDesk 400 G4 SFF Business PC" → ("HP", "PRODESK 400 G4 SFF"). El modelo debe tener algún número. */
    private fun marcaYModelo(linea: String): Pair<String, String?>? {
        val palabras = linea.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val primera = palabras.firstOrNull()?.uppercase()?.trim('.', ',') ?: return null
        if (primera !in MARCAS) return null
        val resto = palabras.drop(1).toMutableList()
        while (resto.isNotEmpty() && resto.last().uppercase().trim('.') in PALABRAS_GENERICAS_MODELO) resto.removeAt(resto.lastIndex)
        val modelo = resto.joinToString(" ").takeIf { tieneDigito(it) && it.length in 2..60 }
        return primera to modelo
    }

    private fun tieneDigito(s: String) = s.any { it.isDigit() }
}
