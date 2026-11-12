package net.neednot.betterauth

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SignInTest {
    private val server = FakeAuthServer()

    @Test
    fun `a client with nothing stored reports signed out`() =
        runTest {
            val client = clientFor(server)

            assertEquals(AuthState.SignedOut, client.state.first { it != AuthState.Loading })
        }

    @Test
    fun `requesting a sign-in code posts the email with the sign-in type`() =
        runTest {
            val client = clientFor(server)
            server.answer("""{"success":true}""")

            val result = client.sendSignInCode("ada@example.com")

            assertEquals(Ok(Unit), result)
            assertEquals("/api/auth/email-otp/send-verification-otp", server.pathOf(0))
            assertEquals("""{"email":"ada@example.com","type":"sign-in"}""", server.bodyOf(0))
            assertEquals("app://test", server.requests[0].headers[HttpHeaders.Origin])
        }

    @Test
    fun `signing in with a code returns the user and reports signed in`() =
        runTest {
            val client = clientFor(server)
            server.answerSignIn()

            val result = client.signInWithCode("ada@example.com", "123456")

            assertEquals(Ok(User("user-1", "ada@example.com", "Ada", emailVerified = true)), result)
            assertEquals(AuthState.SignedIn("user-1"), client.state.value)
            assertEquals("/api/auth/sign-in/email-otp", server.pathOf(0))
            assertEquals("""{"email":"ada@example.com","otp":"123456"}""", server.bodyOf(0))
        }

    @Test
    fun `signing in with an id token posts the provider, token and nonce`() =
        runTest {
            val client = clientFor(server)
            server.answerSignIn()

            val result = client.signInWithIdToken("google", idToken = "id-token", nonce = "n-1")

            assertEquals("user-1", result.get()?.id)
            assertEquals(AuthState.SignedIn("user-1"), client.state.value)
            assertEquals("/api/auth/sign-in/social", server.pathOf(0))
            assertEquals("""{"provider":"google","idToken":{"token":"id-token","nonce":"n-1"}}""", server.bodyOf(0))
        }

    @Test
    fun `a stored session is restored by a new client`() =
        runTest {
            val storage = InMemorySecureStorage()
            signedInClient(server, storage)

            val restarted = clientFor(server, storage)

            assertEquals(AuthState.SignedIn("user-1"), restarted.state.first { it != AuthState.Loading })
        }

    @Test
    fun `a sign-in answer without a session cookie is rejected and stays signed out`() =
        runTest {
            val client = clientFor(server)
            server.answer("""{"token":"t","user":{"id":"user-1","email":"ada@example.com"}}""")

            val result = client.signInWithCode("ada@example.com", "123456")

            assertIs<AuthError.UnexpectedResponse>(result.getError())
            assertEquals(AuthState.SignedOut, client.state.value)
        }

    @Test
    fun `a session that cannot be stored fails the sign-in`() =
        runTest {
            val client = clientFor(server, BrokenStorage())
            server.answerSignIn()

            val result = client.signInWithCode("ada@example.com", "123456")

            assertEquals(Err(AuthError.Storage), result)
            assertEquals(AuthState.SignedOut, client.state.value)
        }

    @Test
    fun `server error codes map to typed errors`() =
        runTest {
            val cases =
                listOf(
                    Triple(HttpStatusCode.BadRequest, "INVALID_OTP", AuthError.InvalidCode),
                    Triple(HttpStatusCode.BadRequest, "OTP_EXPIRED", AuthError.CodeExpired),
                    Triple(HttpStatusCode.Forbidden, "TOO_MANY_ATTEMPTS", AuthError.TooManyAttempts),
                    Triple(
                        HttpStatusCode.Forbidden,
                        "EMAIL_NOT_VERIFIED",
                        AuthError.Server(403, "EMAIL_NOT_VERIFIED", "m"),
                    ),
                )
            val client = clientFor(server)

            cases.forEach { (status, code, expected) ->
                server.answer("""{"code":"$code","message":"m"}""", status)

                assertEquals(Err(expected), client.signInWithCode("ada@example.com", "000000"))
            }
        }

    @Test
    fun `a rate-limited request reports how long to wait`() =
        runTest {
            val client = clientFor(server)
            server.answer("Too many requests", HttpStatusCode.TooManyRequests, retryAfter = "42")

            assertEquals(Err(AuthError.RateLimited(42)), client.sendSignInCode("ada@example.com"))
        }

    @Test
    fun `an error answer that is not json keeps its status`() =
        runTest {
            val client = clientFor(server)
            server.answer("<html>Bad gateway</html>", HttpStatusCode.BadGateway)

            assertEquals(Err(AuthError.Server(502, null, null)), client.sendSignInCode("ada@example.com"))
        }

    @Test
    fun `an unreachable server is a network error`() =
        runTest {
            val client = clientFor(server)
            server.failToConnect()

            assertEquals(Err(AuthError.Network), client.sendSignInCode("ada@example.com"))
        }
}
