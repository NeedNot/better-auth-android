package net.neednot.betterauth

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import net.neednot.betterauth.internal.AuthApi
import net.neednot.betterauth.internal.Jwt
import net.neednot.betterauth.internal.Session
import net.neednot.betterauth.internal.SessionStore
import net.neednot.betterauth.internal.SignInBody
import net.neednot.betterauth.internal.TokenBody
import net.neednot.betterauth.internal.applying
import net.neednot.betterauth.internal.isSessionToken
import kotlin.time.Clock

/**
 * Holds one signed-in session for a Better Auth server: keeps it in [SecureStorage], exposes it as [state] and
 * hands out short-lived JWTs (the server's `jwt` plugin) for other backends.
 *
 * Sign-in methods are extension functions, one file per Better Auth feature, for example [signInWithCode] and
 * [signInWithIdToken].
 */
public class BetterAuthClient internal constructor(
    private val config: AuthConfig,
    storage: SecureStorage,
    private val http: HttpClient,
    private val clock: Clock,
) {
    public constructor(config: AuthConfig, storage: SecureStorage) : this(config, storage, HttpClient(), Clock.System)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = SessionStore(storage)
    private val jwtMutex = Mutex()
    private var cachedJwt: Jwt? = null

    internal val api = AuthApi(http, config, sessions)

    public val state: StateFlow<AuthState> = sessions.state

    init {
        scope.launch { sessions.current() }
    }

    /** A JWT for the signed-in user, reused until it is within [AuthConfig.jwtExpiryMargin] of expiring. */
    public suspend fun getJwt(): Result<String, AuthError> =
        jwtMutex.withLock {
            val cached = cachedJwt
            if (cached != null && cached.expiresAt > clock.now() + config.jwtExpiryMargin) {
                Ok(cached.token)
            } else {
                fetchJwt()
            }
        }

    public suspend fun refreshJwt(): Result<String, AuthError> = jwtMutex.withLock { fetchJwt() }

    public suspend fun signOut(): Result<Unit, AuthError> {
        // The answer is ignored: the phone signs out even when the server cannot be reached.
        api.post("sign-out")
        val cleared = sessions.clear()
        jwtMutex.withLock { cachedJwt = null }
        return cleared
    }

    public fun close() {
        scope.cancel()
        http.close()
    }

    internal suspend fun signIn(
        path: String,
        body: JsonObject,
    ): Result<User, AuthError> {
        val reply = api.post(path, body).getOrElse { return Err(it) }
        val user = reply.decode(SignInBody.serializer()).getOrElse { return Err(it) }.user
        val cookies = emptyMap<String, String>().applying(reply.cookies)
        if (cookies.keys.none(::isSessionToken)) {
            return Err(AuthError.UnexpectedResponse("The sign-in answer set no session cookie"))
        }
        sessions.replace(Session(user.id, cookies)).getOrElse { return Err(it) }
        jwtMutex.withLock { cachedJwt = null }
        return Ok(user)
    }

    private suspend fun fetchJwt(): Result<String, AuthError> {
        if (sessions.current() == null) return Err(AuthError.NotSignedIn)
        val reply =
            api.get("token").getOrElse { error ->
                if (error.isUnauthorized()) {
                    cachedJwt = null
                    sessions.clear()
                    return Err(AuthError.SessionExpired)
                }
                return Err(error)
            }
        val token = reply.decode(TokenBody.serializer()).getOrElse { return Err(it) }.token
        cachedJwt = Jwt.parse(token)
        return Ok(token)
    }

    private fun AuthError.isUnauthorized() = this is AuthError.Server && status == HttpStatusCode.Unauthorized.value
}
