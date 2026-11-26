package com.example.inventory.data.sync

/** La sesión no es válida (401): hay que iniciar sesión; no tiene caso reintentar. */
class SinSesionException : Exception()

/** Fallo temporal (5xx, 429, túnel caído): se reintenta más tarde con WorkManager. */
class TransitorioException(mensaje: String) : Exception(mensaje)

/** Resultado del envío de un registro. */
enum class Envio { OK, CONFLICTO, ERROR }

data class ConteoEnvio(val enviados: Int = 0, val conflictos: Int = 0, val errores: Int = 0)
