package net.neednot.betterauth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headers
import kotlinx.io.IOException

internal class FakeAuthServer {
    val requests = mutableListOf<HttpRequestData>()
    private val answers = ArrayDeque<MockRequestHandleScope.() -> HttpResponseData>()

    val engine =
        MockEngine { request ->
            requests += request
            answers.removeFirst().invoke(this)
        }

    fun answer(
        body: String = "{}",
        status: HttpStatusCode = HttpStatusCode.OK,
        setCookies: List<String> = emptyList(),
        retryAfter: String? = null,
    ) {
        answers += {
            respond(
                content = body,
                status = status,
                headers =
                    headers {
                        setCookies.forEach { append(HttpHeaders.SetCookie, it) }
                        retryAfter?.let { append("X-Retry-After", it) }
                    },
            )
        }
    }

    fun failToConnect() {
        answers += { throw IOException("offline") }
    }

    fun answerSignIn(
        userId: String = USER_ID,
        sessionToken: String = SESSION_TOKEN,
    ) = answer(
        body = """{"token":"t","user":{"id":"$userId","email":"ada@example.com","name":"Ada","emailVerified":true}}""",
        setCookies = listOf("$SESSION_COOKIE=$sessionToken; Max-Age=31536000; Path=/; HttpOnly; SameSite=Lax"),
    )

    fun answerJwt(jwt: String) = answer(body = """{"token":"$jwt"}""")

    fun pathOf(index: Int): String = requests[index].url.encodedPath

    fun cookieOf(index: Int): String? = requests[index].headers[HttpHeaders.Cookie]

    fun bodyOf(index: Int): String = (requests[index].body as TextContent).text

    companion object {
        const val USER_ID = "user-1"
        const val SESSION_COOKIE = "__Secure-better-auth.session_token"
        const val SESSION_TOKEN = "abc.def%2Bghi%3D"
    }
}
