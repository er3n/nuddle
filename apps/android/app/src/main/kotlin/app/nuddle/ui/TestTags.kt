package app.nuddle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import app.nuddle.BuildConfig

/**
 * Test tags of every interactive element (docs/testing.md, Android `e2e` flavor). Compose UI tests find nodes by
 * them; in the `e2e` flavor they are also exposed as resource ids, which is how Maestro flows in `e2e/flows` find
 * them. A tag is a contract with those flows: rename it there too.
 */
object TestTags {
    const val BACK = "bar.back"
    const val BELL = "bar.bell"
    const val PROFILE = "bar.profile"

    const val WELCOME_START = "welcome.start"
    const val WELCOME_TERMS = "welcome.terms"

    const val PHONE_INPUT = "phone.input"
    const val PHONE_SEND = "phone.send"

    const val CODE_INPUT = "code.input"
    const val CODE_CHANGE_NUMBER = "code.changeNumber"
    const val CODE_RESEND = "code.resend"

    const val NAME_INPUT = "name.input"
    const val NAME_CONTINUE = "name.continue"

    const val HOME = "home.screen"
    const val HOME_INVITE = "home.invite"
    const val HOME_NEXT_UP = "home.nextUp"
    const val HOME_DONE_EARLY = "home.doneEarly"
    const val HOME_CALL = "home.call"
    const val HOME_SENT = "home.sent"
    const val HOME_QUEUED = "home.queued"
    const val HOME_OFFLINE = "home.offline"
    const val HOME_SEND_FAILED = "home.sendFailed"
    const val INVALID_INVITE = "invalidInvite.screen"
    const val INVALID_INVITE_HOME = "invalidInvite.home"
    const val GENERIC_ERROR = "genericError.screen"
    const val GENERIC_ERROR_RETRY = "genericError.retry"
    const val GENERIC_ERROR_HOME = "genericError.home"
    const val TASK_DETAIL = "task.screen"
    const val PHOTO = "photo.screen"
    const val PHOTO_CLOSE = "photo.close"
    const val PHOTO_SHOOT = "photo.shoot"
    const val PHOTO_SEND = "photo.send"
    const val PHOTO_RETAKE = "photo.retake"
    const val PHOTO_DONE_WITHOUT = "photo.doneWithout"
    const val HOME_TAKE_PHOTO = "home.takePhoto"
    const val APPROVE = "approve.screen"
    const val HOME_UNSENT_PHOTO = "home.unsentPhoto"
    const val UNSENT_PHOTO = "unsent.screen"
    const val UNSENT_RETRY = "unsent.retry"
    const val APPROVE_NOTE = "approve.note"
    const val APPROVE_OK = "approve.ok"
    const val APPROVE_REREQUEST = "approve.reRequest"
    const val APPROVE_RETRY_PHOTO = "approve.retryPhoto"
    const val TASK_CANCEL = "task.cancel"
    const val TASK_EDIT_DEADLINE = "task.editDeadline"
    const val TASK_EDIT_FINISH = "task.editFinish"

    fun cancelReason(name: String) = "task.cancelReason.$name"

    fun bellEntry(taskId: String) = "inbox.entry.$taskId"

    fun bellPermission(step: String) = "inbox.permission.$step"

    /** "Düzelt" on the warning card tagged [warning]. */
    fun bellFix(warning: String) = "$warning.fix"

    fun brandStep(id: String) = "missedRing.step.$id"

    const val PREVIEW_FINISH_DONE = "preview.finishDone"
    const val PREVIEW_FINISH_PHOTO = "preview.finishPhoto"

    /** null is "Kendine". */
    fun previewRecipient(personId: String?) = "preview.recipient.${personId ?: "self"}"

    const val HOME_MESSAGE = "home.message"
    const val HOME_UNDO = "home.undo"
    const val HOME_DONE_NOTE = "home.doneNote"
    const val HOME_PENDING_INVITE = "home.pendingInvite"
    const val HOME_INVITE_RESHARE = "home.inviteReshare"
    const val HOME_INVITE_REVOKE = "home.inviteRevoke"
    const val PREVIEW = "preview.sheet"
    const val PREVIEW_EVENING = "preview.evening"
    const val PREVIEW_MORNING = "preview.morning"
    const val PREVIEW_PICK_TIME = "preview.pickTime"
    const val SHEET_PRIMARY = "sheet.primary"
    const val SHEET_DISMISS = "sheet.dismiss"

