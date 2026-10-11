package app.nuddle.data.api

import app.nuddle.api.AuthApi
import app.nuddle.api.model.RefreshRequest
import app.nuddle.api.model.Session
import app.nuddle.data.session.SessionStore
import app.nuddle.data.session.StoredSession
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import kotlin.time.Instant

internal fun Session.toStored(): StoredSession =
    StoredSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = Instant.parse(expiresAt),
        personId = personId,
        registered = registered,
    )

private const val AUTHORIZATION = "Authorization"

private fun Request.withBearer(token: String): Request = newBuilder().header(AUTHORIZATION, "Bearer $token").build()

/** Adds the stored access token to every call except the sign-in endpoints (those use a client without this interceptor). */
class AuthInterceptor(
    private val sessions: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // OkHttp calls interceptors on its own worker threads, never on the main thread.
        val token = runBlocking { sessions.current()?.accessToken }
        val request = chain.request()
        return chain.proceed(if (token != null) request.withBearer(token) else request)
    }
}

/**
 * On a 401, exchanges the refresh token once through `POST /api/v1/auth/refresh` (ADR 0014) and retries. A refresh
 * the server rejects signs the user out locally; the next launch starts at the welcome screen.
 */
class TokenAuthenticator(
    private val sessions: SessionStore,
    private val authApi: () -> AuthApi,
) : Authenticator {
    private val mutex = Mutex()

    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        if (response.priorResponse != null) return null
        val failedToken = response.request.header(AUTHORIZATION)?.removePrefix("Bearer ")
        // OkHttp calls authenticators on its own worker threads, never on the main thread.
        val fresh =
            runBlocking {
                mutex.withLock {
                    val current = sessions.current() ?: return@withLock null
                    // Another call refreshed while this one waited.
                    if (current.accessToken != failedToken) return@withLock current.accessToken
                    val refreshed =
                        try {
                            authApi().refreshSession(RefreshRequest(current.refreshToken))
                        } catch (_: java.io.IOException) {
                            // Offline: keep the session and fail this call; the outbox retries later.
                            return@withLock null
                        }
                    val body = refreshed.body()
                    when {
                        refreshed.isSuccessful && body != null -> {
                            body.toStored().also { sessions.save(it) }.accessToken
                        }

                        refreshed.code() == HTTP_UNAUTHORIZED -> {
                            sessions.clear()
                            null
                        }

                        else -> {
                            null
                        }
                    }
                }
            }
        return fresh?.let { response.request.withBearer(it) }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
