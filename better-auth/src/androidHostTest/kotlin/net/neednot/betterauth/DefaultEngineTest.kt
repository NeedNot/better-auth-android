package net.neednot.betterauth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultEngineTest {
    @Test
    fun `the public constructor finds an http engine on the classpath`() =
        runTest {
            val client = BetterAuthClient(testConfig, InMemorySecureStorage())

            assertEquals(AuthState.SignedOut, client.state.first { it != AuthState.Loading })
            client.close()
        }
}
