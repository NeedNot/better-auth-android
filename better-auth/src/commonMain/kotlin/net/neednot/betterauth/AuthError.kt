package net.neednot.betterauth

public sealed interface AuthError {
    public data object NotSignedIn : AuthError

    public data object SessionExpired : AuthError

    public data object InvalidCode : AuthError

    public data object CodeExpired : AuthError

    public data object TooManyAttempts : AuthError

    public data class RateLimited(
        val retryAfterSeconds: Int?,
    ) : AuthError

    public data object Network : AuthError

    public data object Storage : AuthError

    /** Any other error answer; [code] is Better Auth's error code when the server sent one. */
    public data class Server(
        val status: Int,
        val code: String?,
        val message: String?,
    ) : AuthError

    public data class UnexpectedResponse(
        val message: String,
    ) : AuthError
}
