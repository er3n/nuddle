package app.nuddle.data.api

import app.nuddle.api.model.Error
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import kotlin.time.Instant

/** A failed call: either the server answered with an error (`DRT-xxxx`, docs/architecture.md) or nothing came back. */
sealed interface ApiFailure {
    data class Http(
        val status: Int,
        val code: String?,
        val name: String?,
        /** `invalid_otp`: codes left before the wait (6c). */
        val remainingAttempts: Int? = null,
        /** `otp_locked`: when the wait ends. */
        val retryAt: Instant? = null,
    ) : ApiFailure

    data object Network : ApiFailure
}

sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ApiResult<T>

    data class Failure(
        val failure: ApiFailure,
    ) : ApiResult<Nothing>
}

/** Runs one generated-client call and maps the outcome. IO errors become [ApiFailure.Network]. */
suspend fun <T : Any> apiCall(
    json: Json,
    block: suspend () -> Response<T>,
): ApiResult<T> {
    val response =
        try {
            block()
        } catch (_: IOException) {
            // Offline, timeout or connection reset: the caller decides whether to retry.
            return ApiResult.Failure(ApiFailure.Network)
        }
    val body = response.body()
    if (response.isSuccessful && body != null) return ApiResult.Success(body)
    val error =
        response.errorBody()?.string()?.let { raw ->
            try {
                json.decodeFromString(Error.serializer(), raw).error
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    return ApiResult.Failure(
        ApiFailure.Http(
            status = response.code(),
            code = error?.code,
            name = error?.name?.value,
            remainingAttempts = error?.details?.remainingAttempts,
            retryAt = error?.details?.retryAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        ),
    )
}
