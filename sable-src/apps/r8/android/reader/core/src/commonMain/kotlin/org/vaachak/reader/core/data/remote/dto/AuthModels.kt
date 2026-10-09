package org.vaachak.reader.core.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val passwordHash: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val passwordHash: String
)