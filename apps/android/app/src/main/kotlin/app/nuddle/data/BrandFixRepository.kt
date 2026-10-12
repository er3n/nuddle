package app.nuddle.data

import app.nuddle.api.DevicesApi
import app.nuddle.api.model.BrandFix
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** `Build.MANUFACTURER`, as the server's brand steps are keyed by it. */
fun interface DeviceBrand {
    fun manufacturer(): String
}

/** The settings that keep this phone's brand from killing the app (2h). They come from the server, not the app. */
fun interface BrandFixRepository {
    /** Null when they could not be loaded. */
    suspend fun forThisPhone(): BrandFix?
}

class ApiBrandFixRepository
    @Inject
    constructor(
        private val devicesApi: DevicesApi,
        private val brand: DeviceBrand,
        private val json: Json,
    ) : BrandFixRepository {
        override suspend fun forThisPhone(): BrandFix? =
            (apiCall(json) { devicesApi.brandFix(brand.manufacturer()) } as? ApiResult.Success)?.value
    }

/** The phone does not hold the app back for battery (`PowerManager.isIgnoringBatteryOptimizations`). */
fun interface BatteryOptimization {
    fun ignored(): Boolean
}
