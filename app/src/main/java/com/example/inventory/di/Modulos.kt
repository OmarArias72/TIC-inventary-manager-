package com.example.inventory.di

import android.content.Context
import androidx.room.Room
import com.example.inventory.BuildConfig
import com.example.inventory.data.local.AppDatabase
import com.example.inventory.data.local.EjecutorTransacciones
import com.example.inventory.data.local.EjecutorTransaccionesRoom
import com.example.inventory.data.local.EquipoComputoDao
import com.example.inventory.data.local.EquipoDao
import com.example.inventory.data.local.SalaDao
import com.example.inventory.data.local.Migraciones
import com.example.inventory.data.remote.AuthApi
import com.example.inventory.data.remote.BaseUrlInterceptor
import com.example.inventory.data.remote.ComputoApi
import com.example.inventory.data.remote.InventarioApi
import com.example.inventory.data.remote.SesionCookieJar
import com.example.inventory.data.remote.SinAutenticador
import com.example.inventory.data.remote.TokenAuthenticator
import com.example.inventory.data.repository.AuthRepositoryImpl
import com.example.inventory.data.repository.EquipoComputoRepositoryImpl
import com.example.inventory.data.repository.EquipoRepositoryImpl
import com.example.inventory.data.repository.ServidorRepositoryImpl
import com.example.inventory.data.sync.ConectividadMonitorImpl
import com.example.inventory.data.sync.SincronizacionRepositoryImpl
import com.example.inventory.data.sync.SincronizadorComputo
import com.example.inventory.data.sync.SincronizadorComputoImpl
import com.example.inventory.domain.repository.AuthRepository
import com.example.inventory.domain.repository.ConectividadMonitor
import com.example.inventory.domain.repository.EquipoComputoRepository
import com.example.inventory.domain.repository.EquipoRepository
import com.example.inventory.domain.repository.ServidorRepository
import com.example.inventory.domain.repository.SincronizacionRepository
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/*
 * Módulos de Hilt: le dicen a Hilt cómo crear los objetos que no se pueden construir con
 * @Inject constructor (Room, Retrofit, OkHttp) y qué implementación usar para cada interfaz.
 * SingletonComponent = viven mientras viva la app (una sola instancia).
 */

@Module
@InstallIn(SingletonComponent::class)
object BaseDatosModule {

    @Provides
    @Singleton
    fun baseDatos(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NOMBRE)
            .addMigrations(Migraciones.MIGRACION_6_7, Migraciones.MIGRACION_7_8)
            .build()

    @Provides
    fun equipoDao(db: AppDatabase): EquipoDao = db.equipoDao()

    @Provides
    fun salaDao(db: AppDatabase): SalaDao = db.salaDao()

    @Provides
    fun equipoComputoDao(db: AppDatabase): EquipoComputoDao = db.equipoComputoDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RedModule {

    @Provides
    @Singleton
    fun gson(): Gson = Gson()

    @Provides
    @Singleton
    fun logging(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }

    /** Cliente base: URL configurable, cookies de sesión y tiempos de espera. */
    @Provides
    @Singleton
    @SinAutenticador
    fun clienteSinAutenticador(
        baseUrl: BaseUrlInterceptor,
        cookies: SesionCookieJar,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(baseUrl)
        .addInterceptor(logging)
        .cookieJar(cookies)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Cliente principal: el base + renovación automática de la sesión al recibir 401. */
    @Provides
    @Singleton
    fun cliente(
        @SinAutenticador base: OkHttpClient,
        autenticador: TokenAuthenticator,
    ): OkHttpClient = base.newBuilder().authenticator(autenticador).build()

    @Provides
    @Singleton
    fun retrofit(cliente: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/") // se reemplaza en cada petición por BaseUrlInterceptor
        .client(cliente)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun inventarioApi(retrofit: Retrofit): InventarioApi = retrofit.create(InventarioApi::class.java)

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun computoApi(retrofit: Retrofit): ComputoApi = retrofit.create(ComputoApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositorioModule {

    @Binds
    @Singleton
    abstract fun equipoRepository(impl: EquipoRepositoryImpl): EquipoRepository

    @Binds
    @Singleton
    abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun servidorRepository(impl: ServidorRepositoryImpl): ServidorRepository

    @Binds
    @Singleton
    abstract fun sincronizacionRepository(impl: SincronizacionRepositoryImpl): SincronizacionRepository

    @Binds
    @Singleton
    abstract fun conectividad(impl: ConectividadMonitorImpl): ConectividadMonitor

    @Binds
    abstract fun ejecutorTransacciones(impl: EjecutorTransaccionesRoom): EjecutorTransacciones

    @Binds
    @Singleton
    abstract fun equipoComputoRepository(impl: EquipoComputoRepositoryImpl): EquipoComputoRepository

    @Binds
    abstract fun sincronizadorComputo(impl: SincronizadorComputoImpl): SincronizadorComputo
}
