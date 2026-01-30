package com.thaiprompt.smschecker.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates Retrofit API clients for different server configurations.
 * Each server gets its own client instance with proper base URL.
 */
@Singleton
class ApiClientFactory @Inject constructor() {

    private val clientCache = mutableMapOf<String, PaymentApiService>()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "SmsChecker-Android/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun getClient(baseUrl: String): PaymentApiService {
        val normalizedUrl = normalizeUrl(baseUrl)
        return clientCache.getOrPut(normalizedUrl) {
            Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(PaymentApiService::class.java)
        }
    }

    fun clearCache() {
        clientCache.clear()
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http")) {
            normalized = "https://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized += "/"
        }
        return normalized
    }
}
