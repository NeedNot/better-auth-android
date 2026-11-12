package net.neednot.betterauth

/** Forgets the session when the process ends. For tests and hosts without a keystore. */
public class InMemorySecureStorage : SecureStorage {
    private var value: String? = null

    override suspend fun read(): String? = value

    override suspend fun write(value: String) {
        this.value = value
    }

    override suspend fun clear() {
        value = null
    }
}
