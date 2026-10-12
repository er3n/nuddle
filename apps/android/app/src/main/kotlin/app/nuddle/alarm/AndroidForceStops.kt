package app.nuddle.alarm

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.time.Instant

/**
 * The previous process exit from `ApplicationExitInfo` (API 30+). A force stop, by the user or by a phone brand's
 * battery manager, is reported as `REASON_USER_REQUESTED`. Older versions cannot tell.
 */
class AndroidForceStops
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ForceStops {
        override fun last(): Instant? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val exit =
                context
                    .getSystemService(ActivityManager::class.java)
                    .getHistoricalProcessExitReasons(context.packageName, 0, 1)
                    .firstOrNull() ?: return null
            return if (exit.reason == ApplicationExitInfo.REASON_USER_REQUESTED) Instant.fromEpochMilliseconds(exit.timestamp) else null
        }
    }
