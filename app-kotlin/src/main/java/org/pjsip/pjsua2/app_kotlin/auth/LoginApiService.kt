package org.pjsip.pjsua2.app_kotlin.auth

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface LoginApiService {
    @POST("/api/v1/login_cloud")
    suspend fun login(
        @Header("Authorization") authHeader: String,
        @Body request: LoginRequest
    ): Response<LoginResponse>
}
