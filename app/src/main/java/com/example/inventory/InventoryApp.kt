package com.example.inventory

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * @HiltAndroidApp genera el contenedor de dependencias de toda la app.
 * Configuration.Provider hace que WorkManager use la fábrica de Hilt para crear SyncWorker
 * (así el Worker puede recibir SyncManager por inyección).
 */
@HiltAndroidApp
class InventoryApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
