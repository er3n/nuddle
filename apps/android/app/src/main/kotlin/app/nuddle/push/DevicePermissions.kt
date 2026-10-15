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
import app.nuddle.data.DeviceBrand
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.PermissionReader
import app.nuddle.domain.SetupRules
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
        private val brand: DeviceBrand,
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
                // Only where the phone puts apps to sleep; stock Android keeps three steps (NUD-75).
                battery = if (SetupRules.needsBatteryStep(brand.manufacturer())) ignored() else null,
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
