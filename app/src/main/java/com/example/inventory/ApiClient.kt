package com.example.inventory



import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.Interceptor


// 1. Clases de Datos (DTOs) adaptadas a Spring Boot
data class BusquedaRequest(val termino: String) // Cambiado a 'termino' para coincidir con Spring Boot

data class ArticuloDTO(val idArticulo: Int?, val nombreArticulo: String?)
data class MarcaDTO(val idMarca: Int?, val nombreMarca: String?)
data class ModeloDTO(val idModelo: Int?, val nombreModelo: String?, val marca: MarcaDTO?)

// Mapea la entidad Equipo de Spring Boot
data class EquipoDTO(
    val inventario: String?, // Ahora es String (alfanumérico)
    val serie: String?,
    val articulo: ArticuloDTO?,
    val modelo: ModeloDTO?
)

// DTO para el endpoint de creación
data class EquipoMovilCreateDTO(
    val noInventario: String,
    val noSerie: String,
    val marca: String,
    val modelo: String,
    val ubicacion: String,
    val observacion: String
)

// DTO Auxiliar para la Interfaz de Usuario
data class Sugerencia(
    val inventario: String?, // Cambiado a String
    val serie: String?,
    val articulo: String?,
    val marca: String?,
    val modelo: String?
)

// 2. Interfaz de la API
interface InventoryApiService {
    @POST("/api/inventory/buscar-por-serie")
    suspend fun buscarPorSerie(@Body request: BusquedaRequest): Response<List<EquipoDTO>>

    @POST("/api/inventory/buscar-por-marca")
    suspend fun buscarPorMarca(@Body request: BusquedaRequest): Response<List<EquipoDTO>>

    @POST("/api/inventory/buscar-por-modelo")
    suspend fun buscarPorModelo(@Body request: BusquedaRequest): Response<List<EquipoDTO>>

    @POST("/api/inventory/buscar-por-articulo")
    suspend fun buscarPorArticulo(@Body request: BusquedaRequest): Response<List<EquipoDTO>>

    @POST("/api/inventory/crear-equipo-movil")
    suspend fun crearEquipoMovil(@Body request: EquipoMovilCreateDTO): Response<EquipoDTO>
}

// 3. Cliente Modular
object ApiClient {
    // Pega aquí la URL que te dio la terminal de LocalTunnel
    var BASE_URL = "https://inventario-app.loca.lt/"

    private var retrofit: Retrofit? = null

    fun getService(): InventoryApiService {
        if (retrofit == null || retrofit!!.baseUrl().toString() != BASE_URL) {

            // 1. Crear un Interceptor para inyectar el header de LocalTunnel
            val headerInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Bypass-Tunnel-Reminder", "true") // <-- ESTO SALTA LA PANTALLA DE ADVERTENCIA
                    // También es buena práctica especificar que esperas JSON
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

            // 2. Agregar el interceptor a un cliente de OkHttp
            val client = OkHttpClient.Builder()
                .addInterceptor(headerInterceptor)
                .build()

            // 3. Construir Retrofit usando ese cliente
            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client) // <-- PASAMOS EL CLIENTE AQUÍ
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(InventoryApiService::class.java)
    }
}