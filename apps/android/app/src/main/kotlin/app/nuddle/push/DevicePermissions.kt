package app.nuddle.push

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import app.nuddle.alarm.FullScreenPermission
import app.nuddle.alarm.NotificationChannels
import app.nuddle.api.model.PermissionSummary
import app.nuddle.api.model.PermissionSummaryChannels
import app.nuddle.data.BatteryOptimization
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.PermissionReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The permission summary the server derives reachability from (docs/domain.md, Device). A channel turned off in
 * system settings counts as a missing permission.
 */
class DevicePermissions
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : FullScreenPermission,
        PermissionReader,
        BatteryOptimization {
        override fun ignored(): Boolean =
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

        override fun granted(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        private fun exactAlarm(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        override fun read(): GrantedPermissions =
            GrantedPermissions(
                notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarm = exactAlarm(),
                fullScreen = granted(),
            )

        fun summary(): PermissionSummary {
            val notifications = NotificationManagerCompat.from(context)
            return PermissionSummary(
                notifications = notifications.areNotificationsEnabled(),
                exactAlarm = exactAlarm(),
                fullScreen = granted(),
                channels =
                    PermissionSummaryChannels(
                        pokeSilent = channelOn(notifications, NotificationChannels.POKE_SILENT),
                        pokeAlarm = channelOn(notifications, NotificationChannels.POKE_ALARM),
                    ),
            )
        }

        private fun channelOn(
            notifications: NotificationManagerCompat,
            id: String,
        ): Boolean =
            (notifications.getNotificationChannel(id)?.importance ?: NotificationManager.IMPORTANCE_NONE) !=
                NotificationManager.IMPORTANCE_NONE
    }
