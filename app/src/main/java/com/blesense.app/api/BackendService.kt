package com.blesense.app.api

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Response
import java.util.concurrent.TimeUnit

// Generic data packet to be sent to the backend
@Keep
data class SensorPacket(
    @SerializedName("timestamp")
    val timestamp: Long,
    @SerializedName("data")
    val data: Map<String, Any>
)

@Keep
interface BackendService {
    @POST("api/packets")
    suspend fun sendSensorData(@Body packet: SensorPacket): Response<Void>

    @POST("api/packets")
    suspend fun sendSensorDataBatch(@Body packets: List<SensorPacket>): Response<Void>

    // Cloudflare specific endpoint
    @POST("api/packets")
    suspend fun sendSensorDataCloudflare(@Body packet: SensorPacket): Response<Void>

    @POST("api/packets")
    suspend fun sendSensorDataBatchCloudflare(@Body packets: List<SensorPacket>): Response<Void>
}

object RetrofitClient {
    private const val RENDER_BASE_URL = "https://ble-sense-rqnu.onrender.com/"
    private const val CLOUDFLARE_BASE_URL = "https://leone-labour-harmony-visiting.trycloudflare.com"

    // Custom OkHttp client with extended 60-second timeouts & connection pooling
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val renderInstance: BackendService by lazy {
        Retrofit.Builder()
            .baseUrl(RENDER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendService::class.java)
    }

    val cloudflareInstance: BackendService by lazy {
        Retrofit.Builder()
            .baseUrl(CLOUDFLARE_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendService::class.java)
    }

    // For backward compatibility if needed, but we'll update the caller
    val instance: BackendService get() = renderInstance
}
