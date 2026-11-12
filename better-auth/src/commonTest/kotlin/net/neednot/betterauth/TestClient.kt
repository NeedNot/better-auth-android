package net.neednot.betterauth

import io.ktor.client.HttpClient
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

internal val testConfig = AuthConfig(baseUrl = "https://auth.test/api/auth/", origin = "app://test")

internal val startOfTest = Instant.fromEpochSeconds(1_800_000_000)

internal class FakeClock(
    var now: Instant = startOfTest,
) : Clock {
    override fun now(): Instant = now
}

internal fun clientFor(
    server: FakeAuthServer,
    storage: SecureStorage = InMemorySecureStorage(),
    clock: Clock = FakeClock(),
) = BetterAuthClient(testConfig, storage, HttpClient(server.engine), clock)

internal suspend fun signedInClient(
    server: FakeAuthServer,
    storage: SecureStorage = InMemorySecureStorage(),
    clock: Clock = FakeClock(),
): BetterAuthClient {
    val client = clientFor(server, storage, clock)
    server.answerSignIn()
    client.signInWithCode("ada@example.com", "123456")
    return client
}

internal fun jwtExpiringAt(
    expiresAt: Instant,
    label: String = "a",
): String {
    val payload = """{"sub":"user-1","exp":${expiresAt.epochSeconds}}"""
    val encoded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray())
    return "header.$encoded.signature-$label"
}

internal class BrokenStorage : SecureStorage {
    override suspend fun read(): String? = error("unreadable")

    override suspend fun write(value: String): Unit = error("unwritable")

    override suspend fun clear(): Unit = error("unclearable")
}
