package org.pjsip.pjsua2.app_kotlin.auth

import android.util.Base64

class LoginRepository {
    private val apiService = RetrofitClient.instance

    suspend fun login(username: String, secret: String): Result<LoginResponse> {
        return try {
            val credentials = "$username:$secret"
            val authHeader =
                "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

            val response = apiService.login(authHeader, LoginRequest(username, secret))

            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty response body"))
            } else {
                Result.failure(Exception("Login failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
