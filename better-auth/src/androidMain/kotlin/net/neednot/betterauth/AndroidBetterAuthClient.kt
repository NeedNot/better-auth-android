package net.neednot.betterauth

import android.content.Context

public fun BetterAuthClient(
    context: Context,
    config: AuthConfig,
): BetterAuthClient = BetterAuthClient(config, KeystoreSecureStorage(context))
