package org.pjsip.pjsua2.app_kotlin.auth

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val token: String,
    val sipData: SipDataResponse,
    val asteriskServer: AsteriskServerResponse
)

data class SipDataResponse(
    val account: String,
    val password: String,
    val extension: String
)

data class AsteriskServerResponse(
    val notificatorName: String,
    val serverName: String,
    val serverPort: Int,
    val serverWeb: String
)
