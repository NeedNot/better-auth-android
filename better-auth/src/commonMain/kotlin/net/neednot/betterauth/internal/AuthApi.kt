package net.neednot.betterauth.internal

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import net.neednot.betterauth.AuthConfig
import net.neednot.betterauth.AuthError

private const val RETRY_AFTER_HEADER = "X-Retry-After"

internal class AuthApi(
    private val http: HttpClient,
    private val config: AuthConfig,
    private val sessions: SessionStore,
) {
    suspend fun get(path: String): Result<Reply, AuthError> = send(HttpMethod.Get, path, body = null)

    suspend fun post(
        path: String,
        body: JsonObject = JsonObject(emptyMap()),
    ): Result<Reply, AuthError> = send(HttpMethod.Post, path, body)

    private suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonObject?,
    ): Result<Reply, AuthError> {
        val session = sessions.current()
        return try {
            val response =
                http.request("${config.baseUrl.trimEnd('/')}/$path") {
                    this.method = method
                    header(HttpHeaders.Origin, config.origin)
                    session?.let { header(HttpHeaders.Cookie, it.cookieHeader) }
                    body?.let { setBody(TextContent(it.toString(), ContentType.Application.Json)) }
                }
            val text = response.bodyAsText()
            val cookies =
                response.headers
                    .getAll(HttpHeaders.SetCookie)
                    .orEmpty()
                    .mapNotNull(::parseSetCookie)
            sessions.updateCookies(cookies)
            if (response.status.isSuccess()) {
                Ok(Reply(text, cookies))
            } else {
                Err(authError(response.status.value, text, response.headers[RETRY_AFTER_HEADER]))
            }
        } catch (_: IOException) {
            Err(AuthError.Network)
        }
    }
}
