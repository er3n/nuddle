package app.nuddle.data.avatar

import android.graphics.BitmapFactory
import android.net.Uri
import app.nuddle.api.CommandsApi
import app.nuddle.api.model.SetAvatarCommand
import app.nuddle.data.api.ApiResult
import app.nuddle.data.api.apiCall
import app.nuddle.data.photo.GalleryImages
import app.nuddle.data.photo.toJpeg
import app.nuddle.data.session.SessionStore
import app.nuddle.data.toProfile
import app.nuddle.domain.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Longest side of a profile picture: an avatar, not a photo. */
private const val AVATAR_MAX_SIDE = 512

/** My profile picture on Profile (5a): picked from the gallery, or removed (NUD-72). */
interface AvatarRepository {
    /** Shown at once and uploaded when there is a network; false when the picture could not be read. */
    suspend fun pick(uri: Uri): Boolean

    /** "Fotoğrafı kaldır": the initial again, on the server too; false when that did not reach it. */
    suspend fun remove(): Boolean
}

class ApiAvatarRepository
    @Inject
    constructor(
        private val gallery: GalleryImages,
        private val files: AvatarFiles,
        private val uploads: AvatarUploads,
        private val commands: CommandsApi,
        private val sessions: SessionStore,
        private val ids: IdGenerator,
        private val json: Json,
    ) : AvatarRepository {
        override suspend fun pick(uri: Uri): Boolean {
            val picture = gallery.read(uri) ?: return false
            val small =
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(picture, 0, picture.size)?.toJpeg(maxSide = AVATAR_MAX_SIDE)
                } ?: return false
            withContext(Dispatchers.IO) {
                files.savePending(small)
                // Mine on screen right away; the upload replaces the path once the server has it.
                files.save(ME, PENDING_PATH, small)
            }
            uploads.uploadSoon()
            return true
        }

        override suspend fun remove(): Boolean {
            val result = apiCall(json) { commands.setAvatar(SetAvatarCommand(ids.next(), null)) }
            if (result !is ApiResult.Success) return false
            withContext(Dispatchers.IO) {
                files.deletePending()
                files.delete(ME)
            }
            sessions.saveMe(result.value.toProfile())
            return true
        }

        private companion object {
            /** The path a picked picture is kept under until the upload gives it a real one. */
            const val PENDING_PATH = "pending"
        }
    }
