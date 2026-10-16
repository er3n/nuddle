package app.nuddle.data.diagnostics

import android.content.Context
import android.util.Log
import app.nuddle.domain.Diagnostics
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val TAG = "Nuddle"

/**
 * Breadcrumbs, non-fatals and keys go to Crashlytics and to the log (ADR 0023). Without `google-services.json` (CI, a
 * fresh clone) there is no Firebase app and only the log gets them.
 */
class CrashlyticsDiagnostics
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : Diagnostics {
        private val crashlytics: FirebaseCrashlytics? by lazy {
            if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseCrashlytics.getInstance()
        }

        override fun log(line: String) {
            Log.i(TAG, line)
            crashlytics?.log(line)
        }

        override fun record(error: Throwable) {
            Log.w(TAG, "non-fatal", error)
            crashlytics?.recordException(error)
        }

        override fun setKey(
            key: String,
            value: String,
        ) {
            crashlytics?.setCustomKey(key, value)
        }

        override fun setPerson(personId: String?) {
            // Crashlytics clears the user id with an empty string.
            crashlytics?.setUserId(personId.orEmpty())
        }
    }

/** The e2e flavor reports nothing (NUD-39); the log still gets the lines, which E2E runs keep. */
class LogcatDiagnostics
    @Inject
    constructor() : Diagnostics {
        override fun log(line: String) {
            Log.i(TAG, line)
        }

        override fun record(error: Throwable) {
            Log.w(TAG, "non-fatal", error)
        }

        override fun setKey(
            key: String,
            value: String,
        ) = Unit

        override fun setPerson(personId: String?) = Unit
    }
