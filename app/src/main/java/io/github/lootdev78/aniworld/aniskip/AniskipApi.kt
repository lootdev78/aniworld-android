package io.github.lootdev78.aniworld.aniskip

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// DTOs — Aniskip API returns times in seconds (floating). Convert to ms helpers for playback.
data class AniskipRequest(val video_url: String)

data class AniskipSegment(
    val startTime: Double,
    val endTime: Double,
    val type: String
) {
    fun startMs(): Long = (startTime * 1000.0).toLong()
    fun endMs(): Long = (endTime * 1000.0).toLong()
}

interface AniskipService {
    @Headers("Content-Type: application/json")
    @POST("/v1/skip")
    suspend fun getSkipSegments(@Body request: AniskipRequest): List<AniskipSegment>
}

object AniskipApi {
    fun create(baseUrl: String): AniskipService {
        val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder().addInterceptor(logger).build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        return retrofit.create(AniskipService::class.java)
    }
}
