package net.neednot.betterauth

/**
 * Keeps one opaque value (the serialized session) somewhere only this app can read.
 * Implementations may throw; the client reports that as [AuthError.Storage].
 */
public interface SecureStorage {
    public suspend fun read(): String?

    public suspend fun write(value: String)

    public suspend fun clear()
}
