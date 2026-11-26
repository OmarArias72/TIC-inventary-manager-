package com.example.inventory.domain.repository

import com.example.inventory.domain.model.EstadoSincronizacion
import kotlinx.coroutines.flow.Flow

/** Programación y estado de la sincronización (la implementación usa WorkManager). */
interface SincronizacionRepository {
    val estado: Flow<EstadoSincronizacion>

    /** Pide sincronizar en cuanto haya conexión. */
    fun sincronizarAhora()

    /** Sincronización periódica en segundo plano. */
    fun programarPeriodica()

    /** Cancela la sincronización (al cerrar sesión). */
    fun detener()
}

/** Indica si el dispositivo tiene red. Tener red no garantiza que el backend esté disponible. */
interface ConectividadMonitor {
    val hayRed: Flow<Boolean>
}
