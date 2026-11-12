package net.neednot.betterauth

import com.github.michaelbull.result.Result
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Signs in with an ID token the app already obtained from the provider's native SDK
 * (Credential Manager for Google), so no browser redirect happens.
 *
 * @param provider Better Auth's provider id, for example `google` or `apple`.
 */
public suspend fun BetterAuthClient.signInWithIdToken(
    provider: String,
    idToken: String,
    nonce: String? = null,
): Result<User, AuthError> =
    signIn(
        "sign-in/social",
        buildJsonObject {
            put("provider", provider)
            putJsonObject("idToken") {
                put("token", idToken)
                nonce?.let { put("nonce", it) }
            }
        },
    )
