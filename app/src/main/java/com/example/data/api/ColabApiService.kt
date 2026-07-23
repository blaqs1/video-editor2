package com.example.data.api

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ColabApiService {
    @GET("list-videos")
    suspend fun listVideos(): ResponseBody

    @Multipart
    @POST("execute-edit")
    suspend fun executeEdit(
        @Part("filename") filename: RequestBody,
        @Part("command") command: RequestBody,
        @Part audio: MultipartBody.Part?,
        @Part intro: MultipartBody.Part?,
        @Header("X-Gemini-API-Key") geminiApiKeyHeader: String? = null,
        @Part("gemini_api_key") geminiApiKeyPart: RequestBody? = null,
        @Part("ai_engine") aiEnginePart: RequestBody? = null
    ): ResponseBody

    @Multipart
    @POST("upload-overlay")
    suspend fun uploadOverlay(
        @Part file: MultipartBody.Part
    ): ResponseBody

    @Multipart
    @POST("upload-to-library")
    suspend fun uploadToLibrary(
        @Part file: MultipartBody.Part,
        @Part("filename") filename: RequestBody? = null
    ): ResponseBody
}

object ColabApiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(600, TimeUnit.SECONDS)
        .readTimeout(600, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS)
        .build()

    fun getService(baseUrl: String): ColabApiService {
        // Ensure the base URL is properly formatted
        val formattedUrl = when {
            baseUrl.isBlank() -> "https://placeholder.ngrok-free.app/"
            !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") -> "https://$baseUrl"
            else -> baseUrl
        }.let {
            if (it.endsWith("/")) it else "$it/"
        }

        return Retrofit.Builder()
            .baseUrl(formattedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ColabApiService::class.java)
    }
}
