package app.nuddle.alarm

import app.nuddle.data.db.ConnectionEntity
import app.nuddle.data.db.TaskEntity
import app.nuddle.ui.screens.poke.PokeContactUi

/**
 * Ara or Mesaj at on the full screen poke (docs/domain.md, Call or message from the poke; NUD-128): only when the task
 * has a contact and the phone has a number for that person as an active connection. The number comes from the
 * connection, never from the task text or the phone's contacts. A one-time message has none.
 */
internal fun pokeContact(
    task: TaskEntity,
    connection: ConnectionEntity?,
): PokeContactUi? {
    val personId = task.contactPersonId ?: return null
    val message =
        when (task.contactAction) {
            "call" -> false
            "message" -> true
            else -> return null
        }
    if (connection == null || connection.personId != personId || connection.status != "active") return null
    val phone = connection.phone?.takeIf { it.isNotBlank() } ?: return null
    return PokeContactUi(phone = phone, message = message)
}
