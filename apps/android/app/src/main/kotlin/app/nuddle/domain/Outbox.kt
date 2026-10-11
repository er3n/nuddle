package app.nuddle.domain

/** What to do with an outbox command after one send attempt (docs/architecture.md, Sync model). */
enum class SendOutcome { SENT, RETRY, DROP }

object OutboxPolicy {
    private const val HTTP_OK_MIN = 200
    private const val HTTP_OK_MAX = 299
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_TIMEOUT = 408
    private const val HTTP_TOO_MANY = 429
    private const val HTTP_SERVER_MIN = 500

    /**
     * `null` means the request never got an answer (offline, timeout). Commands are idempotent, so anything that may
     * succeed later is retried; a 4xx the server answered on purpose (wrong status, not found) will not change on a
     * replay and is dropped.
     */
    fun outcome(httpStatus: Int?): SendOutcome =
        when {
            httpStatus == null -> SendOutcome.RETRY
            httpStatus in HTTP_OK_MIN..HTTP_OK_MAX -> SendOutcome.SENT
            httpStatus == HTTP_UNAUTHORIZED || httpStatus == HTTP_TIMEOUT || httpStatus == HTTP_TOO_MANY -> SendOutcome.RETRY
            httpStatus >= HTTP_SERVER_MIN -> SendOutcome.RETRY
            else -> SendOutcome.DROP
        }
}
