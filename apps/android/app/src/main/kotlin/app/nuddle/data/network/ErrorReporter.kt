package app.nuddle.data.network

import android.util.Log
import javax.inject.Inject

/** Records the `DRT-xxxx` code shown on 6f as a non-fatal error. */
fun interface ErrorReporter {
    fun report(code: String)
}

/** Until Crashlytics is in the app (NUD-39, open-questions#39), the code only goes to the log. */
class LogErrorReporter
    @Inject
    constructor() : ErrorReporter {
        override fun report(code: String) {
            Log.w(TAG, "Unrecoverable server error $code")
        }

        private companion object {
            const val TAG = "Nuddle"
        }
    }
