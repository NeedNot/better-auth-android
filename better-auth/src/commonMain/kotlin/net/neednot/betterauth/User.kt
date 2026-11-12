package net.neednot.betterauth

import kotlinx.serialization.Serializable

@Serializable
public data class User(
    val id: String,
    val email: String,
    val name: String = "",
    val emailVerified: Boolean = false,
    val image: String? = null,
)
