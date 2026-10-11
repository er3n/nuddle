package app.nuddle.di

import app.nuddle.BuildConfig
import app.nuddle.api.AuthApi
import app.nuddle.api.CommandsApi
import app.nuddle.api.DevicesApi
import app.nuddle.api.SyncApi
import app.nuddle.data.api.AuthInterceptor
import app.nuddle.data.api.RawCommandApi
import app.nuddle.data.api.TokenAuthenticator
import app.nuddle.data.session.SessionStore
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/** Our API only (ADR 0002): the generated client (ADR 0008) on Retrofit, base URL per flavor. */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val TIMEOUT_SECONDS = 20L

    private fun retrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private fun baseClient(): OkHttpClient.Builder =
        OkHttpClient
            .Builder()
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

    /** Sign-in calls carry no token and never trigger a refresh. */
    @Provides
    @Singleton
    @Named("public")
    fun publicRetrofit(json: Json): Retrofit = retrofit(baseClient().build(), json)

    @Provides
    @Singleton
    fun authApi(
        @Named("public") retrofit: Retrofit,
    ): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun authenticatedRetrofit(
        json: Json,
        sessions: SessionStore,
        authApi: Lazy<AuthApi>,
    ): Retrofit {
        val client =
            baseClient()
                .addInterceptor(AuthInterceptor(sessions))
                .authenticator(TokenAuthenticator(sessions) { authApi.get() })
                .build()
        return retrofit(client, json)
    }

    @Provides
    @Singleton
    fun syncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)

    @Provides
    @Singleton
    fun devicesApi(retrofit: Retrofit): DevicesApi = retrofit.create(DevicesApi::class.java)

    @Provides
    @Singleton
    fun commandsApi(retrofit: Retrofit): CommandsApi = retrofit.create(CommandsApi::class.java)

    @Provides
    @Singleton
    fun rawCommandApi(retrofit: Retrofit): RawCommandApi = retrofit.create(RawCommandApi::class.java)
}
