package org.pjsip.pjsua2.app_kotlin.auth

data class PjiSipData(
    val domain: String,
    val user: String,
    val secret: String,
    val idUri: String,
    val ext: String
)