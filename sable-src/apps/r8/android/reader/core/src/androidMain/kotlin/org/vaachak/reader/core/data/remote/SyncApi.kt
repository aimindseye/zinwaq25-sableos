/*
 * Copyright (c) 2026 Piyush Daiya
 * *
 * * Permission is hereby granted, free of charge, to any person obtaining a copy
 * * of this software and associated documentation files (the "Software"), to deal
 * * in the Software without restriction, including without limitation the rights
 * * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * * copies of the Software, and to permit persons to whom the Software is
 * * furnished to do so, subject to the following conditions:
 * *
 * * The above copyright notice and this permission notice shall be included in all
 * * copies or substantial portions of the Software.
 * *
 * * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * * SOFTWARE.
 */

package org.vaachak.reader.core.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.vaachak.reader.core.data.remote.dto.LoginRequest
import org.vaachak.reader.core.data.remote.dto.RegisterRequest
import org.vaachak.reader.core.data.remote.dto.SyncDto
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncApi @Inject constructor() {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000 // 15 seconds
            connectTimeoutMillis = 10000 // 10 seconds
            socketTimeoutMillis = 15000
        }

        install(Logging) {
            level = LogLevel.ALL
        }
    }

    // Default fallback - will be overwritten by setBaseUrl from Repository
    private var baseUrl = "https://vaachak-sync.ai-mindseye.workers.dev"

    fun setBaseUrl(url: String) {
        if (url.isNotBlank()) {
            // Remove spaces and trailing slashes
            var cleanedUrl = url.trim().trimEnd('/')

            // Ensure protocol exists
            if (!cleanedUrl.startsWith("http")) {
                cleanedUrl = "https://$cleanedUrl"
            }

            this.baseUrl = cleanedUrl
                   }
    }

    // ==========================================
    // THE UNIFIED VAULT SYNC (Phase 3)
    // ==========================================

    /**
     * SYNC: Sends local changes and receives remote changes in one single transaction.
     */
    suspend fun syncVault(request: SyncDto.SyncRequest): Result<SyncDto.SyncResponse> = withContext(Dispatchers.IO) {
        return@withContext try {
            val fullUrl = "${baseUrl.trimEnd('/')}/api/v1/sync"


            val response = client.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }



            when (response.status.value) {
                in 200..299 -> Result.success(response.body<SyncDto.SyncResponse>())
                401 -> Result.failure(Exception("Unauthorized: Invalid username or password"))
                else -> Result.failure(Exception("Server Error: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "CRITICAL NETWORK ERROR: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    // ==========================================
    // AUTHENTICATION & UTILITIES
    // ==========================================

    suspend fun register(request: RegisterRequest): Result<Unit> {
        return try {
            val response = client.post("$baseUrl/api/v1/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            when (response.status.value) {
                in 200..299 -> Result.success(Unit)
                409 -> Result.failure(Exception("Username already taken"))
                else -> Result.failure(Exception("Registration failed: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Timber.e(e,"Registration Error: ${e.message}")
            Result.failure(e)
        }
    }

    // FIX: Changed request parameter type from RegisterRequest to LoginRequest
    suspend fun login(request: LoginRequest): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val fullUrl = "${baseUrl.trimEnd('/')}/api/v1/login"

            val response = client.post(fullUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }


            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Login failed: ${response.status.value}"))
            }
        } catch (e: Exception) {
            Timber.e(e,"Login Error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun testConnection(): Result<Unit> {
        return try {
            val response = client.get("$baseUrl/api/v1/health")
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Server returned ${response.status.value}"))
            }
        } catch (e: Exception) {
            Timber.e(e,"Health check failed: ${e.message}")
            Result.failure(e)
        }
    }

}