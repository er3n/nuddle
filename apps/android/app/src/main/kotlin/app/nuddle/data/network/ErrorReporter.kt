package app.nuddle.data.network

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Records the `DRT-xxxx` code shown on 6f as a non-fatal error. */
fun interface ErrorReporter {
    fun report(code: String)
}

/** What Crashlytics groups 6f reports by: one issue per code. */
class UnrecoverableServerError(
    code: String,
) : RuntimeException("Unrecoverable server error $code")

/**
 * 6f's code goes to Crashlytics as a non-fatal error with the code as a key (docs/architecture.md, Error codes;
 * NUD-39), and always to the log. Without `google-services.json` (CI, a fresh clone) there is no Firebase app and
 * only the log gets it.
 */
class CrashlyticsErrorReporter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ErrorReporter {
        override fun report(code: String) {
            Log.w(TAG, "Unrecoverable server error $code")
            if (FirebaseApp.getApps(context).isEmpty()) return
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey(KEY_CODE, code)
                recordException(UnrecoverableServerError(code))
            }
        }

        private companion object {
            const val TAG = "Nuddle"
            const val KEY_CODE = "drt_code"
        }
    }
