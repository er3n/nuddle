package app.nuddle.push

import android.content.Context
import app.nuddle.BuildConfig
import app.nuddle.api.DevicesApi
import app.nuddle.api.model.RegisterDeviceRequest
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.session.SessionStore
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * `POST /api/v1/devices` with the FCM token and the permission summary; the returned `deviceId` goes back on sync
 * and `report_fired` (NUD-26). Sent on every launch and when the token changes (docs/domain.md, Device execution).
 */
@Singleton
class DeviceRegistrar
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: DevicesApi,
        private val sessions: SessionStore,
        private val permissions: DevicePermissions,
        private val json: Json,
    ) {
        /** Returns false when it should be tried again later. Without Firebase config (CI, fresh clone) it is skipped. */
        suspend fun register(token: String? = null): Boolean {
            if (sessions.current()?.registered != true) return true
            if (FirebaseApp.getApps(context).isEmpty()) return true
            val fcmToken = token ?: currentToken() ?: return false
            val request =
                RegisterDeviceRequest(
                    deviceId = sessions.deviceId(),
                    fcmToken = fcmToken,
                    platform = RegisterDeviceRequest.Platform.ANDROID,
                    appVersion = BuildConfig.VERSION_NAME,
                    permissions = permissions.summary(),
                )
            val result = apiCall(json) { api.registerDevice(request) }
            if (result is ApiResult.Success) sessions.saveDeviceId(result.value.deviceId)
            return result is ApiResult.Success
        }

        // Reason: Firebase deprecates registration tokens in favor of installation IDs; NUD-26 keeps tokens until the
        // server, the devices table and the contract move together.
        @Suppress("DEPRECATION")
        private suspend fun currentToken(): String? =
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging
                    .getInstance()
                    .token
                    .addOnCompleteListener { task -> continuation.resume(if (task.isSuccessful) task.result else null) }
            }
    }
