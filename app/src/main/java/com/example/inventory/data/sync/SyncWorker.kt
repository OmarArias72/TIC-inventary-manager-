package com.example.inventory.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.inventory.domain.model.ResultadoSincronizacion
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Trabajo de WorkManager. WorkManager lo ejecuta solo cuando hay red (restricción CONNECTED),
 * sobrevive a que se cierre la app o se reinicie el teléfono, y reintenta con espera exponencial.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val registro: RegistroSincronizacion,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val resultado = syncManager.sincronizar()
        registro.guardar(resultado)
        return when (resultado) {
            is ResultadoSincronizacion.Completada -> Result.success()
            is ResultadoSincronizacion.FalloTemporal ->
                if (runAttemptCount < MAX_REINTENTOS) Result.retry() else Result.failure()
            // Sin sesión o sin URL no tiene caso reintentar: se vuelve a programar al iniciar sesión.
            ResultadoSincronizacion.SinSesion, ResultadoSincronizacion.SinServidor -> Result.failure()
        }
    }

    private companion object {
        const val MAX_REINTENTOS = 8
    }
}
