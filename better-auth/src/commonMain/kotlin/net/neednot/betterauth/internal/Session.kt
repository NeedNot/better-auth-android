package net.neednot.betterauth.internal

import kotlinx.serialization.Serializable

@Serializable
internal data class Session(
    val userId: String,
    val cookies: Map<String, String>,
) {
    val cookieHeader: String
        get() = cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}
