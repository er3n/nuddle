package app.nuddle.ui.screens.person

import app.nuddle.api.model.EdgeType
import app.nuddle.domain.ConnectedFor

/** 5b. [since] is null when the server did not say. [tasks] counts every task between us, both ways. */
data class PersonUiState(
    val personId: String,
    val name: String,
    val relationship: EdgeType,
    val since: ConnectedFor?,
    val notifyMe: Boolean,
    val tasks: Int,
    val failed: Boolean = false,
    val sheet: PersonSheet? = null,
)

/** The sheets of 5b. [busy] while it goes out, [failed] when it did not. */
sealed interface PersonSheet {
    val busy: Boolean
    val failed: Boolean

    /** "İlişki": the 1e chips, what they are to me. */
    data class Relationship(
        val selected: EdgeType,
        override val busy: Boolean = false,
        override val failed: Boolean = false,
    ) : PersonSheet

    /** "Bağlantıyı kaldır" asks first (open-questions#37). */
    data class Remove(
        override val busy: Boolean = false,
        override val failed: Boolean = false,
    ) : PersonSheet
}

data class PersonCallbacks(
    val onBack: () -> Unit = {},
    val onRelationship: () -> Unit = {},
    val onNotifyMe: (Boolean) -> Unit = {},
    val onRemove: () -> Unit = {},
    val onSelect: (EdgeType) -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onDismiss: () -> Unit = {},
)
