package net.neednot.betterauth.internal

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import net.neednot.betterauth.AuthError

internal class Reply(
    private val body: String,
    val cookies: List<CookieChange>,
) {
    fun <T> decode(deserializer: DeserializationStrategy<T>): Result<T, AuthError> =
        try {
            Ok(AuthJson.decodeFromString(deserializer, body))
        } catch (e: SerializationException) {
            Err(AuthError.UnexpectedResponse(e.message.orEmpty()))
        }
}
