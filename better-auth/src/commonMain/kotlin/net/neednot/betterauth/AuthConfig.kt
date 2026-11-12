package net.neednot.betterauth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @param baseUrl where the Better Auth handler is mounted, for example `https://auth.example.com/api/auth`.
 * @param origin sent as the `Origin` header; it must be listed in the server's `trustedOrigins`.
 * @param jwtExpiryMargin a cached JWT this close to expiring is replaced instead of returned.
 */
public data class AuthConfig(
    val baseUrl: String,
    val origin: String,
    val jwtExpiryMargin: Duration = 30.seconds,
)
