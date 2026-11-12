package net.neednot.betterauth.internal

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import net.neednot.betterauth.AuthError

internal fun authError(
    status: Int,
    body: String,
    retryAfter: String?,
): AuthError {
    if (status == HttpStatusCode.TooManyRequests.value) return AuthError.RateLimited(retryAfter?.toIntOrNull())
    val error = parseErrorBody(body)
    return when (error.code) {
        "INVALID_OTP" -> AuthError.InvalidCode
        "OTP_EXPIRED" -> AuthError.CodeExpired
        "TOO_MANY_ATTEMPTS" -> AuthError.TooManyAttempts
        else -> AuthError.Server(status, error.code, error.message)
    }
}

private fun parseErrorBody(body: String): ErrorBody =
    try {
        AuthJson.decodeFromString(ErrorBody.serializer(), body)
    } catch (_: SerializationException) {
        ErrorBody()
    }
