package com.example.inventory.domain.model

/** Campo del formulario al que se puede asignar un valor leído de la etiqueta. */
enum class CampoDestino(val etiqueta: String) {
    INVENTARIO("No. Inventario"),
    SERIE("No. Serie"),
    MARCA("Marca"),
    MODELO("Modelo"),
}

/** Qué quiere capturar el usuario al tomar la foto (decide qué valores se preseleccionan). */
enum class ObjetivoCaptura(val etiqueta: String) {
    INVENTARIO("No. Inventario"),
    SERIE("No. Serie"),
    TODOS("Todos los campos"),
}

/**
 * Un valor encontrado en la imagen.
 *
 * @param contexto de dónde salió (p. ej. "Serial No." o "Código de barras CODE_128").
 * @param puntajes qué tan probable es que sea cada campo (más alto = más probable).
 */
data class CandidatoOcr(
    val valor: String,
    val esCodigoBarras: Boolean,
    val esTexto: Boolean,
    val contexto: String,
    val puntajes: Map<CampoDestino, Int>,
) {
    /** Campo más probable, o null si no parece ninguno. */
    val sugerencia: CampoDestino?
        get() = puntajes.filterValues { it >= UMBRAL_SUGERENCIA }.maxByOrNull { it.value }?.key

    val puntajeMaximo: Int get() = puntajes.values.maxOrNull() ?: 0

    companion object {
        const val UMBRAL_SUGERENCIA = 30
    }
}

/** Resultado de analizar una foto: todos los valores encontrados, ordenados por relevancia. */
data class AnalisisImagen(
    val candidatos: List<CandidatoOcr>,
) {
    /**
     * Asignación inicial que se propone al usuario: el mejor candidato para cada campo
     * pedido, sin repetir un mismo valor en dos campos.
     */
    fun seleccionInicial(objetivo: ObjetivoCaptura, permitidos: Set<CampoDestino>): Map<CampoDestino, String> {
        val pedidos = when (objetivo) {
            ObjetivoCaptura.INVENTARIO -> setOf(CampoDestino.INVENTARIO)
            ObjetivoCaptura.SERIE -> setOf(CampoDestino.SERIE)
            ObjetivoCaptura.TODOS -> CampoDestino.entries.toSet()
        } intersect permitidos

        val opciones = candidatos.flatMap { c ->
            c.puntajes.filter { (campo, puntaje) -> campo in pedidos && puntaje >= CandidatoOcr.UMBRAL_SUGERENCIA }
                .map { (campo, puntaje) -> Triple(c.valor, campo, puntaje) }
        }.sortedByDescending { it.third }

        val asignados = linkedMapOf<CampoDestino, String>()
        val usados = mutableSetOf<String>()
        for ((valor, campo, _) in opciones) {
            if (campo !in asignados && valor !in usados) {
                asignados[campo] = valor
                usados += valor
            }
        }
        return asignados
    }
}
