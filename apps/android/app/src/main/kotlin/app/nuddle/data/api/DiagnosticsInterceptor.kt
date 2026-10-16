package app.nuddle.data.api

import app.nuddle.domain.Diagnostics
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation
import java.io.IOException
import java.net.SocketTimeoutException

/** A call to our API that failed, as Crashlytics shows it: one issue per call and outcome (ADR 0023). */
class ApiCallFailed(
    call: String,
    outcome: String,
) : RuntimeException("$call $outcome") {
    init {
        // Crashlytics groups non-fatals by their top frame. Every one of these is made in the interceptor, so the call and
        // its outcome go first; otherwise a new failing call would hide in an old issue.
        stackTrace = arrayOf(StackTraceElement("api." + call.replace(' ', '.'), outcome.replace(' ', '_'), null, -1)) + stackTrace
    }
}

/**
 * Leaves a breadcrumb for every call (`api done 200`, `api writeTask 409 DRT-4001`, `api sync network
 * UnknownHostException`) and records the failures the app does not expect in normal use as non-fatals (ADR 0023).
 * Only the call's name, the status and the `DRT-xxxx` code are read: never the URL, which carries ids, invite codes and
 * signed Storage tokens, and never a body beyond that code.
 */
class DiagnosticsInterceptor(
    private val diagnostics: Diagnostics,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val call = callName(request)
        val response =
            try {
                chain.proceed(request)
            } catch (error: IOException) {
                val canceled = chain.call().isCanceled()
                // A cancelled call (the screen went away, or the call timeout fired) and a lost connection are normal on a
                // phone; a socket timeout means the server did not answer.
                val kind = if (canceled) "canceled" else error.javaClass.simpleName
                diagnostics.log("api $call network $kind")
                if (!canceled && error is SocketTimeoutException) diagnostics.record(ApiCallFailed(call, "timeout"))
                throw error
            }
        if (response.isSuccessful) {
            diagnostics.log("api $call ${response.code}")
            return response
        }
        val code = drtCode(response)
        val outcome = listOfNotNull(response.code.toString(), code).joinToString(" ")
        diagnostics.log("api $call $outcome")
        if (code !in EXPECTED) diagnostics.record(ApiCallFailed(call, outcome))
        return response
    }

    private companion object {
        /** `invalid_otp` and `otp_locked`: a mistyped code, which 1c and 6c explain. */
        val EXPECTED = setOf("DRT-1006", "DRT-1008")
        val DRT_CODE = Regex(""""code"\s*:\s*"(DRT-\d{4})"""")
        const val PEEK_BYTES = 1024L

        /** The generated client's method (`done`, `sync`), or the outbox's command; Storage uploads have no name. */
        fun callName(request: Request): String {
            val invocation = request.tag(Invocation::class.java) ?: return "storage ${request.method}"
            val method = invocation.method()
            return if (method.declaringClass == RawCommandApi::class.java) {
                "outbox ${invocation.arguments().firstOrNull()}"
            } else {
                method.name
            }
        }

        fun drtCode(response: Response): String? =
            try {
                DRT_CODE.find(response.peekBody(PEEK_BYTES).string())?.groupValues?.get(1)
            } catch (_: IOException) {
                null
            }
    }
}
