package net.neednot.betterauth.internal

import kotlinx.serialization.Serializable
import net.neednot.betterauth.User

@Serializable
internal data class SignInBody(
    val user: User,
)

@Serializable
internal data class TokenBody(
    val token: String,
)

@Serializable
internal data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
)
