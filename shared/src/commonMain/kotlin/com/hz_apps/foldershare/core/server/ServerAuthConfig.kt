package com.hz_apps.foldershare.core.server

/**
 * Credentials model for basic authentication.
 */
data class UserCredentials(
    val username: String,
    val password: String
)

/**
 * Functional interface for authenticating credentials (DIP / OCP).
 */
fun interface ServerAuthenticator {
    fun authenticate(credentials: UserCredentials): Boolean
}

/**
 * Configuration options for WebDAV authentication.
 */
sealed interface ServerAuthConfig {
    object Disabled : ServerAuthConfig

    data class Basic(val authenticator: ServerAuthenticator) : ServerAuthConfig {
        constructor(validUsername: String, validPassword: String) : this(
            ServerAuthenticator { credentials ->
                credentials.username == validUsername && credentials.password == validPassword
            }
        )
    }
}

/**
 * Configuration encapsulating all WebDAV server startup parameters.
 */
data class WebDavServerConfig(
    val port: Int = ServerConstants.DEFAULT_PORT,
    val authConfig: ServerAuthConfig = ServerAuthConfig.Disabled,
    val isHttpsEnabled: Boolean = false,
    val deviceUuid: String? = null
)

