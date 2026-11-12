package net.neednot.betterauth.internal

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.neednot.betterauth.AuthError
import net.neednot.betterauth.AuthState
import net.neednot.betterauth.SecureStorage

internal class SessionStore(
    private val storage: SecureStorage,
) {
    private val mutex = Mutex()
    private var loaded = false
    private var session: Session? = null
    private val mutableState = MutableStateFlow<AuthState>(AuthState.Loading)

    val state: StateFlow<AuthState> = mutableState.asStateFlow()

    suspend fun current(): Session? =
        mutex.withLock {
            if (!loaded) publish(readStored())
            session
        }

    suspend fun replace(newSession: Session): Result<Unit, AuthError> =
        mutex.withLock {
            persist(newSession).onSuccess { publish(newSession) }
        }

    suspend fun updateCookies(changes: List<CookieChange>) {
        if (changes.isEmpty()) return
        mutex.withLock {
            val current = session ?: return
            val updated = current.copy(cookies = current.cookies.applying(changes))
            if (updated == current) return
            publish(updated)
            // A failed write is not reported: the session in memory is right, and the stored one still
            // holds the same session token.
            persist(updated)
        }
    }

    suspend fun clear(): Result<Unit, AuthError> =
        mutex.withLock {
            publish(null)
            storing { storage.clear() }
        }

    private fun publish(newSession: Session?) {
        loaded = true
        session = newSession
        mutableState.value = newSession?.let { AuthState.SignedIn(it.userId) } ?: AuthState.SignedOut
    }

    private suspend fun readStored(): Session? =
        storing { storage.read()?.let { AuthJson.decodeFromString(Session.serializer(), it) } }.get()

    private suspend fun persist(newSession: Session): Result<Unit, AuthError> =
        storing { storage.write(AuthJson.encodeToString(Session.serializer(), newSession)) }

    private suspend fun <T> storing(block: suspend () -> T): Result<T, AuthError> =
        try {
            Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Err(AuthError.Storage)
        }
}
