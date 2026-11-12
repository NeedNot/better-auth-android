package net.neednot.betterauth

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class SessionTest {
    private val server = FakeAuthServer()
    private val cookie = FakeAuthServer.SESSION_COOKIE

    @Test
    fun `signing out tells the server and forgets the session`() =
        runTest {
            val storage = InMemorySecureStorage()
            val client = signedInClient(server, storage)
            server.answer("""{"success":true}""", setCookies = listOf("$cookie=; Max-Age=0; Path=/"))

            val result = client.signOut()

            assertEquals(Ok(Unit), result)
            assertEquals("/api/auth/sign-out", server.pathOf(1))
            assertEquals("$cookie=${FakeAuthServer.SESSION_TOKEN}", server.cookieOf(1))
            assertEquals(AuthState.SignedOut, client.state.value)
            assertEquals(null, storage.read())
        }

    @Test
    fun `signing out offline still forgets the session`() =
        runTest {
            val storage = InMemorySecureStorage()
            val client = signedInClient(server, storage)
            server.failToConnect()

            client.signOut()

            assertEquals(AuthState.SignedOut, client.state.value)
            assertEquals(Err(AuthError.NotSignedIn), client.getJwt())
            assertEquals(AuthState.SignedOut, clientFor(server, storage).state.first { it != AuthState.Loading })
        }

    @Test
    fun `cookies the server rotates later are sent and survive a restart`() =
        runTest {
            val storage = InMemorySecureStorage()
            val client = signedInClient(server, storage)
            val jwt = jwtExpiringAt(startOfTest + 15.minutes)
            server.answer(
                body = """{"token":"$jwt"}""",
                setCookies =
                    listOf(
                        "$cookie=rotated; Max-Age=31536000; Path=/",
                        "better-auth.session_data=cache; Path=/",
                    ),
            )
            client.getJwt()
            server.answerJwt(jwt)
            server.answerJwt(jwt)

            client.refreshJwt()
            clientFor(server, storage).refreshJwt()

            val rotated = "$cookie=rotated; better-auth.session_data=cache"
            assertEquals(rotated, server.cookieOf(2))
            assertEquals(rotated, server.cookieOf(3))
        }
}
