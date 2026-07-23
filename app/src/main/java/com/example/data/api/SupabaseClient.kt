package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object SupabaseClient {
    private const val TAG = "SupabaseClient"

    private var customUrl: String = ""
    private var customAnonKey: String = ""

    fun updateCredentials(url: String, anonKey: String) {
        customUrl = url.trim().removeSurrounding("\"").removeSurrounding("'")
        customAnonKey = anonKey.trim().removeSurrounding("\"").removeSurrounding("'")
    }

    val url: String
        get() {
            if (customUrl.isNotBlank()) return customUrl
            val envUrl = try { BuildConfig.SUPABASE_URL.trim() } catch (_: Throwable) { "" }
            return envUrl.removeSurrounding("\"").removeSurrounding("'")
        }

    val apiKey: String
        get() {
            if (customAnonKey.isNotBlank()) return customAnonKey
            val envKey = try { BuildConfig.SUPABASE_ANON_KEY.trim() } catch (_: Throwable) { "" }
            return envKey.removeSurrounding("\"").removeSurrounding("'")
        }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean
        get() {
            val u = url
            val k = apiKey
            return u.isNotBlank() && u != "SUPABASE_URL" && k.isNotBlank() && k != "SUPABASE_ANON_KEY"
        }

    data class DiagnosticResult(
        val isConnected: Boolean,
        val statusCode: Int,
        val message: String,
        val rawResponseBody: String? = null
    )

    fun runConnectionDiagnostics(tableName: String = "config"): DiagnosticResult {
        if (!isConfigured) {
            return DiagnosticResult(
                isConnected = false,
                statusCode = 0,
                message = "Supabase URL or Anon API Key is not configured in Secrets or Settings."
            )
        }

        val baseUrl = url.removeSuffix("/")
        val key = apiKey

        // Query the requested table directly.
        // Note: The REST root endpoint (/rest/v1/) requires the service_role key,
        // so we skip it and validate by querying an actual table with the anon key.
        return try {
            val endpoint = "$baseUrl/rest/v1/$tableName?select=*&limit=1"
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respBody = response.body?.string()
                when {
                    response.isSuccessful -> {
                        DiagnosticResult(
                            isConnected = true,
                            statusCode = response.code,
                            message = "Connection successful! Fetched data from '$tableName' (HTTP ${response.code}).",
                            rawResponseBody = respBody
                        )
                    }
                    response.code == 401 -> {
                        // Distinguish between invalid key vs. RLS permission issue
                        val hint = respBody ?: ""
                        val isRlsIssue = hint.contains("permission denied", ignoreCase = true) ||
                                hint.contains("42501")
                        if (isRlsIssue) {
                            DiagnosticResult(
                                isConnected = false,
                                statusCode = 401,
                                message = "API key is valid but Row Level Security (RLS) is blocking access to '$tableName'. " +
                                        "Please enable RLS policies or grant SELECT/INSERT to the anon role in Supabase.",
                                rawResponseBody = respBody
                            )
                        } else {
                            DiagnosticResult(
                                isConnected = false,
                                statusCode = 401,
                                message = "Invalid API Key or URL (HTTP 401). Please check your SUPABASE_URL and SUPABASE_ANON_KEY.",
                                rawResponseBody = respBody
                            )
                        }
                    }
                    else -> {
                        val detail = if (!respBody.isNullOrBlank()) " ($respBody)" else ""
                        DiagnosticResult(
                            isConnected = false,
                            statusCode = response.code,
                            message = "Supabase query to '$tableName' returned HTTP ${response.code}: ${response.message}$detail",
                            rawResponseBody = respBody
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Diagnostic check failed: ${e.message}")
            DiagnosticResult(
                isConnected = false,
                statusCode = -1,
                message = "Cannot connect to Supabase host ($baseUrl): ${e.localizedMessage}",
                rawResponseBody = null
            )
        }
    }

    fun checkConnection(): Boolean {
        if (!isConfigured) return false
        val diag = runConnectionDiagnostics("config")
        if (diag.isConnected) return true
        // Fallback: try querying any table to verify connectivity
        return try {
            val baseUrl = url.removeSuffix("/")
            val key = apiKey
            val endpoint = "$baseUrl/rest/v1/config?select=key&limit=1"

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code in 200..299
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check connection error: ${e.message}")
            false
        }
    }

    fun upsert(tableName: String, jsonPayload: String): Result<String> {
        if (!isConfigured) {
            return Result.failure(Exception("Supabase URL or Anon Key is not configured."))
        }

        try {
            val baseUrl = url.removeSuffix("/")
            val key = apiKey
            val endpoint = "$baseUrl/rest/v1/$tableName"

            val body = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully upserted to $tableName: $respStr")
                    return Result.success(respStr)
                } else {
                    Log.e(TAG, "Failed upserting to $tableName (${response.code}): $respStr")
                    return Result.failure(Exception("Supabase HTTP ${response.code}: $respStr"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error upserting to $tableName: ${e.message}")
            return Result.failure(e)
        }
    }

    fun fetchConfig(key: String): Result<String?> {
        if (!isConfigured) {
            return Result.failure(Exception("Supabase credentials not configured."))
        }

        try {
            val baseUrl = url.removeSuffix("/")
            val keyVal = apiKey
            val endpoint = "$baseUrl/rest/v1/config?key=eq.$key&select=value"

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("apikey", keyVal)
                .addHeader("Authorization", "Bearer $keyVal")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val respStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val array = JSONArray(respStr)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val value = if (obj.has("value") && !obj.isNull("value")) obj.getString("value") else null
                        return Result.success(value)
                    }
                    return Result.success(null)
                } else {
                    return Result.failure(Exception("Supabase GET error: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config from Supabase: ${e.message}")
            return Result.failure(e)
        }
    }
}
