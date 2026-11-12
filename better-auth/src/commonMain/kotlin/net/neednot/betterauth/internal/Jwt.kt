package net.neednot.betterauth.internal

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.time.Instant

internal class Jwt(
    val token: String,
    val expiresAt: Instant,
) {
    companion object {
        private const val SEGMENTS = 3
        private val payloadEncoding = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

        fun parse(token: String): Jwt? {
            val segments = token.split('.')
            if (segments.size != SEGMENTS) return null
            val expiresAtSeconds =
                try {
                    val payload = payloadEncoding.decode(segments[1]).decodeToString()
                    AuthJson
                        .parseToJsonElement(payload)
                        .jsonObject["exp"]
                        ?.jsonPrimitive
                        ?.longOrNull
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: SerializationException) {
                    null
                }
            return expiresAtSeconds?.let { Jwt(token, Instant.fromEpochSeconds(it)) }
        }
    }
}