    const val INVITE = "invite.screen"
    const val INVITE_PICK_CONTACT = "invite.pickContact"
    const val INVITE_TYPE_NUMBER = "invite.typeNumber"
    const val INVITE_NAME = "invite.name"
    const val INVITE_NUMBER = "invite.number"
    const val INVITE_SEND = "invite.send"

    /** Followed by the relationship value, e.g. "invite.relationship.spouse". */
    const val INVITE_RELATIONSHIP = "invite.relationship."

    const val CONSENT = "consent.screen"
    const val CONSENT_ALLOW = "consent.allow"
    const val CONSENT_LATER = "consent.later"
    const val CONSENT_DECLINE = "consent.decline"
    const val CONSENT_CHANGE = "consent.change"
    const val MATCHED = "matched.screen"
    const val MATCHED_START = "matched.start"
    const val INBOX = "inbox.screen"
    const val INBOX_OPEN_INVITE = "inbox.openInvite"
    const val BELL_MISSED_RING = "inbox.missedRing"
    const val MISSED_RING_SCREEN = "missedRing.screen"
    const val MISSED_RING_OPEN = "missedRing.open"

    const val SETUP = "setup.screen"
    const val SETUP_TRY = "setup.try"
    const val SETUP_SKIP_DEMO = "setup.skipDemo"
    const val SETUP_DEMO = "setup.demo"
    const val SETUP_DEMO_OK = "setup.demoOk"
    const val SETUP_STEPS = "setup.steps"
    const val SETUP_OPEN_ACTIVE = "setup.openActive"
    const val SETUP_SKIP_STEP = "setup.skipStep"

    const val COMPOSE_INPUT = "compose.input"
    const val COMPOSE_SEND = "compose.send"
    const val COMPOSE_MIC = "compose.mic"
    const val COMPOSE_RETRY = "compose.retry"

    const val POKE = "poke.screen"
    const val POKE_DONE = "poke.done"
    const val POKE_SNOOZE = "poke.snooze"
    const val POKE_CANT_TODAY = "poke.cantToday"
    const val DROP_NOTE = "drop.note"
    const val ISSUE_DID_IT_LATER = "issue.didItLater"
    const val ISSUE_CALL = "issue.call"
    const val ISSUE_NEW_TIME = "issue.newTime"
    const val ISSUE_I_HANDLE = "issue.iHandle"
    const val ISSUE_CANCEL = "issue.cancel"
    const val ISSUE_CANCELLED = "issue.cancelled"

    const val PROFILE_SCREEN = "profile.screen"
    const val PROFILE_EDIT = "profile.edit"
    const val PROFILE_INVITE = "profile.invite"
    const val PROFILE_USE_CONNECTIONS = "profile.useConnections"
    const val PROFILE_NOTIFY_ME = "profile.notifyMe"
    const val PROFILE_QUIET = "profile.quiet"
    const val PROFILE_LOG_OUT = "profile.logOut"
    const val PROFILE_DELETE = "profile.delete"
    const val QUIET_START = "quiet.start"
    const val QUIET_END = "quiet.end"
    const val RENAME_INPUT = "rename.input"
    const val PERSON_SCREEN = "person.screen"
    const val PERSON_RELATIONSHIP = "person.relationship"
    const val PERSON_NOTIFY_ME = "person.notifyMe"
    const val PERSON_REMOVE = "person.remove"

    fun dropReason(name: String) = "drop.reason.$name"

    fun profilePerson(name: String) = "profile.person.$name"

    fun profilePermission(step: String) = "profile.permission.$step"

    fun personRelationship(type: String) = "person.relationship.$type"
}

/** Root of an activity's content: exposes the test tags as resource ids in the `e2e` flavor only. */
@Composable
fun TestTagsRoot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.semantics { testTagsAsResourceId = BuildConfig.EXPOSE_TEST_TAGS }) { content() }
}
