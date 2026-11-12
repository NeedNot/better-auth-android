package net.neednot.betterauth.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class CookieChangeTest {
    @Test
    fun `the value is kept exactly as the server sent it`() {
        val change = parseSetCookie("better-auth.session_token=abc.d%2Bf%3D; Max-Age=604800; Path=/; HttpOnly")

        assertEquals(CookieChange("better-auth.session_token", "abc.d%2Bf%3D"), change)
    }

    @Test
    fun `an empty value or a max-age of zero deletes the cookie`() {
        assertEquals(CookieChange("a", null), parseSetCookie("a=; Path=/"))
        assertEquals(CookieChange("a", null), parseSetCookie("a=gone; max-age=0; Path=/"))
    }

    @Test
    fun `a header without a name is ignored`() {
        assertEquals(null, parseSetCookie("=value"))
        assertEquals(null, parseSetCookie("garbage"))
    }

    @Test
    fun `changes add, replace and remove cookies in order`() {
        val cookies = mapOf("keep" to "1", "replace" to "old", "remove" to "x")

        val updated =
            cookies.applying(
                listOf(CookieChange("replace", "new"), CookieChange("remove", null), CookieChange("add", "2")),
            )

        assertEquals(mapOf("keep" to "1", "replace" to "new", "add" to "2"), updated)
    }
}
