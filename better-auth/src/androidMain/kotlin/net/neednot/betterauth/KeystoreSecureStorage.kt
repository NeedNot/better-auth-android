package net.neednot.betterauth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.neednot.betterauth.internal.KeystoreCipher

private val Context.sessionDataStore by preferencesDataStore(name = "better_auth")
private val sessionKey = stringPreferencesKey("session")

/**
 * Stores the session in a Preferences DataStore, encrypted with an AES-256-GCM key that never leaves the
 * Android Keystore. A value that can no longer be decrypted (for example after a device restore) reads as
 * absent, so the user simply signs in again.
 */
public class KeystoreSecureStorage(
    context: Context,
) : SecureStorage {
    private val dataStore = context.applicationContext.sessionDataStore
    private val cipher = KeystoreCipher()

    override suspend fun read(): String? =
        withContext(Dispatchers.IO) {
            dataStore.data.first()[sessionKey]?.let(cipher::decrypt)
        }

    override suspend fun write(value: String) {
        withContext(Dispatchers.IO) {
            val encrypted = cipher.encrypt(value)
            dataStore.edit { it[sessionKey] = encrypted }
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(sessionKey) }
    }
}
