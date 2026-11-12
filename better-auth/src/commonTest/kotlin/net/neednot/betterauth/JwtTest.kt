package net.neednot.betterauth

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class JwtTest {
    private val server = FakeAuthServer()
    private val clock = FakeClock()
    private val firstJwt = jwtExpiringAt(startOfTest + 15.minutes, label = "first")
    private val secondJwt = jwtExpiringAt(startOfTest + 30.minutes, label = "second")

    @Test
    fun `a jwt is fetched with the session cookie and reused while fresh`() =
        runTest {
            val client = signedInClient(server, clock = clock)
            server.answerJwt(firstJwt)

            val fetched = client.getJwt()
            clock.now += 14.minutes
            val reused = client.getJwt()

            assertEquals(Ok(firstJwt), fetched)
            assertEquals(Ok(firstJwt), reused)
            assertEquals(2, server.requests.size)
            assertEquals("/api/auth/token", server.pathOf(1))
            assertEquals("${FakeAuthServer.SESSION_COOKIE}=${FakeAuthServer.SESSION_TOKEN}", server.cookieOf(1))
        }

    @Test
    fun `a jwt inside the expiry margin is replaced`() =
        runTest {
            val client = signedInClient(server, clock = clock)
            server.answerJwt(firstJwt)
            server.answerJwt(secondJwt)
            client.getJwt()

            clock.now += 15.minutes - 20.seconds

            assertEquals(Ok(secondJwt), client.getJwt())
        }

    @Test
    fun `refreshing asks the server even when the cached jwt is fresh`() =
        runTest {
            val client = signedInClient(server, clock = clock)
            server.answerJwt(firstJwt)
            server.answerJwt(secondJwt)
            client.getJwt()

            assertEquals(Ok(secondJwt), client.refreshJwt())
            assertEquals(Ok(secondJwt), client.getJwt())
        }

    @Test
    fun `concurrent callers share one fetch`() =
        runTest {
            val client = signedInClient(server, clock = clock)
            server.answerJwt(firstJwt)

            val results = List(5) { async { client.getJwt() } }.awaitAll()

            assertEquals(List(5) { Ok(firstJwt) }, results)
            assertEquals(2, server.requests.size)
        }

    @Test
    fun `a token without a readable expiry is returned but never reused`() =
        runTest {
            val client = signedInClient(server, clock = clock)
            server.answerJwt("opaque")
            server.answerJwt(firstJwt)

            assertEquals(Ok("opaque"), client.getJwt())
            assertEquals(Ok(firstJwt), client.getJwt())
        }

    @Test
    fun `asking for a jwt while signed out makes no request`() =
        runTest {
            val client = clientFor(server)

            assertEquals(Err(AuthError.NotSignedIn), client.getJwt())
            assertEquals(0, server.requests.size)
        }

    @Test
    fun `a rejected session signs the client out and clears storage`() =
        runTest {
            val storage = InMemorySecureStorage()
            val client = signedInClient(server, storage, clock)
            server.answer("""{"message":"Unauthorized"}""", HttpStatusCode.Unauthorized)

            val result = client.getJwt()

            assertEquals(Err(AuthError.SessionExpired), result)
            assertEquals(AuthState.SignedOut, client.state.value)
            assertEquals(null, storage.read())
        }

    @Test
    fun `a network failure keeps the session`() =
        runTest {
            val client = signedInClient(server, clock = clock)
            server.failToConnect()

            assertEquals(Err(AuthError.Network), client.getJwt())
            assertEquals(AuthState.SignedIn("user-1"), client.state.value)
        }

    @Test
    fun `a jwt is not reused after signing in again`() =
        runTest {
            val client = signedInClient(server, clock = clock)
            server.answerJwt(firstJwt)
            client.getJwt()
            server.answerSignIn(userId = "user-2", sessionToken = "other")
            client.signInWithCode("grace@example.com", "654321")
            server.answerJwt(secondJwt)

            assertEquals(Ok(secondJwt), client.getJwt())
        }
}
