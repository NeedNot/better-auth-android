package net.neednot.betterauth.internal

/** One `Set-Cookie` header. A null [value] means the server deleted the cookie. */
internal data class CookieChange(
    val name: String,
    val value: String?,
)

private const val SESSION_TOKEN_SUFFIX = "session_token"

internal fun isSessionToken(cookieName: String) = cookieName.endsWith(SESSION_TOKEN_SUFFIX)

// Values are kept exactly as received: Better Auth signs and percent-encodes them, and decoding then
// re-encoding with a general cookie parser can change the bytes the server compares.
internal fun parseSetCookie(header: String): CookieChange? {
    val parts = header.split(';')
    val pair = parts.first()
    val separator = pair.indexOf('=')
    if (separator <= 0) return null
    val name = pair.substring(0, separator).trim()
    val value = pair.substring(separator + 1).trim()
    val deleted = value.isEmpty() || parts.drop(1).any(::isExpiredMaxAge)
    return CookieChange(name, value.takeUnless { deleted })
}

private fun isExpiredMaxAge(attribute: String): Boolean {
    val (name, value) = attribute.split('=', limit = 2).map(String::trim).takeIf { it.size == 2 } ?: return false
    return name.equals("Max-Age", ignoreCase = true) && (value.toLongOrNull() ?: return false) <= 0
}

internal fun Map<String, String>.applying(changes: List<CookieChange>): Map<String, String> =
    changes.fold(this) { cookies, change ->
        if (change.value == null) cookies - change.name else cookies + (change.name to change.value)
    }
