package ayola.literacyapp.game.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // Production backend on Railway (HTTPS — reachable from a physical device).
    // For local dev against Django on your Mac, use "http://10.0.2.2:8001/" (emulator loopback).
    private const val BASE_URL = "https://literacyapp-production-b4fe.up.railway.app/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    val apiService: LiteracyApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LiteracyApiService::class.java)
    }
}
