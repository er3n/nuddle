package app.nuddle.di

import app.nuddle.api.InvitesApi
import app.nuddle.data.ApiInvitePreviews
import app.nuddle.data.InvitePreviews
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

/** The invite preview, read before sign-in without a token (0b, 0e; ADR 0016). */
@Module
@InstallIn(SingletonComponent::class)
abstract class InvitePreviewModule {
    @Binds
    abstract fun invitePreviews(impl: ApiInvitePreviews): InvitePreviews

    companion object {
        @Provides
        @Singleton
        fun invitesApi(
            @Named("public") retrofit: Retrofit,
        ): InvitesApi = retrofit.create(InvitesApi::class.java)
    }
}
