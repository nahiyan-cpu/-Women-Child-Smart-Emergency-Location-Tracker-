package com.example.data.remote

import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SosDispatchRequest(
    val incidentId: String,
    val shareId: String,
    val latitude: Double,
    val longitude: Double,
    val triggerType: String,
    val message: String,
    val recipientTokens: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SosDispatchResponse(
    val dispatched: Boolean = false,
    val notifiedCount: Int = 0,
    val dispatchId: String = "",
    val message: String = "",
)

@JsonClass(generateAdapter = true)
data class BackendHealthResponse(
    val status: String = "unknown",
    val fcmConfigured: Boolean = false,
)

interface BackendDispatchService {
    @POST("api/v1/sos/dispatch")
    suspend fun dispatchSosAlert(
        @Header("Authorization") bearerToken: String,
        @Body request: SosDispatchRequest,
    ): Response<SosDispatchResponse>

    @GET("api/v1/health")
    suspend fun health(): Response<BackendHealthResponse>
}

class BackendDispatchClient {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL
        .takeIf { it.isNotBlank() && it != "https://api.safeguard-sos.example.com" }
        ?.let { if (it.endsWith("/")) it else "$it/" }
        ?: "https://ais-dev-c7y2kzcnykaotqnojb4vh2-53089186225.asia-east1.run.app/"

    private val api: BackendDispatchService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendDispatchService::class.java)
    }

    suspend fun dispatchSosViaBackend(request: SosDispatchRequest): Result<SosDispatchResponse> = runCatching {
        val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("Authenticated Firebase ID token required.")
        val response = api.dispatchSosAlert("Bearer $token", request)
        if (response.isSuccessful) {
            response.body() ?: SosDispatchResponse(dispatched = true)
        } else {
            throw IllegalStateException("Backend returned HTTP ${response.code()}")
        }
    }

    suspend fun checkHealth(): Result<BackendHealthResponse> = runCatching {
        val response = api.health()
        if (response.isSuccessful) {
            response.body() ?: BackendHealthResponse(status = "ok")
        } else {
            throw IllegalStateException("Backend unreachable (HTTP ${response.code()})")
        }
    }
}
