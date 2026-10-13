package app.nuddle.data

import app.nuddle.api.CommandsApi
import app.nuddle.api.model.DeleteAccountCommand
import app.nuddle.api.model.EdgeType
import app.nuddle.api.model.LogOutCommand
import app.nuddle.api.model.Me
import app.nuddle.api.model.RemoveConnectionCommand
import app.nuddle.api.model.UpdateConnectionCommand
import app.nuddle.api.model.UpdateSettingsCommand
import app.nuddle.api.model.UpdateSettingsCommandQuietHours
import app.nuddle.data.api.ApiFailure
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.session.Profile
import app.nuddle.data.session.SessionStore
import app.nuddle.data.sync.Syncer
import app.nuddle.domain.IdGenerator
import app.nuddle.domain.QuietHours
import kotlinx.datetime.LocalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal fun Me.toProfile() =
    Profile(
        displayName = displayName,
        phone = phone,
        quietHours = QuietHours.parse(quietHours.start, quietHours.end, quietHours.timeZone),
        useConnectionsForMyTasks = useConnectionsForMyTasks,
        notifyMeForOthersTasks = notifyMeForOthersTasks,
    )

/** Everything this phone keeps for the signed-in person goes: alarms, notifications, queued work, the database. */
fun interface LocalWipe {
    suspend fun wipe()
}

/**
 * Profile and settings (5a, 5b, 7g, 7h, 7i). Like invites these go straight to the API, not through the outbox: the
 * screen says when one did not go (open-questions#37). The name uses [AuthRepository.register].
 */
interface AccountRepository {
    suspend fun setQuietHours(
        start: LocalTime,
        end: LocalTime,
    ): Boolean

    /** "Benim işlerimde tanıdıklarımı kullanabilirsin" */
    suspend fun setUseConnectionsForMyTasks(on: Boolean): Boolean

    /** "Başkalarının işleri için bana haber verilebilir" */
    suspend fun setNotifyMeForOthersTasks(on: Boolean): Boolean

    /** What [personId] is to me (5b, İlişki). */
    suspend fun setRelationship(
        personId: String,
        relationship: EdgeType,
    ): Boolean

    /** "Sibel'in işleri için bana haber verilebilir" (5b). */
    suspend fun setNotifyMe(
        personId: String,
        on: Boolean,
    ): Boolean

    /** Bağlantıyı kaldır (5b). Reuse [idempotencyKey] when retrying. */
    suspend fun removeConnection(
        personId: String,
        idempotencyKey: String,
    ): Boolean

    /** Çıkış yap (7h): this phone stops ringing; data stays on the server. */
    suspend fun logOut(idempotencyKey: String): Boolean

    /** Hesabımı sil (7i). */
    suspend fun deleteAccount(idempotencyKey: String): Boolean
}

class ApiAccountRepository
    @Inject
    constructor(
        private val commandsApi: CommandsApi,
        private val sessions: SessionStore,
        private val sync: Syncer,
        private val wipe: LocalWipe,
        private val ids: IdGenerator,
        private val json: Json,
    ) : AccountRepository {
        override suspend fun setQuietHours(
            start: LocalTime,
            end: LocalTime,
        ): Boolean = settings(UpdateSettingsCommand(ids.next(), quietHours = UpdateSettingsCommandQuietHours(start.hhmm(), end.hhmm())))

        override suspend fun setUseConnectionsForMyTasks(on: Boolean): Boolean =
            settings(UpdateSettingsCommand(ids.next(), useConnectionsForMyTasks = on))

        override suspend fun setNotifyMeForOthersTasks(on: Boolean): Boolean =
            settings(UpdateSettingsCommand(ids.next(), notifyMeForOthersTasks = on))

        private suspend fun settings(command: UpdateSettingsCommand): Boolean {
            val result = apiCall(json) { commandsApi.updateSettings(command) }
            if (result !is ApiResult.Success) return false
            sessions.saveMe(result.value.toProfile())
            // Plans that depend on the setting changed on the server.
            sync.sync()
            return true
        }

        override suspend fun setRelationship(
            personId: String,
            relationship: EdgeType,
        ): Boolean = connection(UpdateConnectionCommand(ids.next(), personId, relationship = relationship))

        override suspend fun setNotifyMe(
            personId: String,
            on: Boolean,
        ): Boolean = connection(UpdateConnectionCommand(ids.next(), personId, notifyMe = on))

        private suspend fun connection(command: UpdateConnectionCommand): Boolean {
            val result = apiCall(json) { commandsApi.updateConnection(command) }
            if (result !is ApiResult.Success) return false
            sync.sync()
            return true
        }

        override suspend fun removeConnection(
            personId: String,
            idempotencyKey: String,
        ): Boolean {
            val result = apiCall(json) { commandsApi.removeConnection(RemoveConnectionCommand(idempotencyKey, personId)) }
            // Already gone (removed from the other side, or by an earlier try): the same end state.
            val gone = (result as? ApiResult.Failure)?.failure.let { it is ApiFailure.Http && it.name == NOT_CONNECTED }
            if (result !is ApiResult.Success && !gone) return false
            sync.sync()
            return true
        }

        override suspend fun logOut(idempotencyKey: String): Boolean {
            val deviceId = sessions.deviceId()
            if (deviceId != null) {
                val result = apiCall(json) { commandsApi.logOut(LogOutCommand(idempotencyKey, deviceId)) }
                if (result !is ApiResult.Success) return false
            }
            wipe.wipe()
            return true
        }

        override suspend fun deleteAccount(idempotencyKey: String): Boolean {
            val result = apiCall(json) { commandsApi.deleteAccount(DeleteAccountCommand(idempotencyKey)) }
            if (result !is ApiResult.Success) return false
            wipe.wipe()
            return true
        }

        private companion object {
            const val NOT_CONNECTED = "not_connected"
            val HHMM =
                LocalTime.Format {
                    hour()
                    char(':')
                    minute()
                }

            fun LocalTime.hhmm(): String = format(HHMM)
        }
    }
