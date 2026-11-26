package com.example.inventory.domain.usecase

import com.example.inventory.domain.model.DatosComputo
import com.example.inventory.domain.model.DatosEquipo

/**
 * Un campo comparado entre la versión del dispositivo y la del servidor.
 * Los valores se muestran tal cual; [distinto] se calcula con los valores normalizados
 * (mayúsculas, espacios), con el mismo criterio que usa la sincronización para decidir
 * si hay conflicto. Así "hp" y "HP" no se marcan como diferentes.
 */
data class DiferenciaCampo(
    val campo: String,
    val local: String,
    val servidor: String,
    val distinto: Boolean,
)

/** Segmento de texto para resaltar palabra por palabra. */
data class Segmento(val texto: String, val resaltado: Boolean)

object ComparadorVersiones {

    fun equipo(local: DatosEquipo, servidor: DatosEquipo): List<DiferenciaCampo> {
        val nl = NormalizadorEquipo.normalizar(local)
        val ns = NormalizadorEquipo.normalizar(servidor)
        return listOf(
            fila("No. Serie", local.serie, servidor.serie, nl.serie, ns.serie),
            fila("Artículo", local.articulo, servidor.articulo, nl.articulo, ns.articulo),
            fila("Marca", local.marca, servidor.marca, nl.marca, ns.marca),
            fila("Modelo", local.modelo, servidor.modelo, nl.modelo, ns.modelo),
            fila("Estado", local.estado.name, servidor.estado.name, nl.estado.name, ns.estado.name),
            fila("Observaciones", local.caracteristicas, servidor.caracteristicas, nl.caracteristicas, ns.caracteristicas),
        )
    }

    /** @param nombreSala convierte el id de la sala en un texto legible (código y nombre). */
    fun computo(local: DatosComputo, servidor: DatosComputo, nombreSala: (Int?) -> String): List<DiferenciaCampo> {
        val nl = NormalizadorComputo.normalizar(local)
        val ns = NormalizadorComputo.normalizar(servidor)
        return equipo(local.base, servidor.base) + listOf(
            fila("Sala", nombreSala(local.idSala), nombreSala(servidor.idSala), nl.idSala.toString(), ns.idSala.toString()),
            fila("Procesador", local.procesador, servidor.procesador, nl.procesador, ns.procesador),
            fila("RAM", local.ram, servidor.ram, nl.ram, ns.ram),
            fila("Almacenamiento", local.almacenamiento, servidor.almacenamiento, nl.almacenamiento, ns.almacenamiento),
            fila("Sistema operativo", local.sistemaOperativo, servidor.sistemaOperativo, nl.sistemaOperativo, ns.sistemaOperativo),
            fila("Especificaciones", local.especificacionesHardware, servidor.especificacionesHardware, nl.especificacionesHardware, ns.especificacionesHardware),
            fila("Software instalado", local.softwareInstalado, servidor.softwareInstalado, nl.softwareInstalado, ns.softwareInstalado),
            fila("Estado de la computadora", local.estadoComputadora, servidor.estadoComputadora, nl.estadoComputadora, ns.estadoComputadora),
            fila("CPU", local.estadoCpu, servidor.estadoCpu, nl.estadoCpu, ns.estadoCpu),
            fila("Monitor", local.estadoMonitor, servidor.estadoMonitor, nl.estadoMonitor, ns.estadoMonitor),
            fila("Teclado", local.estadoTeclado, servidor.estadoTeclado, nl.estadoTeclado, ns.estadoTeclado),
            fila("Mouse", local.estadoMouse, servidor.estadoMouse, nl.estadoMouse, ns.estadoMouse),
            fila("Cable Ethernet", local.estadoCableEthernet, servidor.estadoCableEthernet, nl.estadoCableEthernet, ns.estadoCableEthernet),
            fila("Observaciones técnicas", local.observaciones, servidor.observaciones, nl.observaciones, ns.observaciones),
        )
    }

    private fun fila(campo: String, local: String, servidor: String, normalLocal: String, normalServidor: String) =
        DiferenciaCampo(campo, local, servidor, distinto = normalLocal != normalServidor)

    /**
     * Divide [texto] en palabras y espacios, y marca las palabras que no aparecen en [otro]
     * (sin distinguir mayúsculas; cada palabra de [otro] solo "empareja" una vez).
     * Sirve para resaltar qué cambió dentro de textos largos como observaciones o software.
     */
    fun marcarPalabras(texto: String, otro: String): List<Segmento> {
        val disponibles = mutableMapOf<String, Int>()
        Regex("""\S+""").findAll(otro).forEach { m ->
            val clave = m.value.lowercase()
            disponibles[clave] = (disponibles[clave] ?: 0) + 1
        }
        return Regex("""\S+|\s+""").findAll(texto).map { m ->
            val parte = m.value
            if (parte.isBlank()) {
                Segmento(parte, false)
            } else {
                val clave = parte.lowercase()
                val quedan = disponibles[clave] ?: 0
                if (quedan > 0) {
                    disponibles[clave] = quedan - 1
                    Segmento(parte, false)
                } else {
                    Segmento(parte, true)
                }
            }
        }.toList()
    }
}
