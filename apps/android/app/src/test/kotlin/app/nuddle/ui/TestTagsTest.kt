package app.nuddle.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.nuddle.api.model.EdgeType
import app.nuddle.api.model.Reachability
import app.nuddle.domain.Ago
import app.nuddle.domain.SetupStep
import app.nuddle.domain.TimeLeft
import app.nuddle.testing.Cast
import app.nuddle.ui.components.ComposeState
import app.nuddle.ui.screens.approve.ApproveCallbacks
import app.nuddle.ui.screens.approve.ApproveScreen
import app.nuddle.ui.screens.approve.ApproveUiState
import app.nuddle.ui.screens.approve.PhotoView
import app.nuddle.ui.screens.code.CodeScreen
import app.nuddle.ui.screens.code.CodeUiState
import app.nuddle.ui.screens.consent.ConsentCallbacks
import app.nuddle.ui.screens.consent.ConsentScreen
import app.nuddle.ui.screens.consent.ConsentUiState
import app.nuddle.ui.screens.consent.MatchedScreen
import app.nuddle.ui.screens.home.DeadlineChoice
import app.nuddle.ui.screens.home.DeadlineText
import app.nuddle.ui.screens.home.FromUi
import app.nuddle.ui.screens.home.HomeScreen
import app.nuddle.ui.screens.home.HomeUiState
import app.nuddle.ui.screens.home.NextUpCallbacks
import app.nuddle.ui.screens.home.NextUpStatus
import app.nuddle.ui.screens.home.NextUpUi
import app.nuddle.ui.screens.home.PendingInviteCallbacks
import app.nuddle.ui.screens.home.PendingInviteUi
import app.nuddle.ui.screens.home.PreviewCallbacks
import app.nuddle.ui.screens.home.PreviewUi
import app.nuddle.ui.screens.home.SentUi
import app.nuddle.ui.screens.home.WrittenText
import app.nuddle.ui.screens.inbox.InboxInviteUi
import app.nuddle.ui.screens.inbox.InboxScreen
import app.nuddle.ui.screens.inbox.InboxUiState
import app.nuddle.ui.screens.invite.InviteCallbacks
import app.nuddle.ui.screens.invite.InviteScreen
import app.nuddle.ui.screens.invite.InviteUiState
import app.nuddle.ui.screens.invite.Invitee
import app.nuddle.ui.screens.name.NameScreen
import app.nuddle.ui.screens.name.NameUiState
import app.nuddle.ui.screens.phone.PhoneScreen
import app.nuddle.ui.screens.phone.PhoneUiState
import app.nuddle.ui.screens.photo.PhotoCallbacks
import app.nuddle.ui.screens.photo.PhotoScreen
import app.nuddle.ui.screens.photo.PhotoStage
import app.nuddle.ui.screens.photo.PhotoUiState
import app.nuddle.ui.screens.photo.UnsentPhotoScreen
import app.nuddle.ui.screens.photo.UnsentPhotoUiState
import app.nuddle.ui.screens.poke.PokeDemoScreen
import app.nuddle.ui.screens.poke.PokeScreen
import app.nuddle.ui.screens.poke.PokeUiState
import app.nuddle.ui.screens.poke.SnoozeUi
import app.nuddle.ui.screens.setup.PermissionStepUi
import app.nuddle.ui.screens.setup.PermissionsScreen
import app.nuddle.ui.screens.setup.SetupDemoScreen
import app.nuddle.ui.screens.setup.SetupUiState
import app.nuddle.ui.screens.task.TaskDetailCallbacks
import app.nuddle.ui.screens.task.TaskDetailScreen
import app.nuddle.ui.screens.task.TaskDetailUiState
import app.nuddle.ui.screens.welcome.WelcomeScreen
import app.nuddle.ui.screens.welcome.WelcomeUiState
import app.nuddle.ui.theme.DurtTheme
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Every interactive element carries its test tag (docs/testing.md, Android `e2e` flavor): the E2E flows in
 * `e2e/orchestrator/src/flows.ts` find elements only by these tags, so a lost tag breaks E2E without failing anything else.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class TestTagsTest {
    @get:Rule val compose = createComposeRule()

    private val clicked = mutableListOf<String>()

    private fun content(block: @Composable () -> Unit) {
        compose.setContent { DurtTheme(block) }
    }

    /** Each tag is displayed, clickable, and its click reaches the callback that records `name`. */
    private fun assertTaps(vararg tags: Pair<String, String>) {
        for ((tag, name) in tags) {
            compose
                .onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
                .performClick()
            assertEquals(name, clicked.lastOrNull(), "tag $tag")
        }
    }

    private fun assertShown(vararg tags: String) {
        for (tag in tags) compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun welcome() {
        content { WelcomeScreen(WelcomeUiState(), onStart = { clicked += "start" }, onTerms = {}, onPrivacy = {}) }
        assertShown(TestTags.WELCOME_TERMS)
        assertTaps(TestTags.WELCOME_START to "start")
    }

    @Test
    fun invitedWelcome() {
        content { WelcomeScreen(WelcomeUiState(inviterName = "Sibel"), onStart = {}, onTerms = {}, onPrivacy = {}) }
        assertShown(TestTags.WELCOME_INVITER)
    }

    @Test
    fun phone() {
        content {
            PhoneScreen(
                PhoneUiState(national = "5320000001"),
                onNumberChange = {},
                onSend = { clicked += "send" },
                onBack = { clicked += "back" },
            )
        }
        assertShown(TestTags.PHONE_INPUT)
        assertTaps(TestTags.PHONE_SEND to "send", TestTags.BACK to "back")
    }

    @Test
    fun code() {
        content {
            CodeScreen(
                CodeUiState("+90 532 000 00 01"),
                onCodeChange = {},
                onComplete = {},
                onResend = { clicked += "resend" },
                onChangeNumber = { clicked += "change" },
                onBack = { clicked += "back" },
            )
        }
        assertShown(TestTags.CODE_INPUT)
        assertTaps(TestTags.CODE_CHANGE_NUMBER to "change", TestTags.CODE_RESEND to "resend", TestTags.BACK to "back")
    }

    @Test
    fun `code after a wrong code`() {
        content {
            CodeScreen(
                CodeUiState("+90 532 000 00 01", code = "000000", wrongCode = true),
                onCodeChange = {},
                onComplete = {},
                onResend = { clicked += "resend" },
                onChangeNumber = {},
                onBack = {},
            )
        }
        assertTaps(TestTags.CODE_RESEND to "resend")
    }

    @Test
    fun name() {
        content { NameScreen(NameUiState(name = "Sibel"), onNameChange = {}, onContinue = { clicked += "continue" }) }
        assertShown(TestTags.NAME_INPUT)
        assertTaps(TestTags.NAME_CONTINUE to "continue")
    }

    @Test
    fun home() {
        homeWith(ComposeState.Idle)
        assertShown(TestTags.HOME, TestTags.COMPOSE_INPUT, TestTags.COMPOSE_MIC)
        assertTaps(TestTags.BELL to "bell", TestTags.PROFILE to "profile", TestTags.HOME_INVITE to "invite")
    }

    @Test
    fun `home with text`() {
        homeWith(ComposeState.Typing("çöpü at"))
        assertTaps(TestTags.COMPOSE_SEND to "send")
    }

    @Test
    fun `home after a failed send`() {
        homeWith(ComposeState.Failed("çöpü at"))
        assertTaps(TestTags.COMPOSE_RETRY to "send")
    }

    @Test
    fun poke() {
        content {
            PokeScreen(
                PokeUiState(
                    pokeNumber = 1,
                    time = "08:15",
                    title = "Para çek",
                    detail = null,
                    timeLeft = TimeLeft.Minutes(45),
                    snooze = SnoozeUi(time = "08:25", last = false),
                ),
                onDone = { clicked += "done" },
                onSnooze = { clicked += "snooze" },
                onCantToday = { clicked += "cant" },
            )
        }
        assertShown(TestTags.POKE)
        assertTaps(TestTags.POKE_DONE to "done", TestTags.POKE_SNOOZE to "snooze", TestTags.POKE_CANT_TODAY to "cant")
    }

    @Test
    fun `home with a task next up`() {
        content {
            HomeScreen(
                state =
                    HomeUiState(
                        myName = "Eren",
                        nextUp = NextUpUi("task-1", "Para çek", "1.000 TL", DeadlineText.Tomorrow("09:00"), photo = false),
                    ),
                onBell = { clicked += "bell" },
                onProfile = { clicked += "profile" },
                onInvite = { clicked += "invite" },
                onComposeTextChange = {},
                onComposeSend = { clicked += "send" },
                onVoiceStart = {},
                onVoiceEnd = {},
                nextUpActions = NextUpCallbacks(onDone = { clicked += "doneEarly" }),
            )
        }
        assertShown(TestTags.HOME_NEXT_UP)
        assertTaps(TestTags.HOME_DONE_EARLY to "doneEarly", TestTags.HOME_INVITE to "invite")
    }

    @Test
    fun `home with a task I sent`() {
        val sent = SentUi("task-1", "Para çek", null, DeadlineText.Tomorrow("09:00"), photo = false, toId = Cast.SIBEL_ID, toName = "Sibel")
        content {
            HomeScreen(
                state = HomeUiState(myName = "Eren", connected = true, sent = listOf(sent)),
                onBell = {},
                onProfile = {},
                onInvite = {},
                onComposeTextChange = {},
                onComposeSend = {},
                onVoiceStart = {},
                onVoiceEnd = {},
                onOpenTask = { taskId, approval -> clicked += "open $taskId $approval" },
            )
        }
        assertTaps(TestTags.HOME_SENT to "open task-1 false")
    }

    @Test
    fun `task detail with the cancel sheet`() {
        val state =
            TaskDetailUiState(
                myName = "Eren",
                title = "Para çek",
                rawText = null,
                toName = "Sibel",
                reachability = Reachability.SOUND,
                deadline = DeadlineText.Tomorrow("09:00"),
                detail = null,
                photo = false,
            )
        content {
            TaskDetailScreen(
                state = state,
                callbacks = TaskDetailCallbacks(onBack = { clicked += "back" }, onCancel = { clicked += "cancel" }),
            )
        }
        assertShown(TestTags.TASK_DETAIL)
        assertTaps(TestTags.TASK_CANCEL to "cancel")
    }

    @Test
    fun `photo, checking the shot`() {
        val state = PhotoUiState("Para çek", "Sibel", null, stage = PhotoStage.Review(ImageBitmap(3, 4)))
        content {
            PhotoScreen(
                state,
                PhotoCallbacks(onClose = { clicked += "close" }, onSend = { clicked += "send" }, onRetake = { clicked += "retake" }),
                viewfinder = {},
            )
        }
        assertShown(TestTags.PHOTO)
        assertTaps(TestTags.PHOTO_SEND to "send", TestTags.PHOTO_RETAKE to "retake", TestTags.PHOTO_CLOSE to "close")
    }

    @Test
    fun `photo, camera and no camera`() {
        var stage: PhotoStage by mutableStateOf(PhotoStage.Camera)
        content {
            PhotoScreen(
                PhotoUiState("Para çek", null, null, stage = stage),
                PhotoCallbacks(onShoot = { clicked += "shoot" }, onDoneWithoutPhoto = { clicked += "without" }),
                viewfinder = {},
            )
        }
        assertTaps(TestTags.PHOTO_SHOOT to "shoot")
        stage = PhotoStage.NoCamera
        assertTaps(TestTags.PHOTO_DONE_WITHOUT to "without")
    }

    @Test
    fun `home with a photo task opens the camera`() {
        content {
            HomeScreen(
                state =
                    HomeUiState(
                        myName = "Eren",
                        nextUp = NextUpUi("task-1", "Para çek", "1.000 TL", DeadlineText.Tomorrow("09:00"), photo = true),
                    ),
                onBell = {},
                onProfile = {},
                onInvite = {},
                onComposeTextChange = {},
                onComposeSend = {},
                onVoiceStart = {},
                onVoiceEnd = {},
                nextUpActions = NextUpCallbacks(onTakePhoto = { clicked += "photo $it" }),
            )
        }
        assertTaps(TestTags.HOME_TAKE_PHOTO to "photo task-1")
    }

    @Test
    fun `approval of a photo`() {
        content {
            ApproveScreen(
                ApproveUiState("Para çek", "Eren", "08:24", null, photo = PhotoView.Failed),
                ApproveCallbacks(
                    onApprove = { clicked += "approve" },
                    onReRequest = { clicked += "again" },
                    onRetryPhoto = { clicked += "retry" },
                ),
            )
        }
        assertShown(TestTags.APPROVE, TestTags.APPROVE_NOTE)
        assertTaps(TestTags.APPROVE_RETRY_PHOTO to "retry", TestTags.APPROVE_REREQUEST to "again", TestTags.APPROVE_OK to "approve")
    }

    @Test
    fun `photo upload failed`() {
        content { UnsentPhotoScreen(UnsentPhotoUiState("Sibel", null), onBack = { clicked += "back" }, onRetry = { clicked += "retry" }) }
        assertTaps(TestTags.UNSENT_RETRY to "retry")
    }

    @Test
    fun `home with a photo that did not go out`() {
        content {
            HomeScreen(
                state =
                    HomeUiState(
                        myName = "Eren",
                        connected = true,
                        nextUp =
                            NextUpUi(
                                "task-1",
                                "Para çek",
                                null,
                                DeadlineText.Today("09:00"),
                                photo = true,
                                from = FromUi("Sibel", WrittenText.Today("08:00"), null),
                                status = NextUpStatus.AwaitingApproval,
                                uploadFailed = true,
                            ),
                    ),
                onBell = {},
                onProfile = {},
                onInvite = {},
                onComposeTextChange = {},
                onComposeSend = {},
                onVoiceStart = {},
                onVoiceEnd = {},
                nextUpActions = NextUpCallbacks(onOpenUnsentPhoto = { clicked += "unsent $it" }),
            )
        }
        assertTaps(TestTags.HOME_UNSENT_PHOTO to "unsent task-1")
    }

    @Test
    fun `home with a task from Sibel`() {
        val from = FromUi("Sibel", WrittenText.Today("14:32"), Cast.SIBEL_PHONE)
        content {
            HomeScreen(
                state =
                    HomeUiState(
                        myName = "Eren",
                        nextUp = NextUpUi("task-1", "Gelirken ekmek al", null, DeadlineText.Today("19:00"), photo = false, from = from),
                    ),
                onBell = {},
                onProfile = {},
                onInvite = {},
                onComposeTextChange = {},
                onComposeSend = {},
                onVoiceStart = {},
                onVoiceEnd = {},
                nextUpActions =
                    NextUpCallbacks(
                        onDone = { clicked += "doneEarly" },
                        onCall = { clicked += "call $it" },
                        onMessage = { clicked += "message $it" },
                    ),
            )
        }
        assertShown(TestTags.HOME_NEXT_UP)
        assertTaps(
            TestTags.HOME_DONE_EARLY to "doneEarly",
            TestTags.HOME_CALL to "call ${Cast.SIBEL_PHONE}",
            TestTags.HOME_MESSAGE to "message ${Cast.SIBEL_PHONE}",
        )
    }

    @Test
    fun `preview sheet with a deadline chosen`() {
        val morning = DeadlineChoice.TomorrowMorning(Instant.parse("2026-10-12T09:00:00+03:00"))
        content {
            HomeScreen(
                state =
                    HomeUiState(
                        myName = "Eren",
                        preview = PreviewUi("1000 tl çek", "Para çek", "1.000 TL", listOf(morning), morning, "23:00", "07:00"),
                    ),
                onBell = {},
                onProfile = {},
                onInvite = {},
                onComposeTextChange = {},
                onComposeSend = {},
                onVoiceStart = {},
                onVoiceEnd = {},
                preview =
                    PreviewCallbacks(
                        onChoice = { clicked += "choice" },
                        onConfirm = { clicked += "confirm" },
                        onDismiss = { clicked += "dismiss" },
                    ),
            )
        }
        assertShown(TestTags.PREVIEW, TestTags.PREVIEW_PICK_TIME)
        assertTaps(TestTags.PREVIEW_MORNING to "choice", TestTags.SHEET_PRIMARY to "confirm", TestTags.SHEET_DISMISS to "dismiss")
    }

    @Test
    fun `invite, nothing chosen`() {
        content {
            InviteScreen(
                InviteUiState(),
                InviteCallbacks(
                    onBack = { clicked += "back" },
                    onPickContact = { clicked += "pick" },
                    onTypeNumber = { clicked += "type" },
                    onRelationship = { clicked += it.value },
                ),
            )
        }
        assertShown(TestTags.INVITE, TestTags.INVITE_SEND)
        assertTaps(
            TestTags.INVITE_PICK_CONTACT to "pick",
            TestTags.INVITE_TYPE_NUMBER to "type",
            TestTags.INVITE_RELATIONSHIP + "spouse" to "spouse",
            TestTags.BACK to "back",
        )
    }

    @Test
    fun `invite, ready to send`() {
        content {
            InviteScreen(
                InviteUiState(contact = Invitee("Sibel", "+905329876543"), relationship = EdgeType.SPOUSE),
                InviteCallbacks(onPickContact = { clicked += "change" }, onSend = { clicked += "send" }),
            )
        }
        assertTaps(TestTags.INVITE_PICK_CONTACT to "change", TestTags.INVITE_SEND to "send")
    }

    @Test
    fun `invite, typing a number`() {
        content { InviteScreen(InviteUiState(typing = true), InviteCallbacks()) }
        assertShown(TestTags.INVITE_NAME, TestTags.INVITE_NUMBER)
    }

    @Test
    fun `home with a pending invite`() {
        val invite = PendingInviteUi("i1", "abc", "Sibel", EdgeType.SPOUSE, DeadlineText.Today("14:32"))
        content {
            HomeScreen(
                state = HomeUiState(myName = "Eren", pendingInvite = invite),
                onBell = {},
                onProfile = {},
                onInvite = {},
                onComposeTextChange = {},
                onComposeSend = {},
                onVoiceStart = {},
                onVoiceEnd = {},
                pendingInvite =
                    PendingInviteCallbacks(onReshare = { clicked += "reshare" }, onRevoke = { clicked += "revoke" }),
            )
        }
        assertShown(TestTags.HOME_PENDING_INVITE)
        assertTaps(TestTags.HOME_INVITE_RESHARE to "reshare", TestTags.HOME_INVITE_REVOKE to "revoke")
    }

    @Test
    fun `consent after sign-in`() {
        content {
            ConsentScreen(
                ConsentUiState("Sibel", EdgeType.SPOUSE, EdgeType.SPOUSE, "23:00", "07:00"),
                ConsentCallbacks(
                    onAllow = { clicked += "allow" },
                    onLater = { clicked += "later" },
                    onChangeRelationship = { clicked += "change" },
                ),
            )
        }
        assertShown(TestTags.CONSENT)
        assertTaps(TestTags.CONSENT_CHANGE to "change", TestTags.CONSENT_ALLOW to "allow", TestTags.CONSENT_LATER to "later")
    }

    @Test
    fun `invite detail from the bell`() {
        content {
            ConsentScreen(
                ConsentUiState("Sibel", EdgeType.SPOUSE, EdgeType.SPOUSE, "23:00", "07:00", fromBell = true),
                ConsentCallbacks(onDecline = { clicked += "decline" }, onBack = { clicked += "back" }),
            )
        }
        assertTaps(TestTags.CONSENT_DECLINE to "decline", TestTags.BACK to "back")
    }

    @Test
    fun matched() {
        content { MatchedScreen("Sibel", "Eren", onStart = { clicked += "start" }) }
        assertShown(TestTags.MATCHED)
        assertTaps(TestTags.MATCHED_START to "start")
    }

    @Test
    fun `bell with an invite`() {
        content {
            InboxScreen(
                InboxUiState(listOf(InboxInviteUi("i1", "Sibel", Ago.Hours(2)))),
                onBack = { clicked += "back" },
                onOpenInvite = { clicked += "open" },
            )
        }
        assertShown(TestTags.INBOX)
        assertTaps(TestTags.INBOX_OPEN_INVITE to "open", TestTags.BACK to "back")
    }

    @Test
    fun `setup, try first`() {
        content { SetupDemoScreen(SetupUiState.Intro(), onTry = { clicked += "try" }, onSkipDemo = { clicked += "skip" }) }
        assertShown(TestTags.SETUP)
        assertTaps(TestTags.SETUP_TRY to "try", TestTags.SETUP_SKIP_DEMO to "skip")
    }

    @Test
    fun `setup, demo poke`() {
        content { PokeDemoScreen(settingsLeft = 2, onContinue = { clicked += "continue" }) }
        assertShown(TestTags.SETUP_DEMO)
        assertTaps(TestTags.SETUP_DEMO_OK to "continue")
    }

    @Test
    fun `setup, steps`() {
        content {
            PermissionsScreen(
                SetupUiState.Steps(
                    settingsCount = 2,
                    notificationsGranted = true,
                    steps =
                        listOf(
                            PermissionStepUi(SetupStep.NOTIFICATIONS, granted = true, skipped = false),
                            PermissionStepUi(SetupStep.EXACT_ALARM, granted = false, skipped = false),
                            PermissionStepUi(SetupStep.FULL_SCREEN, granted = false, skipped = false),
                        ),
                    active = SetupStep.EXACT_ALARM,
                ),
                onOpenActive = { clicked += "open" },
                onSkipActive = { clicked += "skip" },
            )
        }
        assertShown(TestTags.SETUP_STEPS)
        assertTaps(TestTags.SETUP_OPEN_ACTIVE to "open", TestTags.SETUP_SKIP_STEP to "skip")
    }

    private fun homeWith(compose: ComposeState) {
        content {
            HomeScreen(
                state = HomeUiState(myName = "Sibel", compose = compose),
                onBell = { clicked += "bell" },
                onProfile = { clicked += "profile" },
                onInvite = { clicked += "invite" },
                onComposeTextChange = {},
                onComposeSend = { clicked += "send" },
                onVoiceStart = {},
                onVoiceEnd = {},
            )
        }
    }
}
