package app.nuddle.push

import android.content.Context
import app.nuddle.BuildConfig
import app.nuddle.api.DevicesApi
import app.nuddle.api.model.RegisterDeviceRequest
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.session.SessionStore
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * `POST /api/v1/devices` with the Firebase Installation ID and the permission summary; the returned `deviceId` goes
 * back on sync and `report_fired`. Sent on every launch and when FCM reports a new registration (docs/domain.md,
 * Device execution; ADR 0015).
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
        suspend fun register(fid: String? = null): Boolean {
            if (sessions.current()?.registered != true) return true
            if (FirebaseApp.getApps(context).isEmpty()) return true
            val installationId = fid ?: currentFid() ?: return false
            val request =
                RegisterDeviceRequest(
                    deviceId = sessions.deviceId(),
                    fid = installationId,
                    platform = RegisterDeviceRequest.Platform.ANDROID,
                    appVersion = BuildConfig.VERSION_NAME,
                    permissions = permissions.summary(),
                )
            val result = apiCall(json) { api.registerDevice(request) }
            if (result is ApiResult.Success) sessions.saveDeviceId(result.value.deviceId)
            return result is ApiResult.Success
        }

        /** Registers this install with FCM, then reads its FID. Null when either step fails. */
        private suspend fun currentFid(): String? {
            if (await(FirebaseMessaging.getInstance().register()).isFailure) return null
            return await(FirebaseInstallations.getInstance().id).getOrNull()
        }

        private suspend fun <T> await(task: Task<T>): Result<T> =
            suspendCancellableCoroutine { continuation ->
                task.addOnCompleteListener {
                    val result =
                        if (it.isSuccessful) {
                            Result.success(it.result)
                        } else {
                            Result.failure(it.exception ?: IllegalStateException("task failed"))
                        }
                    continuation.resume(result)
                }
            }
    }
