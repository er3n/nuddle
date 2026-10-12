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
    const val HOME_UNDO = "home.undo"
    const val HOME_DONE_NOTE = "home.doneNote"
    const val PREVIEW = "preview.sheet"
    const val PREVIEW_EVENING = "preview.evening"
    const val PREVIEW_MORNING = "preview.morning"
    const val PREVIEW_PICK_TIME = "preview.pickTime"
    const val SHEET_PRIMARY = "sheet.primary"
    const val SHEET_DISMISS = "sheet.dismiss"

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
}

/** Root of an activity's content: exposes the test tags as resource ids in the `e2e` flavor only. */
@Composable
fun TestTagsRoot(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.semantics { testTagsAsResourceId = BuildConfig.EXPOSE_TEST_TAGS }) { content() }
}
