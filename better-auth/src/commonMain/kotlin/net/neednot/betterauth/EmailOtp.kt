package net.neednot.betterauth

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Needs the server's `emailOTP` plugin. */
public suspend fun BetterAuthClient.sendSignInCode(email: String): Result<Unit, AuthError> =
    api
        .post(
            "email-otp/send-verification-otp",
            buildJsonObject {
                put("email", email)
                put("type", "sign-in")
            },
        ).map { }

public suspend fun BetterAuthClient.signInWithCode(
    email: String,
    code: String,
): Result<User, AuthError> =
    signIn(
        "sign-in/email-otp",
        buildJsonObject {
            put("email", email)
            put("otp", code)
        },
    )
