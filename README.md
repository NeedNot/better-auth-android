# better-auth-android

A small Kotlin client for a [Better Auth](https://better-auth.com) server, for apps that cannot use the JavaScript
SDK. It signs a user in, keeps the session in encrypted storage, exposes the auth state as a `StateFlow`, and hands
out short-lived JWTs for your other backends.

It is a Kotlin Multiplatform library with Android and JVM targets. iOS is not published yet.

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io") {
            content { includeGroup("com.github.NeedNot.better-auth-android") }
        }
    }
}

// build.gradle.kts
dependencies {
    implementation("com.github.NeedNot.better-auth-android:better-auth:0.1.0")
}
```

Android needs `minSdk` 26 and `compileSdk` 37 or later.

## Use

```kotlin
val auth = BetterAuthClient(
    context,
    AuthConfig(
        baseUrl = "https://auth.example.com/api/auth",
        origin = "https://app.example.com",
    ),
)

auth.state.collect { state ->
    when (state) {
        AuthState.Loading -> Unit
        AuthState.SignedOut -> showSignIn()
        is AuthState.SignedIn -> showHome(state.userId)
    }
}
```

Every call returns a [kotlin-result](https://github.com/michaelbull/kotlin-result) `Result<T, AuthError>`; nothing
throws.

```kotlin
auth.sendSignInCode("ada@example.com")
auth.signInWithCode("ada@example.com", "123456")

auth.signInWithIdToken(provider = "google", idToken = googleIdToken)

auth.getJwt()      // cached until shortly before it expires
auth.refreshJwt()  // always asks the server
auth.signOut()
```

`getJwt()` returns `AuthError.SessionExpired` and signs the client out when the server no longer accepts the
session.

## What the server needs

| Client call | Better Auth setup |
|---|---|
| `sendSignInCode`, `signInWithCode` | `emailOTP` plugin |
| `signInWithIdToken` | the social provider configured (for example Google) |
| `getJwt`, `refreshJwt` | `jwt` plugin |

`AuthConfig.origin` must be one of the server's `trustedOrigins`. The session travels as Better Auth's own cookies,
so the `bearer` plugin is not required.

## Storage

On Android, `BetterAuthClient(context, config)` stores the session in a Preferences DataStore, encrypted with an
AES-256-GCM key held in the Android Keystore. On other targets, or to use your own storage, implement
`SecureStorage` and call `BetterAuthClient(config, storage)`. `InMemorySecureStorage` is there for tests.

## Adding a sign-in method

Only the email code and ID token flows are covered so far. Each method is a file of extension functions on
`BetterAuthClient` that call the internal `signIn(path, body)`, which stores the session from any Better Auth
endpoint that answers with a user and a session cookie. See `EmailOtp.kt` for the pattern.

## License

MIT
