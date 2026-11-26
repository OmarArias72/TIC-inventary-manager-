package com.example.inventory.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.inventory.data.local.EquipoComputoDao
import com.example.inventory.data.local.EquipoDao
import com.example.inventory.data.remote.SesionStore
import com.example.inventory.domain.model.EstadoSincronizacion
import com.example.inventory.domain.model.EstadoSync
import com.example.inventory.domain.model.ResultadoSincronizacion
import com.example.inventory.domain.model.ResumenSincronizacion
import com.example.inventory.domain.model.mensaje
import com.example.inventory.domain.repository.SincronizacionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SincronizacionRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    dao: EquipoDao,
    computoDao: EquipoComputoDao,
    registro: RegistroSincronizacion,
    private val sesion: SesionStore,
) : SincronizacionRepository {

    /** En modo sin servidor no hay a dónde enviar: los cambios quedan pendientes en el dispositivo. */
    private val modoLocal: Boolean get() = sesion.usuario.value?.esLocal == true

    private val workManager = WorkManager.getInstance(context)

    private val conRed = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override val estado: Flow<EstadoSincronizacion> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(TRABAJO_INMEDIATO),
        workManager.getWorkInfosForUniqueWorkFlow(TRABAJO_PERIODICO),
        dao.observarConteos(),
        computoDao.observarConteos(),
        registro.ultima,
    ) { inmediato, periodico, conteos, conteosComputo, ultima ->
        // Equipos generales + equipos de cómputo
        val porEstado = (conteos + conteosComputo).groupBy { it.estado }.mapValues { (_, l) -> l.sumOf { it.total } }
        EstadoSincronizacion(
            sincronizando = (inmediato + periodico).any { it.state == WorkInfo.State.RUNNING },
            pendientes = (porEstado[EstadoSync.PENDIENTE_CREAR] ?: 0) +
                (porEstado[EstadoSync.PENDIENTE_ACTUALIZAR] ?: 0) +
                (porEstado[EstadoSync.PENDIENTE_ELIMINAR] ?: 0),
            conflictos = porEstado[EstadoSync.CONFLICTO] ?: 0,
            errores = porEstado[EstadoSync.ERROR] ?: 0,
            ultima = ultima,
        )
    }

    override fun sincronizarAhora() {
        if (modoLocal) return
        val peticion = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // APPEND_OR_REPLACE: si ya hay una sincronización en curso, esta se ejecuta después
        // (así no se pierden los cambios hechos mientras corría la anterior).
        workManager.enqueueUniqueWork(TRABAJO_INMEDIATO, ExistingWorkPolicy.APPEND_OR_REPLACE, peticion)
    }

    override fun programarPeriodica() {
        if (modoLocal) return
        val peticion = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(conRed)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, peticion)
    }

    override fun detener() {
        workManager.cancelUniqueWork(TRABAJO_INMEDIATO)
        workManager.cancelUniqueWork(TRABAJO_PERIODICO)
    }

    private companion object {
        const val TRABAJO_INMEDIATO = "sincronizar-inventario"
        const val TRABAJO_PERIODICO = "sincronizar-inventario-periodico"
    }
}

/** Guarda el resultado de la última sincronización para mostrarlo en pantalla. */
@Singleton
class RegistroSincronizacion @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("sincronizacion", Context.MODE_PRIVATE)
    private val _ultima = MutableStateFlow(leer())
    val ultima: StateFlow<ResumenSincronizacion?> = _ultima.asStateFlow()

    fun guardar(resultado: ResultadoSincronizacion) {
        val resumen = ResumenSincronizacion(
            fechaMs = System.currentTimeMillis(),
            exitosa = resultado is ResultadoSincronizacion.Completada,
            mensaje = resultado.mensaje(),
        )
        prefs.edit()
            .putLong("fecha", resumen.fechaMs)
            .putBoolean("exitosa", resumen.exitosa)
            .putString("mensaje", resumen.mensaje)
            .apply()
        _ultima.value = resumen
    }

    private fun leer(): ResumenSincronizacion? {
        val fecha = prefs.getLong("fecha", 0L)
        if (fecha == 0L) return null
        return ResumenSincronizacion(fecha, prefs.getBoolean("exitosa", false), prefs.getString("mensaje", "").orEmpty())
    }
}
