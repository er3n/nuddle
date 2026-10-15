package app.nuddle.ui.screens.profile

import app.nuddle.api.model.EdgeType
import app.nuddle.domain.GrantedPermissions
import app.nuddle.domain.SetupStep
import kotlinx.datetime.LocalTime

/** An active connection in "Bizimkiler": what they are to me (5a). */
data class PersonRowUi(
    val personId: String,
    val name: String,
    val relationship: EdgeType,
)

/** Profile and settings (5a). [failed] when a switch did not reach the server; it is shown as it was. */
data class ProfileUiState(
    val name: String,
    /** Display form, "+90 532 123 45 67". */
    val phone: String,
    val people: List<PersonRowUi>,
    /** Invites still open: the name I typed for them. */
    val pending: List<String>,
    val useConnectionsForMyTasks: Boolean,
    val notifyMeForOthersTasks: Boolean,
    val quietStart: LocalTime,
    val quietEnd: LocalTime,
    val permissions: GrantedPermissions,
    val failed: Boolean = false,
    val sheet: ProfileSheet? = null,
    /** I have a profile picture (NUD-72): the sheet offers to remove it. */
    val hasPicture: Boolean = false,
)

enum class QuietEdge { START, END }

/** The settings sheets on top of profile (Routes: `profile`). [busy] while it goes out, [failed] when it did not. */
sealed interface ProfileSheet {
    val busy: Boolean
    val failed: Boolean

    /** The avatar on top (NUD-72): Galeriden seç, and Fotoğrafı kaldır when there is one. */
    data class Picture(
        val hasPicture: Boolean,
        override val busy: Boolean = false,
        override val failed: Boolean = false,
    ) : ProfileSheet

    /** "Düzenle": the name (0e rename). */
    data class Rename(
        val name: String,
        override val busy: Boolean = false,
        override val failed: Boolean = false,
    ) : ProfileSheet

    /** 7g: the two boxes, the selected one is set with the picker under them. */
    data class Quiet(
        val start: LocalTime,
        val end: LocalTime,
        val editing: QuietEdge = QuietEdge.START,
        override val busy: Boolean = false,
        override val failed: Boolean = false,
    ) : ProfileSheet

    /** 7h. */
    data class LogOut(
        override val busy: Boolean = false,
        override val failed: Boolean = false,
    ) : ProfileSheet

    /** 7i. */
    data class Delete(
        override val busy: Boolean = false,
        override val failed: Boolean = false,
    ) : ProfileSheet
}

data class ProfileCallbacks(
    val onBack: () -> Unit = {},
    val onEditName: () -> Unit = {},
    val onPicture: () -> Unit = {},
    val onOpenPerson: (String) -> Unit = {},
    val onInvite: () -> Unit = {},
    val onUseConnections: (Boolean) -> Unit = {},
    val onNotifyMe: (Boolean) -> Unit = {},
    val onQuietHours: () -> Unit = {},
    val onFixPermission: (SetupStep) -> Unit = {},
    val onLogOut: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

data class ProfileSheetCallbacks(
    val onNameChange: (String) -> Unit = {},
    val onQuietEdge: (QuietEdge) -> Unit = {},
    val onQuietTime: (LocalTime) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    /** "Fotoğrafı kaldır" on the picture sheet (NUD-72). */
    val onRemovePicture: () -> Unit = {},
)
