package app.nuddle.data.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Sends a queued command as stored. The body was encoded from the generated contract model when it was queued
 * (data/outbox), so the outbox replays exactly what the typed client would have sent.
 */
interface RawCommandApi {
    @POST("api/v1/commands/{name}")
    suspend fun send(
        @Path("name") name: String,
        @Body body: RequestBody,
    ): Response<ResponseBody>
}
