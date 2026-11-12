package net.neednot.betterauth

public sealed interface AuthState {
    public data object Loading : AuthState

    public data object SignedOut : AuthState

    public data class SignedIn(
        val userId: String,
    ) : AuthState
}
